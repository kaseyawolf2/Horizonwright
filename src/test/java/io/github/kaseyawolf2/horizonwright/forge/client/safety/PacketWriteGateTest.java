package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathContext;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyInterlock;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationAttempt;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationPermit;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveCandidate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchStatus;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryStack;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationStatus;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RespawnObservation;

public class PacketWriteGateTest {

    @Test
    public void respawnAuthorizationPersistsBeforeExactlyOnePacketWrite() {
        ConnectionSafetyEventStampSource stamps = new ConnectionSafetyEventStampSource(3L);
        DeathSafetyController controller = controller(3L);
        long deathEpoch = latch(controller, stamps);
        List<String> order = new ArrayList<>();
        RespawnPacketWriteGate gate = new RespawnPacketWriteGate(controller, stamps, processor(order));

        assertTrue(gate.tryWrite(deathEpoch, 2L, () -> order.add("respawn-write")));
        assertFalse(gate.tryWrite(deathEpoch, 3L, () -> order.add("duplicate-write")));

        assertEquals(java.util.Arrays.asList("persist", "respawn-write"), order);
    }

    @Test
    public void exactGraveAuthorizationPersistsBeforeOneWriteAndRejectsReplay() {
        ConnectionSafetyEventStampSource stamps = new ConnectionSafetyEventStampSource(5L);
        DeathSafetyController controller = controller(5L);
        InventoryManifest preDeath = new InventoryManifest(
            36,
            Collections.singletonList(new InventoryStack("minecraft:diamond|0|none", 3, 64)));
        long deathEpoch = latch(controller, stamps, preDeath);
        InventoryManifest empty = InventoryManifest.empty(36);
        controller.onRespawnObservation(
            stamps.next(2L),
            deathEpoch,
            new RespawnObservation(
                "new-player",
                20.0D,
                false,
                true,
                true,
                new DimensionBlockPosition(0, 0, 64, 0),
                empty));
        controller.onRecoveryNavigation(
            stamps.next(3L),
            deathEpoch,
            new RecoveryNavigationObservation(
                RecoveryNavigationStatus.ARRIVED,
                new DimensionBlockPosition(0, 10, 64, 10),
                empty,
                false,
                false));
        GraveCandidate grave = new GraveCandidate(
            new GraveIdentity("grave-tile", new DimensionBlockPosition(0, 11, 64, 10)),
            "old-player",
            new InventoryManifest(
                27,
                Collections.singletonList(new InventoryStack("minecraft:diamond|0|none", 3, 64))));
        controller.onGraveSearch(
            stamps.next(4L),
            deathEpoch,
            new GraveSearchObservation(GraveSearchStatus.COMPLETE, Collections.singletonList(grave), empty, true));
        GraveActivationPermit permit = controller.snapshot()
            .getGraveActivationPermit()
            .orElseThrow(() -> new AssertionError("missing permit"));
        GraveActivationAttempt attempt = new GraveActivationAttempt(
            permit.getPermitId(),
            permit.getDeathEpoch(),
            permit.getGraveIdentity(),
            true,
            true);
        List<String> order = new ArrayList<>();
        GraveActivationPacketWriteGate gate = new GraveActivationPacketWriteGate(
            controller,
            stamps,
            processor(order),
            new GraveActivationReplayBlock(false));

        assertTrue(gate.tryWrite(attempt, 5L, () -> order.add("grave-write")));
        assertFalse(gate.tryWrite(attempt, 6L, () -> order.add("duplicate-write")));

        assertEquals(java.util.Arrays.asList("persist", "grave-write"), order);
    }

    @Test
    public void persistenceFailurePreventsRespawnPacketWrite() {
        ConnectionSafetyEventStampSource stamps = new ConnectionSafetyEventStampSource(9L);
        DeathSafetyController controller = controller(9L);
        long deathEpoch = latch(controller, stamps);
        DeathSafetyDirectiveProcessor processor = new DeathSafetyDirectiveProcessor(new DeathSafetyDurableState() {

            @Override
            public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
                throw new IllegalStateException("disk unavailable");
            }

            @Override
            public void clearResolvedDeath() {}
        });
        final boolean[] written = { false };
        try {
            new RespawnPacketWriteGate(controller, stamps, processor).tryWrite(deathEpoch, 2L, () -> written[0] = true);
        } catch (IllegalStateException expected) {
            assertEquals("disk unavailable", expected.getMessage());
        }
        assertFalse(written[0]);
    }

    @Test
    public void connectionRetiredDuringPersistenceCannotReachFinalPacketWrite() {
        final ConnectionSafetyEventStampSource stamps = new ConnectionSafetyEventStampSource(10L);
        DeathSafetyController controller = controller(10L);
        long deathEpoch = latch(controller, stamps);
        DeathSafetyDirectiveProcessor processor = new DeathSafetyDirectiveProcessor(new DeathSafetyDurableState() {

            @Override
            public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
                stamps.retire();
            }

            @Override
            public void clearResolvedDeath() {}
        });
        final boolean[] written = { false };

        assertFalse(
            new RespawnPacketWriteGate(controller, stamps, processor)
                .tryWrite(deathEpoch, 2L, () -> written[0] = true));
        assertFalse(written[0]);
    }

    private static DeathSafetyDirectiveProcessor processor(final List<String> order) {
        return new DeathSafetyDirectiveProcessor(new DeathSafetyDurableState() {

            @Override
            public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
                order.add("persist");
            }

            @Override
            public void clearResolvedDeath() {
                order.add("clear");
            }
        });
    }

    private static DeathSafetyController controller(long connectionEpoch) {
        return new DeathSafetyController(
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            new NoOpInterlock(),
            BrokerBackedDeathSafetyInterlockTest.connection(connectionEpoch, "old-player"));
    }

    private static long latch(DeathSafetyController controller, ConnectionSafetyEventStampSource stamps) {
        return latch(controller, stamps, InventoryManifest.empty(36));
    }

    private static long latch(DeathSafetyController controller, ConnectionSafetyEventStampSource stamps,
        InventoryManifest inventory) {
        return controller
            .onDeathSignal(
                stamps.next(1L),
                DeathSignal.LOCAL_DEATH_CALLBACK,
                new DeathContext(new DimensionBlockPosition(0, 10, 64, 10), "old-player", "task", inventory))
            .getSnapshot()
            .getDeathEpoch();
    }

    static final class NoOpInterlock implements DeathSafetyInterlock {

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
