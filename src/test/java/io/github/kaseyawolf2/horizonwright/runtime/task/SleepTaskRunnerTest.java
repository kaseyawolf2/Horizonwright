package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.SleepObservation;
import io.github.kaseyawolf2.horizonwright.core.base.SleepProviderKind;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedCause;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class SleepTaskRunnerTest {

    private Harness harness;

    @After
    public void closeHarness() {
        if (harness != null) harness.close();
    }

    @Test
    public void missingBackendBlocksWithoutGameplayAuthority() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        TaskOrchestrator controller = new TaskOrchestrator(
            new FixedClock(),
            new RuntimeTaskRunnerFactory(UnusedNavigationAccess.INSTANCE),
            broker);
        try {
            TaskSpec spec = SleepTask.once("sleep", "home-bed");
            controller.submit(spec);
            TaskSnapshot task = task(controller.tick(), spec.getId());

            assertEquals(TaskState.BLOCKED, task.getState());
            assertEquals(
                BlockedCause.MISSING_REQUIREMENT,
                task.getBlockedReason()
                    .get()
                    .getCause());
            assertEquals(TaskCheckpoint.empty(), task.getCheckpoint());
            assertTrue(
                broker.snapshot()
                    .getActiveOwners()
                    .isEmpty());
        } finally {
            controller.close();
        }
    }

    @Test
    public void verifiedDaytimeCompletesWithoutInteraction() {
        harness = new Harness(observation(1L, 1000L, false, true, true));
        TaskSpec spec = SleepTask.once("sleep", "home-bed");
        harness.controller.submit(spec);

        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(
            "true",
            completed.getCheckpoint()
                .getValues()
                .get("verified"));
        assertEquals(0, harness.backend.actions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void dangerBlocksBeforeLeaseOrInteraction() {
        harness = new Harness(observation(1L, 13000L, true, true, true));
        TaskSpec spec = SleepTask.once("sleep", "home-bed");
        harness.controller.submit(spec);

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertTrue(
            blocked.getBlockedReason()
                .get()
                .getDetail()
                .contains("Danger"));
        assertEquals(0, harness.backend.actions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void interactionCompletesOnlyAfterBackendConfirmation() {
        harness = new Harness(observation(1L, 13000L, false, true, true));
        TaskSpec spec = SleepTask.once("sleep", "home-bed");
        harness.controller.submit(spec);

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        TaskSnapshot waiting = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, submitted.getState());
        assertEquals(TaskCheckpoint.empty(), waiting.getCheckpoint());
        assertEquals(1, harness.backend.actions);
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.MOVEMENT));
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.LOOK));
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.USE));
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.DIG));

        harness.backend.handle.state = SleepBackend.ActionState.CONFIRMED;
        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(
            "home-bed",
            completed.getCheckpoint()
                .getValues()
                .get("bedLocationId"));
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void pauseCancelsUnconfirmedInteractionAndResumeReobserves() {
        harness = new Harness(observation(1L, 13000L, false, true, true));
        TaskSpec spec = SleepTask.once("sleep", "home-bed");
        harness.controller.submit(spec);
        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        RecordingBackend.Handle first = harness.backend.handle;

        harness.controller.pause(spec.getId());
        TaskSnapshot suspended = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.SUSPENDED, suspended.getState());
        assertEquals(submitted.getCheckpoint(), suspended.getCheckpoint());
        assertEquals(SleepBackend.ActionState.CANCELLED, first.state);

        harness.controller.resume(spec.getId());
        task(harness.controller.tick(), spec.getId());
        assertEquals(2, harness.backend.observations);
        assertEquals(2, harness.backend.actions);
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String id) {
        return snapshot.findTask(id)
            .orElseThrow(() -> new AssertionError("missing task " + id));
    }

    private static SleepObservation observation(long revision, long time, boolean danger, boolean available,
        boolean reachable) {
        return new SleepObservation(
            revision,
            "sleep-" + revision + "-" + time + "-" + danger,
            0,
            time,
            true,
            danger,
            SleepProviderKind.REGISTERED_BED,
            new BasePosition(0, 5, 64, 5),
            available,
            reachable);
    }

    private static final class Harness implements AutoCloseable {

        private final InMemoryActionBroker broker = new InMemoryActionBroker();
        private final RecordingBackend backend;
        private final TaskOrchestrator controller;

        private Harness(SleepObservation observation) {
            backend = new RecordingBackend(observation);
            controller = new TaskOrchestrator(
                new FixedClock(),
                new RuntimeTaskRunnerFactory(UnusedNavigationAccess.INSTANCE, new Access(backend)),
                broker);
        }

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class Access implements SleepRuntimeAccess {

        private final SleepBackend backend;

        private Access(SleepBackend backend) {
            this.backend = backend;
        }

        @Override
        public SleepBackend getSleepBackend() {
            return backend;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private static final class RecordingBackend implements SleepBackend {

        private final SleepObservation observation;
        private int observations;
        private int actions;
        private ActionLease lease;
        private Handle handle;

        private RecordingBackend(SleepObservation observation) {
            this.observation = observation;
        }

        @Override
        public Availability availability() {
            return Availability.available("recording sleep backend ready");
        }

        @Override
        public ObservationSnapshot observe(ObservationRequest request) {
            observations++;
            return new ObservationSnapshot(
                request.getTaskId(),
                request.getBedLocationId(),
                request.getActionEpoch(),
                observation);
        }

        @Override
        public ActionHandle execute(ActionRequest request, ActionLease lease) {
            actions++;
            this.lease = lease;
            handle = new Handle(request.getRequestId());
            return handle;
        }

        private static final class Handle implements ActionHandle {

            private final String requestId;
            private ActionState state = ActionState.EXECUTING;

            private Handle(String requestId) {
                this.requestId = requestId;
            }

            @Override
            public String getRequestId() {
                return requestId;
            }

            @Override
            public ActionProgress progress() {
                return new ActionProgress(requestId, state, state.name());
            }

            @Override
            public void cancel() {
                state = ActionState.CANCELLED;
            }
        }
    }

    private enum UnusedNavigationAccess implements NavigationRuntimeAccess {

        INSTANCE;

        @Override
        public NavigationBackend getNavigationBackend() {
            return null;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }

        @Override
        public void publishNavigationProgress(NavigationProgress progress) {}
    }

    private static final class FixedClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
