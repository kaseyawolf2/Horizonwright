package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import org.junit.Test;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

public class OutboundPacketFirewallTest {

    @Test
    public void staleActionPacketsStayBlockedUntilTheDrainBarrierRuns() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));

        C03PacketPlayer.C04PacketPlayerPosition activeMovement = position(1.0D);
        assertTrue(channel.writeOutbound(activeMovement));
        assertSame(activeMovement, channel.readOutbound());

        guard.quarantine(lease);
        guard.end(lease);
        channel.writeOutbound(position(2.0D));
        assertNull(channel.readOutbound());

        C07PacketPlayerDigging abortDig = new C07PacketPlayerDigging(1, 1, 64, 1, 1);
        assertTrue(channel.writeOutbound(abortDig));
        assertSame(abortDig, channel.readOutbound());

        long barrier = guard.drainGenerationOrZero();
        assertTrue(barrier > 0L);
        assertTrue(guard.completeDrain(barrier));
        C03PacketPlayer.C04PacketPlayerPosition playerMovement = position(3.0D);
        assertTrue(channel.writeOutbound(playerMovement));
        assertSame(playerMovement, channel.readOutbound());
        channel.finish();
    }

    @Test
    public void unknownTrafficPassesByIdentityWithoutPoisoningAnActiveSession() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));
        Object opaqueNonPacket = new Object();
        FMLProxyPacket arbitraryProxy = new FMLProxyPacket(Unpooled.wrappedBuffer(new byte[] { 7 }), "MutatingMod");
        C17PacketCustomPayload customPayload = new C17PacketCustomPayload("HW|UNKNOWN", new byte[] { 1 });

        assertTrue(channel.writeOutbound(opaqueNonPacket));
        assertSame(opaqueNonPacket, channel.readOutbound());
        assertTrue(channel.writeOutbound(arbitraryProxy));
        assertSame(arbitraryProxy, channel.readOutbound());
        assertTrue(channel.writeOutbound(customPayload));
        assertSame(customPayload, channel.readOutbound());
        assertEquals(0L, guard.getBlockedActionCount());

        arbitraryProxy.payload()
            .release();
        channel.finish();
    }

    @Test
    public void manualAutomationStopDoesNotBlockDirectPlayerMining() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        broker.addRevocationListener(guard);
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));
        broker.enterAutomationLockdown();
        C07PacketPlayerDigging playerDig = new C07PacketPlayerDigging(0, 1, 64, 1, 1);

        assertTrue(channel.writeOutbound(playerDig));
        assertSame(playerDig, channel.readOutbound());
        assertEquals(0L, guard.getBlockedActionCount());
        channel.finish();
    }

    @Test
    public void deathRecoveryLeasePassesMovementButBlocksIntegratedInteractionsAndPreservesUnknownTraffic() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        broker.addRevocationListener(guard);
        broker.enterSafetyLockdown();
        ActionLease recovery = broker.tryAcquireSafetyRecovery("death-recovery")
            .get();
        guard.begin(recovery);
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));

        C03PacketPlayer.C04PacketPlayerPosition movement = position(4.0D);
        assertTrue(channel.writeOutbound(movement));
        assertSame(movement, channel.readOutbound());

        channel.writeOutbound(new C07PacketPlayerDigging(0, 1, 64, 1, 1));
        assertNull(channel.readOutbound());
        assertEquals(1L, guard.getBlockedActionCount());

        FMLProxyPacket unknown = new FMLProxyPacket(Unpooled.wrappedBuffer(new byte[] { 9 }), "UnintegratedMod");
        assertTrue(channel.writeOutbound(unknown));
        assertSame(unknown, channel.readOutbound());
        unknown.payload()
            .release();
        channel.finish();
    }

    @Test
    public void directHandlerRemovalDisablesAutomationButLeavesTheChannelAlone() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        OutboundPacketFirewall firewall = new OutboundPacketFirewall(guard);
        EmbeddedChannel channel = new EmbeddedChannel(firewall);

        channel.pipeline()
            .remove(firewall);

        assertTrue(channel.isOpen());
        assertFalse(guard.isReadyForSession());
        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());
        Object unknown = new Object();
        assertTrue(channel.writeOutbound(unknown));
        assertSame(unknown, channel.readOutbound());
        assertEquals(0L, guard.getBlockedActionCount());
        channel.finish();
    }

    @Test
    public void inactiveContainerObserverLeavesManualClicksAndConfirmationsUntouched() {
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        final boolean[] synchronizationObserved = { false, false };
        ContainerTransactionPacketBridge observer = new ContainerTransactionPacketBridge() {

            @Override
            public ClickWriteDecision beforeClickWrite(C0EPacketClickWindow packet) {
                return ClickWriteDecision.NOT_APPLICABLE;
            }

            @Override
            public void beforeConfirmationRead(S32PacketConfirmTransaction packet) {}

            @Override
            public void beforeWindowItemsRead(S30PacketWindowItems packet) {
                synchronizationObserved[0] = true;
            }

            @Override
            public void beforeSetSlotRead(S2FPacketSetSlot packet) {
                synchronizationObserved[1] = true;
            }

            @Override
            public void onBoundaryUnavailable(boolean transportClosed) {}
        };
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard, null, null, observer));
        C0EPacketClickWindow click = new C0EPacketClickWindow(7, 0, 0, 1, null, (short) 3);
        S32PacketConfirmTransaction confirmation = new S32PacketConfirmTransaction(7, (short) 3, true);
        S30PacketWindowItems windowItems = new S30PacketWindowItems(
            7,
            java.util.Collections.<net.minecraft.item.ItemStack>emptyList());
        S2FPacketSetSlot cursor = new S2FPacketSetSlot(-1, -1, null);

        assertTrue(channel.writeOutbound(click));
        assertSame(click, channel.readOutbound());
        assertTrue(channel.writeInbound(confirmation));
        assertSame(confirmation, channel.readInbound());
        assertTrue(channel.writeInbound(windowItems));
        assertSame(windowItems, channel.readInbound());
        assertTrue(synchronizationObserved[0]);
        assertTrue(channel.writeInbound(cursor));
        assertSame(cursor, channel.readInbound());
        assertTrue(synchronizationObserved[1]);
        channel.finish();
    }

    @Test
    public void inboundObserverFailureStillForwardsVanillaConfirmation() {
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        ContainerTransactionPacketBridge observer = new ContainerTransactionPacketBridge() {

            @Override
            public ClickWriteDecision beforeClickWrite(C0EPacketClickWindow packet) {
                return ClickWriteDecision.NOT_APPLICABLE;
            }

            @Override
            public void beforeConfirmationRead(S32PacketConfirmTransaction packet) {
                throw new IllegalStateException("observer failed");
            }

            @Override
            public void onBoundaryUnavailable(boolean transportClosed) {}
        };
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard, null, null, observer));
        S32PacketConfirmTransaction confirmation = new S32PacketConfirmTransaction(7, (short) 3, false);

        assertTrue(channel.writeInbound(confirmation));
        assertSame(confirmation, channel.readInbound());
        assertFalse(guard.isReadyForSession());
        assertTrue(channel.isOpen());
        channel.finish();
    }

    private static C03PacketPlayer.C04PacketPlayerPosition position(double x) {
        return new C03PacketPlayer.C04PacketPlayerPosition(x, 64.0D, 65.62D, 1.0D, true);
    }
}
