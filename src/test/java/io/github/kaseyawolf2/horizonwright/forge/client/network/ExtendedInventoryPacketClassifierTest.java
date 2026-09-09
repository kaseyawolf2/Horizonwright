package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertEquals;

import java.util.EnumSet;

import net.minecraft.network.play.client.C17PacketCustomPayload;

import org.junit.Test;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.github.kaseyawolf2.horizonwright.core.action.ActionAuthorizationDecision;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ExtendedInventoryPacketClassifierTest {

    @Test
    public void adventureOpenNeedsAllCapabilitiesAndPreservesPayload() {
        ByteBuf payload = Unpooled.buffer()
            .writeByte(99)
            .writeByte(5)
            .writeByte(1)
            .writeByte(0);
        payload.readByte();
        FMLProxyPacket packet = new FMLProxyPacket(payload, "advBackpackChan");
        try {
            int reader = payload.readerIndex();
            int writer = payload.writerIndex();
            int references = payload.refCnt();
            PacketActionRequirement requirement = OutboundPacketClassifier.classify(packet);
            assertEquals(
                EnumSet.of(ActionCapability.USE, ActionCapability.HELD_USE, ActionCapability.CONTAINER),
                requirement.getCapabilities());
            assertEquals(
                ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY,
                requirement.evaluate(active(EnumSet.of(ActionCapability.CONTAINER))));
            assertEquals(
                ActionAuthorizationDecision.AUTHORIZED,
                requirement.evaluate(
                    active(EnumSet.of(ActionCapability.USE, ActionCapability.HELD_USE, ActionCapability.CONTAINER))));
            assertEquals(reader, payload.readerIndex());
            assertEquals(writer, payload.writerIndex());
            assertEquals(references, payload.refCnt());
            assertEquals(5, payload.getUnsignedByte(reader));
        } finally {
            payload.release();
        }
    }

    @Test
    public void ae2TargetAndShiftClickAreBlockedAfterLeaseRevocation() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("inventory", EnumSet.of(ActionCapability.CONTAINER))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        FMLProxyPacket target = new FMLProxyPacket(
            Unpooled.buffer()
                .writeInt(21)
                .writeShort(0x0100)
                .writeByte(0x1f)
                .writeByte(0x8b),
            "AE2");
        FMLProxyPacket shiftClick = new FMLProxyPacket(
            Unpooled.buffer()
                .writeInt(42)
                .writeInt(2)
                .writeInt(-1),
            "AE2");
        try {
            assertEquals(
                ActionAuthorizationDecision.AUTHORIZED,
                OutboundPacketClassifier.classify(target)
                    .evaluate(guard));
            assertEquals(
                ActionAuthorizationDecision.AUTHORIZED,
                OutboundPacketClassifier.classify(shiftClick)
                    .evaluate(guard));
            guard.quarantine(lease);
            guard.end(lease);
            assertEquals(
                ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH,
                OutboundPacketClassifier.classify(target)
                    .evaluate(guard));
            assertEquals(
                ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH,
                OutboundPacketClassifier.classify(shiftClick)
                    .evaluate(guard));
        } finally {
            target.payload()
                .release();
            shiftClick.payload()
                .release();
        }
    }

    @Test
    public void vanillaCustomPayloadEnvelopeHasTheSameGuards() {
        ActionSessionGuard movement = active(EnumSet.of(ActionCapability.MOVEMENT));
        assertEquals(
            ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY,
            OutboundPacketClassifier.classify(new C17PacketCustomPayload("advBackpackChan", new byte[] { 5, 1, 1 }))
                .evaluate(movement));
        assertEquals(
            ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY,
            OutboundPacketClassifier.classify(new C17PacketCustomPayload("AE2", new byte[] { 0, 0, 0, 42, 0, 0, 0, 2 }))
                .evaluate(movement));
    }

    @Test
    public void truncatedKnownHeaderStaysGatedAndUnrelatedPacketsPassThrough() {
        ActionSessionGuard movement = active(EnumSet.of(ActionCapability.MOVEMENT));
        assertEquals(
            ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY,
            OutboundPacketClassifier.classify(new C17PacketCustomPayload("AE2", new byte[] { 0, 0, 0, 42 }))
                .evaluate(movement));
        for (C17PacketCustomPayload packet : new C17PacketCustomPayload[] {
            new C17PacketCustomPayload("AE2", new byte[] { 0, 0, 0 }),
            new C17PacketCustomPayload("AE2", new byte[] { 0, 0, 0, 43 }),
            new C17PacketCustomPayload("advBackpackChan", new byte[] { 4, 1, 1 }),
            new C17PacketCustomPayload("other", new byte[] { 5, 1, 1 }) }) {
            assertEquals(
                PacketActionRequirement.Kind.OBSERVE_ONLY,
                OutboundPacketClassifier.classify(packet)
                    .getKind());
            assertEquals(
                ActionAuthorizationDecision.PLAYER_PASSTHROUGH,
                OutboundPacketClassifier.classify(packet)
                    .evaluate(movement));
        }
    }

    private static ActionSessionGuard active(EnumSet<ActionCapability> capabilities) {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("test", capabilities)
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        return guard;
    }
}
