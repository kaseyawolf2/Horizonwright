package io.github.kaseyawolf2.horizonwright;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocation;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocationListener;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;

public class HorizonwrightRuntimeTest {

    @Test
    public void explicitEnvironmentTicksControllerAndCloseRevokesActiveWork() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        RecordingBackend backend = new RecordingBackend();
        HorizonwrightRuntime runtime = new HorizonwrightRuntime(broker, new ActionSessionGuard(), new FixedClock());
        runtime.installNavigationBackend(backend);
        runtime.submitGoTo(0, 4, 64, 4, 1);

        ScheduleEnvironment environment = ScheduleEnvironment.connected(6_000L, Collections.singleton("daytime"));
        assertEquals(
            TaskState.RUNNING,
            runtime.clientTick(environment)
                .getTasks()
                .get(0)
                .getState());
        assertEquals(1, backend.clientTicks);
        assertEquals(
            2,
            broker.snapshot()
                .getActiveOwners()
                .size());
        assertSame(runtime.getController(), runtime.getController());

        runtime.close();

        assertTrue(broker.isAutomationLocked());
        assertFalse(broker.isDeathSafetyLocked());
        assertEquals(NavigationState.CANCELLED, backend.handle.state);
        assertTrue(
            broker.snapshot()
                .getActiveOwners()
                .isEmpty());
        try {
            runtime.clientTick(environment);
            fail("closed runtime must reject further ticks");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("closed"));
        }
    }

    @Test
    public void backendReplacementRemovesThePreviousRevocationListener() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        HorizonwrightRuntime runtime = new HorizonwrightRuntime(broker, new ActionSessionGuard(), new FixedClock());
        RecordingBackend first = new RecordingBackend();
        RecordingBackend second = new RecordingBackend();

        runtime.installNavigationBackend(first);
        runtime.installNavigationBackend(second);
        broker.revokeAll();

        assertEquals(0, first.revocations);
        assertEquals(1, second.revocations);
        assertEquals(
            "recording backend",
            runtime.snapshot()
                .getNavigationDiagnostic());
        runtime.close();
    }

    @Test
    public void enablingDryRunPausesActiveWorkAndPreventsAQueuedTaskFromAcquiring() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        RecordingBackend backend = new RecordingBackend();
        HorizonwrightRuntime runtime = new HorizonwrightRuntime(broker, new ActionSessionGuard(), new FixedClock());
        runtime.installNavigationBackend(backend);
        runtime.submitGoTo(0, 2, 64, 2, 1);
        runtime.clientTick(ScheduleEnvironment.disconnected());

        runtime.setDryRun(true);
        assertTrue(runtime.isDryRun());
        assertEquals(
            TaskState.SUSPENDING,
            runtime.controllerSnapshot()
                .getTasks()
                .get(0)
                .getState());
        assertEquals(
            TaskState.SUSPENDED,
            runtime.clientTick(ScheduleEnvironment.disconnected())
                .getTasks()
                .get(0)
                .getState());
        assertTrue(
            broker.snapshot()
                .getActiveOwners()
                .isEmpty());
        assertFalse(backend.handle.lease.isValid());
        runtime.close();
    }

    @Test
    public void restoredNavigationSequenceDoesNotReusePersistedTaskIds() {
        HorizonwrightRuntime original = new HorizonwrightRuntime(
            new InMemoryActionBroker(),
            new ActionSessionGuard(),
            new FixedClock());
        assertEquals(
            "goto-1",
            original.submitGoTo(0, 2, 64, 2, 1)
                .getSpec()
                .getId());

        HorizonwrightRuntime restored = new HorizonwrightRuntime(
            new InMemoryActionBroker(),
            new ActionSessionGuard(),
            new FixedClock());
        restored.restoreControllerState(original.exportControllerState());

        assertEquals(
            "goto-2",
            restored.createGoToTaskSpec(0, 4, 64, 4, 1)
                .getId());
        original.close();
        restored.close();
    }

    @Test
    public void manualStopCanBeResetWithoutEnteringTheDeathPacketLockdown() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        HorizonwrightRuntime runtime = new HorizonwrightRuntime(broker, guard, new FixedClock());

        runtime.stopAutomation("operator test");

        assertTrue(broker.isAutomationLocked());
        assertFalse(
            broker.snapshot()
                .isDeathSafetyLocked());
        assertEquals(ActionSessionGuard.Mode.PLAYER, guard.getMode());
        try {
            runtime.submitGoTo(0, 4, 64, 4, 1);
            fail("manual automation stop must reject new work");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("/hw reset"));
        }
        try {
            runtime.resumeTask("anything");
            fail("manual automation stop must reject resumes");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("/hw reset"));
        }
        assertTrue(runtime.resetAutomationStop());
        assertFalse(runtime.resetAutomationStop());
        assertFalse(broker.isSafetyLocked());
        runtime.close();
    }

    @Test
    public void excavationSubmissionAcceptsOnlyExcavationSpecsAndHonorsAutomationStop() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        HorizonwrightRuntime runtime = new HorizonwrightRuntime(broker, new ActionSessionGuard(), new FixedClock());

        assertEquals(
            "quarry",
            runtime.submitExcavation(ExcavationTask.cleanVolumeCylinder("quarry", 0, 10, 20, 2, 60, 64))
                .getSpec()
                .getId());
        try {
            runtime.submitExcavation(runtime.createGoToTaskSpec(0, 4, 64, 4, 1));
            fail("the excavation entry point must reject other task types");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("excavation"));
        }

        runtime.stopAutomation("operator test");
        try {
            runtime.submitExcavation(ExcavationTask.cleanVolumeCylinder("blocked", 0, 0, 0, 1, 60, 60));
            fail("automation stop must reject new excavation work");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("/hw reset"));
        }
        runtime.close();
    }

    @Test
    public void farmSubmissionAcceptsOnlyFarmPassSpecsAndHonorsAutomationStop() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        HorizonwrightRuntime runtime = new HorizonwrightRuntime(broker, new ActionSessionGuard(), new FixedClock());

        assertEquals(
            "farm",
            runtime.submitFarm(FarmTask.finitePass("farm", "north-field", 2))
                .getSpec()
                .getId());
        try {
            runtime.submitFarm(runtime.createGoToTaskSpec(0, 4, 64, 4, 1));
            fail("the farm entry point must reject other task types");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("farm-pass"));
        }

        runtime.stopAutomation("operator test");
        try {
            runtime.submitFarm(FarmTask.finitePass("blocked", "north-field", 2));
            fail("automation stop must reject new farm work");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("/hw reset"));
        }
        runtime.close();
    }

    private static final class RecordingBackend implements NavigationBackend, ActionRevocationListener {

        private int clientTicks;
        private int revocations;
        private Handle handle;

        @Override
        public BackendAvailability availability() {
            return BackendAvailability.available("recording backend");
        }

        @Override
        public NavigationHandle submit(NavigationRequest request, ActionLease movementLease) {
            handle = new Handle(request, movementLease);
            return handle;
        }

        @Override
        public void clientTick() {
            clientTicks++;
        }

        @Override
        public void onActionEpochRevoked(ActionRevocation revocation) {
            revocations++;
        }

        private static final class Handle implements NavigationHandle {

            private final NavigationRequest request;
            private final ActionLease lease;
            private NavigationState state = NavigationState.SUBMITTED;

            private Handle(NavigationRequest request, ActionLease lease) {
                this.request = request;
                this.lease = lease;
            }

            @Override
            public String getRequestId() {
                return request.getRequestId();
            }

            @Override
            public NavigationProgress progress() {
                return new NavigationProgress(request.getRequestId(), request.getActionEpoch(), state, "recorded");
            }

            @Override
            public void cancel() {
                state = NavigationState.CANCELLED;
            }
        }
    }

    private static final class FixedClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
