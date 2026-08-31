package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Optional;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationStatus;

public class LiveDeathSafetyControlsTest {

    @Test
    public void submitsAndPollsMovementLookOnlyRecoveryWithoutReleasingDeathLockdown() {
        HorizonwrightRuntime runtime = HorizonwrightRuntime.createSession();
        FakeBackend backend = new FakeBackend();
        runtime.installNavigationBackend(backend);
        runtime.getActionSessionGuard()
            .markFirewallInstalled();
        runtime.getActionBroker()
            .enterSafetyLockdown();
        LiveDeathSafetyControls controls = new LiveDeathSafetyControls(runtime);

        controls.startInteractionDisabledRecoveryNavigation(request(4L), null);

        assertTrue(backend.lease.isSafetyRecoveryLease());
        assertEquals(
            2,
            backend.lease.getCapabilities()
                .size());
        assertTrue(
            backend.lease.getCapabilities()
                .contains(ActionCapability.MOVEMENT));
        assertTrue(
            backend.lease.getCapabilities()
                .contains(ActionCapability.LOOK));
        assertEquals(6, backend.request.getTolerance());
        assertEquals(Optional.of(RecoveryNavigationStatus.IN_PROGRESS), controls.pollRecoveryNavigation(4L));

        backend.handle.state = NavigationState.COMPLETED;
        assertEquals(Optional.of(RecoveryNavigationStatus.ARRIVED), controls.pollRecoveryNavigation(4L));
        assertFalse(backend.lease.isValid());
        assertTrue(
            runtime.getActionBroker()
                .isDeathSafetyLocked());
    }

    @Test
    public void unavailableBackendProducesOneExplicitFailureObservation() {
        HorizonwrightRuntime runtime = HorizonwrightRuntime.createSession();
        LiveDeathSafetyControls controls = new LiveDeathSafetyControls(runtime);

        controls.startInteractionDisabledRecoveryNavigation(request(9L), null);

        assertEquals(Optional.of(RecoveryNavigationStatus.FAILED), controls.pollRecoveryNavigation(9L));
        assertEquals(Optional.empty(), controls.pollRecoveryNavigation(9L));
    }

    private static RecoveryNavigationRequest request(long deathEpoch) {
        try {
            Constructor<RecoveryNavigationRequest> constructor = RecoveryNavigationRequest.class
                .getDeclaredConstructor(long.class, DimensionBlockPosition.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(deathEpoch, new DimensionBlockPosition(0, 10, 64, 10), 6);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class FakeBackend implements NavigationBackend {

        private final FakeHandle handle = new FakeHandle();
        private NavigationRequest request;
        private ActionLease lease;

        @Override
        public BackendAvailability availability() {
            return BackendAvailability.available("test backend ready");
        }

        @Override
        public NavigationHandle submit(NavigationRequest submitted, ActionLease movementLease) {
            request = submitted;
            lease = movementLease;
            handle.request = submitted;
            return handle;
        }
    }

    private static final class FakeHandle implements NavigationHandle {

        private NavigationRequest request;
        private NavigationState state = NavigationState.MOVING;

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public NavigationProgress progress() {
            return new NavigationProgress(request.getRequestId(), request.getActionEpoch(), state, "test");
        }

        @Override
        public void cancel() {
            state = NavigationState.CANCELLED;
        }
    }
}
