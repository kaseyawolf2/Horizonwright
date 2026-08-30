package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
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

    private static C03PacketPlayer.C04PacketPlayerPosition position(double x) {
        return new C03PacketPlayer.C04PacketPlayerPosition(x, 64.0D, 65.62D, 1.0D, true);
    }
}
