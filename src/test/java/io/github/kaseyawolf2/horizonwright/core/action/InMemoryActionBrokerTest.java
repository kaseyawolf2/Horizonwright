package io.github.kaseyawolf2.horizonwright.core.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
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
}
