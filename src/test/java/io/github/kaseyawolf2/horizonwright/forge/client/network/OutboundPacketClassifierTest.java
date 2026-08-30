package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.EnumSet;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C11PacketEnchantItem;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionAuthorizationDecision;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;

public class OutboundPacketClassifierTest {

    @Test
    public void movementOnlyLeaseCannotDigPlaceUseOrChangeSlots() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker
            .tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);

        assertBlocked(guard, new C07PacketPlayerDigging(0, 1, 64, 1, 1));
        assertBlocked(
            guard,
            new C08PacketPlayerBlockPlacement(1, 64, 1, 1, new ItemStack(Blocks.stone), 0.5F, 0.5F, 0.5F));
        assertBlocked(
            guard,
            new C08PacketPlayerBlockPlacement(-1, -1, -1, 255, new ItemStack(Items.apple), 0.0F, 0.0F, 0.0F));
        assertBlocked(guard, new C09PacketHeldItemChange(2));
    }

    @Test
    public void ordinaryProtocolPacketsRemainUnrestricted() {
        ActionSessionGuard guard = new ActionSessionGuard();
        PacketActionRequirement requirement = OutboundPacketClassifier.classify(new C03PacketPlayer(true));

        assertEquals(ActionAuthorizationDecision.PLAYER_PASSTHROUGH, requirement.evaluate(guard));
        assertEquals(
            ActionAuthorizationDecision.PLAYER_PASSTHROUGH,
            OutboundPacketClassifier.classify(new C07PacketPlayerDigging(1, 1, 64, 1, 1))
                .evaluate(guard));
        assertEquals(
            ActionAuthorizationDecision.PLAYER_PASSTHROUGH,
            OutboundPacketClassifier.classify(new C07PacketPlayerDigging(5, 0, 0, 0, 255))
                .evaluate(guard));
    }

    @Test
    public void basePlayerHeartbeatRemainsAvailableWithoutMovementAuthority() {
        ActionSessionGuard containerSession = activeGuard(EnumSet.of(ActionCapability.CONTAINER));

        assertEquals(
            ActionAuthorizationDecision.PLAYER_PASSTHROUGH,
            OutboundPacketClassifier.classify(new C03PacketPlayer(true))
                .evaluate(containerSession));
    }

    @Test
    public void positionAndLookSubtypesRequireTheirExactCapabilities() {
        ActionSessionGuard movement = activeGuard(EnumSet.of(ActionCapability.MOVEMENT));
        ActionSessionGuard look = activeGuard(EnumSet.of(ActionCapability.LOOK));

        assertEquals(
            ActionAuthorizationDecision.AUTHORIZED,
            OutboundPacketClassifier
                .classify(new C03PacketPlayer.C04PacketPlayerPosition(1.0D, 64.0D, 65.62D, 1.0D, true))
                .evaluate(movement));
        assertEquals(
            ActionAuthorizationDecision.AUTHORIZED,
            OutboundPacketClassifier.classify(new C03PacketPlayer.C05PacketPlayerLook(20.0F, 10.0F, true))
                .evaluate(look));
        assertEquals(
            ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY,
            OutboundPacketClassifier
                .classify(new C03PacketPlayer.C06PacketPlayerPosLook(1.0D, 64.0D, 65.62D, 1.0D, 20.0F, 10.0F, true))
                .evaluate(movement));
    }

    @Test
    public void quarantineAllowsOnlyAuditedReleaseAndMaintenancePackets() throws Exception {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        guard.quarantine(lease);
        guard.end(lease);

        assertAllowed(guard, new C00PacketKeepAlive(1));
        assertAllowed(guard, new C03PacketPlayer(true));
        assertAllowed(guard, new C07PacketPlayerDigging(1, 1, 64, 1, 1));
        assertAllowed(guard, new C07PacketPlayerDigging(5, 0, 0, 0, 255));
        assertAllowed(guard, entityAction(2));
        assertAllowed(guard, entityAction(5));
        assertAllowed(guard, new C0CPacketInput(0.0F, 0.0F, false, false));
        C13PacketPlayerAbilities stopFlying = new C13PacketPlayerAbilities();
        stopFlying.func_149483_b(false);
        assertAllowed(guard, stopFlying);

        assertRevoked(guard, new C0CPacketInput(0.0F, 1.0F, false, false));
        assertRevoked(guard, new C17PacketCustomPayload("HW|MUTATE", new byte[] { 1 }));
        C13PacketPlayerAbilities startFlying = new C13PacketPlayerAbilities();
        startFlying.func_149483_b(true);
        assertRevoked(guard, startFlying);
    }

    @Test
    public void unknownAndPreviouslyMissingMutationPacketsFailClosedDuringASession() {
        ActionSessionGuard guard = activeGuard(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK));

        assertBlocked(guard, new C11PacketEnchantItem(1, 2));
        assertBlocked(guard, new C17PacketCustomPayload("MC|ItemName", new byte[] { 1 }));
    }

    private static void assertBlocked(ActionSessionGuard guard, Object packet) {
        assertEquals(
            ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY,
            OutboundPacketClassifier.classify(packet)
                .evaluate(guard));
    }

    private static void assertRevoked(ActionSessionGuard guard, Object packet) {
        assertEquals(
            ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH,
            OutboundPacketClassifier.classify(packet)
                .evaluate(guard));
    }

    private static void assertAllowed(ActionSessionGuard guard, Object packet) {
        assertEquals(
            ActionAuthorizationDecision.PLAYER_PASSTHROUGH,
            OutboundPacketClassifier.classify(packet)
                .evaluate(guard));
    }

    private static ActionSessionGuard activeGuard(EnumSet<ActionCapability> capabilities) {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("test", capabilities)
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        return guard;
    }

    private static C0BPacketEntityAction entityAction(int action) throws Exception {
        C0BPacketEntityAction packet = new C0BPacketEntityAction();
        Field actionField = C0BPacketEntityAction.class.getDeclaredField("field_149515_b");
        actionField.setAccessible(true);
        actionField.setInt(packet, action);
        return packet;
    }
}
