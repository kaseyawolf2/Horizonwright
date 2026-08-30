package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.EnumSet;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ConnectionIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathContext;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyPolicy;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;

public class BrokerBackedDeathSafetyInterlockTest {

    @Test
    public void deathLatchSynchronouslyLocksBrokerAndEveryInputProducerBeforeCleanupDelegation() {
        final InMemoryActionBroker broker = new InMemoryActionBroker();
        final AutomationInputGate inputGate = new AutomationInputGate();
        final ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .orElseThrow(() -> new AssertionError("missing lease"));
        final boolean[] synchronousStop = { false };
        final boolean[] cleanupScheduled = { false };
        ClientDeathInterlockDelegate delegate = new NoOpInterlockDelegate() {

            @Override
            public void performSynchronousEmergencyStop(DeathLatchRecord record) {
                assertTrue(broker.isSafetyLocked());
                assertTrue(inputGate.blocksAllAutomationInputOwners());
                assertFalse(lease.isValid());
                synchronousStop[0] = true;
            }

            @Override
            public void scheduleClientThreadCleanup(DeathLatchRecord record) {
                assertTrue(synchronousStop[0]);
                cleanupScheduled[0] = true;
            }
        };
        BrokerBackedDeathSafetyInterlock interlock = new BrokerBackedDeathSafetyInterlock(broker, inputGate, delegate);
        DeathSafetyController controller = new DeathSafetyController(
            testPolicy(),
            interlock,
            connection(2L, "old-player"));

        controller.onDeathSignal(new SafetyEventStamp(2L, 1L, 1L), DeathSignal.LOCAL_DEATH_CALLBACK, deathContext());

        assertTrue(synchronousStop[0]);
        assertTrue(cleanupScheduled[0]);
        assertTrue(
            interlock.latestLatch()
                .isPresent());
        assertEquals(1L, inputGate.getDeathEpoch());
    }

    @Test
    public void listenerFailureCannotPreventEmergencyStopOrClientCleanupScheduling() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        broker.addRevocationListener(revocation -> { throw new IllegalStateException("listener failed"); });
        AutomationInputGate inputGate = new AutomationInputGate();
        final boolean[] synchronousStop = { false };
        final boolean[] cleanupScheduled = { false };
        BrokerBackedDeathSafetyInterlock interlock = new BrokerBackedDeathSafetyInterlock(
            broker,
            inputGate,
            new NoOpInterlockDelegate() {

                @Override
                public void performSynchronousEmergencyStop(DeathLatchRecord record) {
                    synchronousStop[0] = true;
                }

                @Override
                public void scheduleClientThreadCleanup(DeathLatchRecord record) {
                    cleanupScheduled[0] = true;
                }
            });
        DeathSafetyController controller = new DeathSafetyController(
            testPolicy(),
            interlock,
            connection(2L, "old-player"));

        try {
            controller
                .onDeathSignal(new SafetyEventStamp(2L, 1L, 1L), DeathSignal.LOCAL_DEATH_CALLBACK, deathContext());
            fail("expected listener failure");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("revocation"));
        }

        assertTrue(broker.isSafetyLocked());
        assertTrue(inputGate.blocksAllAutomationInputOwners());
        assertTrue(synchronousStop[0]);
        assertTrue(cleanupScheduled[0]);
    }

    static DeathSafetyPolicy testPolicy() {
        return new DeathSafetyPolicy(0.4D, 0.6D, 1, 1, 1, 6);
    }

    static ConnectionIdentity connection(long epoch, String playerIdentity) {
        return new ConnectionIdentity(epoch, "server", "world", playerIdentity);
    }

    static DeathContext deathContext() {
        return new DeathContext(
            new DimensionBlockPosition(0, 10, 64, 10),
            "old-player",
            "task",
            new InventoryManifest(36, Collections.emptyList()));
    }

    static class NoOpInterlockDelegate implements ClientDeathInterlockDelegate {

        @Override
        public void onCriticalRestrictionsEntered() {}

        @Override
        public void onCriticalRestrictionsReleased() {}

        @Override
        public void performSynchronousEmergencyStop(DeathLatchRecord record) {}

        @Override
        public void scheduleClientThreadCleanup(DeathLatchRecord record) {}

        @Override
        public void scheduleClientThreadLockdownReaffirmation(long deathEpoch) {}

        @Override
        public void beforeDeathLockdownReleased(long deathEpoch) {}
    }
}
