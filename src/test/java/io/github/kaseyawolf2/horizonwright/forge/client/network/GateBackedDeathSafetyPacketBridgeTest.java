package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Optional;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S06PacketUpdateHealth;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ConnectionIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyInterlock;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyPolicy;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationAttempt;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.ClientDeathContextPublisher;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.ClientDeathContextSource;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.ClientInventorySnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.ConnectionSafetyEventStampSource;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.DeathSafetyDirectiveProcessor;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.DeathSafetyDurableState;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.GraveActivationPacketWriteGate;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.GraveActivationReplayBlock;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.InboundLethalHealthHook;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.RespawnPacketWriteGate;
import io.netty.channel.embedded.EmbeddedChannel;

public class GateBackedDeathSafetyPacketBridgeTest {

    @Test
    public void realInboundAndRespawnGatesMeetAtTheNettyBoundary() {
        final long connectionEpoch = 7L;
        final long[] clientTick = { 1L };
        DeathSafetyController controller = new DeathSafetyController(
            DeathSafetyPolicy.planDefaults(8),
            new NoOpInterlock(),
            new ConnectionIdentity(connectionEpoch, "server", "world", "old-player"));
        ConnectionSafetyEventStampSource stamps = new ConnectionSafetyEventStampSource(connectionEpoch);
        ClientDeathContextPublisher publisher = new ClientDeathContextPublisher(() -> true);
        publisher.captureAndPublish(connectionEpoch, clientTick[0], deathContext());
        DeathSafetyDirectiveProcessor directives = new DeathSafetyDirectiveProcessor(new NoOpDurableState());
        InboundLethalHealthHook lethalHook = new InboundLethalHealthHook(
            controller,
            stamps,
            publisher,
            directives,
            (directive, snapshot) -> {});
        GateBackedDeathSafetyPacketBridge bridge = new GateBackedDeathSafetyPacketBridge(
            lethalHook,
            new RespawnPacketWriteGate(controller, stamps, directives),
            new GraveActivationPacketWriteGate(controller, stamps, directives, new GraveActivationReplayBlock(false)),
            new DeathSafetyPacketContext() {

                @Override
                public long getClientTick() {
                    return clientTick[0];
                }

                @Override
                public double getMaximumHealth() {
                    return 20.0D;
                }

                @Override
                public long getActiveDeathEpoch() {
                    return controller.snapshot()
                        .getDeathEpoch();
                }

                @Override
                public Optional<GraveActivationAttempt> matchGraveActivation(C08PacketPlayerBlockPlacement packet) {
                    return Optional.empty();
                }

                @Override
                public void onBoundaryUnavailable(boolean transportClosed) {}
            });
        EmbeddedChannel channel = new EmbeddedChannel(
            new OutboundPacketFirewall(new ActionSessionGuard(), null, bridge));
        S06PacketUpdateHealth lethal = new S06PacketUpdateHealth(0.0F, 20, 5.0F);

        assertTrue(channel.writeInbound(lethal));
        assertSame(lethal, channel.readInbound());
        assertSame(
            DeathSafetyState.DEATH_LATCHED,
            controller.snapshot()
                .getState());

        clientTick[0]++;
        C16PacketClientStatus respawn = new C16PacketClientStatus(C16PacketClientStatus.EnumState.PERFORM_RESPAWN);
        assertTrue(channel.writeOutbound(respawn));
        assertSame(respawn, channel.readOutbound());
        assertTrue(
            controller.snapshot()
                .isRespawnRequestConsumed());
        channel.finish();
    }

    private static ClientDeathContextSource deathContext() {
        return new ClientDeathContextSource() {

            @Override
            public DimensionBlockPosition getPlayerPosition() {
                return new DimensionBlockPosition(0, 10, 64, 10);
            }

            @Override
            public String getPlayerIdentity() {
                return "old-player";
            }

            @Override
            public String getActiveTaskId() {
                return "task";
            }

            @Override
            public ClientInventorySnapshot getInventorySnapshot() {
                return new ClientInventorySnapshot(36, Collections.emptyList());
            }
        };
    }

    private static final class NoOpDurableState implements DeathSafetyDurableState {

        @Override
        public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {}

        @Override
        public void clearResolvedDeath() {}
    }

    private static final class NoOpInterlock implements DeathSafetyInterlock {

        @Override
        public void enterCriticalRestrictions() {}

        @Override
        public void releaseCriticalRestrictions() {}

        @Override
        public void latchDeath(DeathLatchRecord record) {}

        @Override
        public void reaffirmDeathLockdown(long deathEpoch) {}

        @Override
        public void releaseDeathLockdown(long deathEpoch) {}
    }
}
