package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.death.UnresolvedDeathPersistenceAdapter;
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
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RespawnObservation;

public class DeathSafetyConnectionCoordinatorTest {

    @Test
    public void disconnectFailureStillRetiresTheOldSessionAndAllowsTheNextConnection() {
        DeathSafetyInterlock interlock = new DeathSafetyInterlock() {

            @Override
            public void enterCriticalRestrictions() {}

            @Override
            public void releaseCriticalRestrictions() {
                throw new IllegalStateException("release failed");
            }

            @Override
            public void latchDeath(DeathLatchRecord record) {}

            @Override
            public void reaffirmDeathLockdown(long deathEpoch) {}

            @Override
            public void releaseDeathLockdown(long deathEpoch) {}
        };
        DeathSafetyConnectionCoordinator coordinator = new DeathSafetyConnectionCoordinator(
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            interlock);
        DeathSafetyConnectionCoordinator.Session first = coordinator
            .openFresh(BrokerBackedDeathSafetyInterlockTest.connection(1L, "first-player"));
        first.getController()
            .onHealthObservation(
                first.getStamps()
                    .next(1L),
                1.0D,
                20.0D,
                null);

        try {
            coordinator.disconnect(first, 2L);
            fail("expected disconnect cleanup failure");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("release failed"));
        }

        assertFalse(
            first.getStamps()
                .isOpen());
        assertFalse(coordinator.isActive(first));
        DeathSafetyConnectionCoordinator.Session second = coordinator
            .openFresh(BrokerBackedDeathSafetyInterlockTest.connection(2L, "second-player"));
        assertTrue(coordinator.isActive(second));
    }

    @Test
    public void restartEnforcesMinimumEpochReaffirmsLockdownAndCarriesActivationReplayBlock() {
        PersistedScenario scenario = consumedActivationState();
        final boolean[] reaffirmationScheduled = { false };
        InMemoryActionBroker broker = new InMemoryActionBroker();
        AutomationInputGate inputGate = new AutomationInputGate();
        BrokerBackedDeathSafetyInterlock interlock = new BrokerBackedDeathSafetyInterlock(
            broker,
            inputGate,
            new BrokerBackedDeathSafetyInterlockTest.NoOpInterlockDelegate() {

                @Override
                public void scheduleClientThreadLockdownReaffirmation(long deathEpoch) {
                    reaffirmationScheduled[0] = true;
                }
            });
        DeathSafetyConnectionCoordinator coordinator = new DeathSafetyConnectionCoordinator(
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            interlock);

        try {
            coordinator.restore(scenario.state, BrokerBackedDeathSafetyInterlockTest.connection(7L, "new-player"));
            fail("expected persisted minimum-epoch rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("advance"));
        }

        DeathSafetyConnectionCoordinator.Session first = coordinator
            .restore(scenario.state, BrokerBackedDeathSafetyInterlockTest.connection(8L, "new-player"));

        assertTrue(broker.isSafetyLocked());
        assertTrue(inputGate.blocksAllAutomationInputOwners());
        assertTrue(reaffirmationScheduled[0]);
        assertTrue(
            first.getReplayBlock()
                .isBlocked());
        assertEquals(
            RecoveryPhase.VERIFYING_RECOVERY,
            first.getController()
                .snapshot()
                .getRecoveryPhase());
        assertTrue(
            first.getController()
                .snapshot()
                .getPreDeathInventory()
                .isPresent());
        assertTrue(
            first.getController()
                .snapshot()
                .getStableGrave()
                .isPresent());

        coordinator.disconnect(first, 1L);
        DeathSafetyConnectionCoordinator.Session second = coordinator
            .restore(scenario.state, BrokerBackedDeathSafetyInterlockTest.connection(9L, "newer-player"));
        assertFalse(
            first.getStamps()
                .isOpen());
        assertTrue(coordinator.isActive(second));
        try {
            first.getStamps()
                .next(2L);
            fail("expected stale connection source rejection");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("retired"));
        }

        GraveActivationPacketWriteGate replayGate = new GraveActivationPacketWriteGate(
            second.getController(),
            second.getStamps(),
            noOpProcessor(),
            second.getReplayBlock());
        assertFalse(replayGate.tryWrite(scenario.attempt, 2L, () -> fail("replayed grave write")));
    }

    private static PersistedScenario consumedActivationState() {
        ConnectionSafetyEventStampSource stamps = new ConnectionSafetyEventStampSource(7L);
        DeathSafetyController controller = new DeathSafetyController(
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            new PacketWriteGateTest.NoOpInterlock(),
            BrokerBackedDeathSafetyInterlockTest.connection(7L, "old-player"));
        InventoryManifest preDeath = new InventoryManifest(
            36,
            Collections.singletonList(new InventoryStack("minecraft:diamond|0|none", 3, 64)));
        long deathEpoch = controller
            .onDeathSignal(
                stamps.next(1L),
                DeathSignal.LOCAL_DEATH_CALLBACK,
                new DeathContext(new DimensionBlockPosition(0, 10, 64, 10), "old-player", "task", preDeath))
            .getSnapshot()
            .getDeathEpoch();
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
            deathEpoch,
            permit.getGraveIdentity(),
            true,
            true);
        controller.authorizeGraveActivation(stamps.next(5L), attempt);
        UnresolvedDeathState state = UnresolvedDeathPersistenceAdapter
            .captureCheckpoint(controller.snapshot(), null, 1000L);
        assertTrue(
            state.getGraveState()
                .requiresActivationReplayBlock());
        return new PersistedScenario(state, attempt);
    }

    private static DeathSafetyDirectiveProcessor noOpProcessor() {
        return new DeathSafetyDirectiveProcessor(new DeathSafetyDurableState() {

            @Override
            public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {}

            @Override
            public void clearResolvedDeath() {}
        });
    }

    private static final class PersistedScenario {

        private final UnresolvedDeathState state;
        private final GraveActivationAttempt attempt;

        private PersistedScenario(UnresolvedDeathState state, GraveActivationAttempt attempt) {
            this.state = state;
            this.attempt = attempt;
        }
    }
}
