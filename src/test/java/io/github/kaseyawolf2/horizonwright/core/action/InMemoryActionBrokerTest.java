package io.github.kaseyawolf2.horizonwright.core.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class InMemoryActionBrokerTest {

    @Test
    public void leasesAreExclusivePerCapabilityAndReleaseIsIdempotent() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease movement = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();

        assertFalse(
            broker.tryAcquire("other", EnumSet.of(ActionCapability.MOVEMENT))
                .isPresent());
        assertTrue(
            broker.tryAcquire("camera", EnumSet.of(ActionCapability.LOOK))
                .isPresent());
        assertTrue(movement.isValid());

        movement.close();
        movement.close();

        assertFalse(movement.isValid());
        assertTrue(
            broker.tryAcquire("other", EnumSet.of(ActionCapability.MOVEMENT))
                .isPresent());
    }

    @Test
    public void revokeAllAdvancesEpochAndInvalidatesEveryLease() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker
            .tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK))
            .get();
        long leasedEpoch = lease.getEpoch();

        broker.revokeAll();

        assertEquals(leasedEpoch + 1L, broker.currentEpoch());
        assertFalse(lease.isValid());
        assertTrue(
            broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void safetyLockdownRejectsAcquisitionUntilExplicitlyReleased() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        broker.enterSafetyLockdown();

        Optional<ActionLease> rejected = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT));
        assertFalse(rejected.isPresent());
        assertTrue(broker.isSafetyLocked());

        long lockedEpoch = broker.currentEpoch();
        broker.leaveSafetyLockdown();

        assertFalse(broker.isSafetyLocked());
        assertEquals(lockedEpoch + 1L, broker.currentEpoch());
        assertTrue(
            broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
                .isPresent());
    }

    @Test
    public void deathLockdownAllowsOnlyAnExplicitMovementLookRecoveryLease() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        broker.enterSafetyLockdown();

        ActionLease recovery = broker.tryAcquireSafetyRecovery("death-recovery")
            .get();

        assertTrue(recovery.isSafetyRecoveryLease());
        assertEquals(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK), recovery.getCapabilities());
        assertTrue(recovery.isValid());
        assertFalse(
            broker.tryAcquire("ordinary", EnumSet.of(ActionCapability.MOVEMENT))
                .isPresent());
        assertFalse(
            broker.tryAcquireSafetyRecovery("second-recovery")
                .isPresent());

        broker.revokeAll();
        assertFalse(recovery.isValid());
        assertTrue(
            broker.tryAcquireSafetyRecovery("replacement-recovery")
                .isPresent());
    }

    @Test
    public void operatorStopAlsoStopsSafetyRecoveryAutomation() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        broker.enterSafetyLockdown();
        ActionLease recovery = broker.tryAcquireSafetyRecovery("death-recovery")
            .get();

        broker.enterAutomationLockdown();

        assertFalse(recovery.isValid());
        assertFalse(
            broker.tryAcquireSafetyRecovery("replacement-recovery")
                .isPresent());
    }

    @Test
    public void persistedEpochFloorAdvancesInOneBoundedTransition() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        List<ActionRevocation> observed = new ArrayList<>();
        broker.addRevocationListener(observed::add);

        broker.advanceEpochPast(Long.MAX_VALUE - 2L);

        assertEquals(Long.MAX_VALUE - 1L, broker.currentEpoch());
        assertEquals(1, observed.size());
        assertEquals(
            ActionRevocationReason.RESTORE_EPOCH_ADVANCE,
            observed.get(0)
                .getReason());
    }

    @Test
    public void operatorAutomationStopDoesNotMasqueradeAsDeathSafetyButStillRejectsLeases() {
        InMemoryActionBroker broker = new InMemoryActionBroker();

        broker.enterAutomationLockdown();

        assertTrue(broker.isAutomationLocked());
        assertTrue(broker.isSafetyLocked());
        assertTrue(
            broker.snapshot()
                .isAutomationLocked());
        assertFalse(
            broker.snapshot()
                .isDeathSafetyLocked());
        assertFalse(
            broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
                .isPresent());

        broker.leaveAutomationLockdown();

        assertFalse(broker.isAutomationLocked());
        assertFalse(broker.isSafetyLocked());
        assertTrue(
            broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
                .isPresent());
    }

    @Test
    public void manualResetCannotReleaseAnIndependentDeathLockdown() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        broker.enterSafetyLockdown();
        broker.enterAutomationLockdown();

        broker.leaveAutomationLockdown();

        assertFalse(broker.isAutomationLocked());
        assertTrue(broker.isSafetyLocked());
        assertTrue(
            broker.snapshot()
                .isDeathSafetyLocked());
        assertFalse(
            broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
                .isPresent());
    }

    @Test
    public void revocationListenersRunSynchronouslyAfterLeasesBecomeInvalid() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        List<ActionRevocation> observed = new ArrayList<>();
        broker.addRevocationListener(revocation -> {
            assertFalse(lease.isValid());
            assertEquals(revocation.getNewEpoch(), broker.currentEpoch());
            observed.add(revocation);
        });

        broker.enterSafetyLockdown();

        assertEquals(1, observed.size());
        assertEquals(
            ActionRevocationReason.SAFETY_LOCKDOWN,
            observed.get(0)
                .getReason());
        assertEquals(
            1L,
            observed.get(0)
                .getRevokedEpoch());
        assertEquals(
            2L,
            observed.get(0)
                .getNewEpoch());
    }

    @Test
    public void everyListenerRunsEvenWhenAnEarlierListenerFails() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        List<String> calls = new ArrayList<>();
        broker.addRevocationListener(revocation -> {
            calls.add("first");
            throw new IllegalStateException("expected listener failure");
        });
        broker.addRevocationListener(revocation -> calls.add("second"));

        try {
            broker.revokeAll();
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("revocation listeners failed"));
        }

        assertEquals(2L, broker.currentEpoch());
        assertEquals(java.util.Arrays.asList("first", "second"), calls);
    }

    @Test
    public void listenersCannotAcquireAReplacementLeaseInsideARevocationTransition() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        broker.tryAcquire("old", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        List<Boolean> acquiredDuringListener = new ArrayList<>();
        broker.addRevocationListener(
            revocation -> acquiredDuringListener.add(
                broker.tryAcquire("too-early", EnumSet.of(ActionCapability.MOVEMENT))
                    .isPresent()));

        broker.revokeAll();

        assertEquals(java.util.Arrays.asList(false), acquiredDuringListener);
        assertTrue(
            broker.tryAcquire("after-cleanup", EnumSet.of(ActionCapability.MOVEMENT))
                .isPresent());
    }

    @Test
    public void repeatedEmergencyStopReassertsCleanupWithoutAdvancingTheEpochAgain() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        List<ActionRevocation> observed = new ArrayList<>();
        broker.addRevocationListener(observed::add);

        broker.enterSafetyLockdown();
        long lockedEpoch = broker.currentEpoch();
        broker.enterSafetyLockdown();

        assertEquals(lockedEpoch, broker.currentEpoch());
        assertEquals(2, observed.size());
        assertEquals(
            ActionRevocationReason.SAFETY_LOCKDOWN,
            observed.get(1)
                .getReason());
    }
}
