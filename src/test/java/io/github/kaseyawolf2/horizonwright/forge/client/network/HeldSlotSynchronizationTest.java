package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.block.BlockLog;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.netty.channel.embedded.EmbeddedChannel;

public class HeldSlotSynchronizationTest {

    private final InMemoryActionBroker broker = new InMemoryActionBroker();
    private final ActionLease lease = broker
        .tryAcquire("pillar", EnumSet.of(ActionCapability.DIG, ActionCapability.HELD_USE, ActionCapability.PLACE))
        .get();
    private final ActionSessionGuard guard = new ActionSessionGuard();

    @Test
    public void queuedRestorePassesFirewallBeforeSessionIsQuarantined() {
        guard.markFirewallInstalled();
        guard.begin(lease);
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));
        try {
            List<Runnable> eventLoop = new ArrayList<>();
            C09PacketHeldItemChange restore = new C09PacketHeldItemChange(4);
            eventLoop.add(() -> channel.writeOutbound(restore));
            ActionPacketDispatch.endAfterPendingWrites(guard, lease, eventLoop::add);
            assertTrue(guard.isActiveLease(lease));
            eventLoop.get(0)
                .run();
            assertSame(restore, channel.readOutbound());
            assertTrue(guard.isActiveLease(lease));
            eventLoop.get(1)
                .run();
            assertFalse(guard.isActiveLease(lease));
            assertEquals(0, guard.getBlockedActionCount());
            assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
        } finally {
            channel.finish();
        }
    }

    @Test
    public void explicitSlotResyncPrecedesPlacementAfterPreviouslyDroppedRestore() {
        guard.markFirewallInstalled();
        guard.begin(lease);
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));
        try {
            // Reproduce the old race: vanilla cached this slot, but the firewall rejected its packet.
            guard.quarantine(lease);
            guard.end(lease);
            channel.writeOutbound(new C09PacketHeldItemChange(4));
            assertNull(channel.readOutbound());
            assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
            guard.begin(lease);
            C08PacketPlayerBlockPlacement placement = new C08PacketPlayerBlockPlacement(
                201,
                68,
                419,
                1,
                new ItemStack(new ItemBlock(new BlockLog() {})),
                0.5F,
                1F,
                0.5F);
            HeldSlotSynchronization.sendSelectedSlot(4, packet -> channel.writeOutbound(packet));
            channel.writeOutbound(placement);
            C09PacketHeldItemChange selected = (C09PacketHeldItemChange) channel.readOutbound();
            assertEquals(4, selected.func_149614_c());
            assertSame(placement, channel.readOutbound());
            // Reusing the same client slot still transmits it; no optimistic cache suppresses the repair.
            HeldSlotSynchronization.sendSelectedSlot(4, packet -> channel.writeOutbound(packet));
            C09PacketHeldItemChange repeated = (C09PacketHeldItemChange) channel.readOutbound();
            assertEquals(4, repeated.func_149614_c());
        } finally {
            channel.finish();
        }
    }

    @Test
    public void revokedEpochStillRejectsQueuedSlotWrites() {
        guard.markFirewallInstalled();
        guard.begin(lease);
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));
        try {
            List<Runnable> eventLoop = new ArrayList<>();
            eventLoop.add(() -> HeldSlotSynchronization.sendSelectedSlot(4, packet -> channel.writeOutbound(packet)));
            ActionPacketDispatch.endAfterPendingWrites(guard, lease, eventLoop::add);
            broker.revokeAll();
            eventLoop.forEach(Runnable::run);
            assertNull(channel.readOutbound());
            assertFalse(guard.isActiveLease(lease));
        } finally {
            channel.finish();
        }
    }

    @Test
    public void dispatchFailureReleasesSessionInsteadOfLeavingAuthorityActive() {
        guard.markFirewallInstalled();
        guard.begin(lease);
        try {
            ActionPacketDispatch.endAfterPendingWrites(
                guard,
                lease,
                completion -> { throw new IllegalStateException("closed channel"); });
            fail("Expected dispatcher failure");
        } catch (IllegalStateException expected) {
            assertEquals("closed channel", expected.getMessage());
        }
        assertFalse(guard.isActiveLease(lease));
        assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
    }
}
