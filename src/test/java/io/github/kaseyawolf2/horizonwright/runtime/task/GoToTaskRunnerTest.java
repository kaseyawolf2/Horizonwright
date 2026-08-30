package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedCause;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.RestoredTaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.SchedulerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSuspensionReason;

public class GoToTaskRunnerTest {

    private Harness harness;

    @After
    public void closeHarness() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    public void startsAndCompletesThroughTheTaskActionGateway() {
        harness = new Harness();
        TaskSpec spec = GoToTask.create("home", 0, 12, 64, -8, 1);
        harness.controller.submit(spec);

        TaskSnapshot running = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, running.getState());
        assertEquals(1, harness.backend.submissions);
        assertEquals("home-nav-1", harness.backend.lastRequest.getRequestId());
        assertEquals(
            "navigating",
            running.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(
            "1",
            running.getCheckpoint()
                .getValues()
                .get("attempt"));
        assertEquals(
            2,
            harness.broker.snapshot()
                .getActiveOwners()
                .size());
        assertEquals(
            "task:home",
            harness.broker.snapshot()
                .getActiveOwners()
                .get(ActionCapability.MOVEMENT));
        assertEquals(
            "task:home",
            harness.broker.snapshot()
                .getActiveOwners()
                .get(ActionCapability.LOOK));

        harness.backend.complete();
        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(
            "completed",
            completed.getCheckpoint()
                .getValues()
                .get("phase"));
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
        assertNotNull(harness.access.lastProgress);
        assertEquals(NavigationState.COMPLETED, harness.access.lastProgress.getState());
    }

    @Test
    public void pauseResumeAndCancelAlwaysReleaseNavigation() {
        harness = new Harness();
        TaskSpec spec = GoToTask.create("route", 0, 4, 65, 9, 0);
        harness.controller.submit(spec);
        harness.controller.tick();
        RecordingBackend.Handle firstHandle = harness.backend.active;

        assertEquals(
            TaskState.SUSPENDING,
            harness.controller.pause(spec.getId())
                .getState());
        TaskSnapshot suspended = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.SUSPENDED, suspended.getState());
        assertEquals(NavigationState.CANCELLED, firstHandle.state);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());

        harness.controller.resume(spec.getId());
        TaskSnapshot resumed = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, resumed.getState());
        assertEquals(2, harness.backend.submissions);
        assertEquals("route-nav-2", harness.backend.lastRequest.getRequestId());
        RecordingBackend.Handle secondHandle = harness.backend.active;

        assertEquals(
            TaskState.SUSPENDING,
            harness.controller.cancel(spec.getId())
                .getState());
        TaskSnapshot cancelled = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.CANCELLED, cancelled.getState());
        assertEquals(NavigationState.CANCELLED, secondHandle.state);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void aNavigatingCheckpointRestartsAsANewBoundedRequestAfterReload() {
        harness = new Harness();
        TaskSpec spec = GoToTask.create("reload", -1, 3, 90, 5, 2);
        harness.controller.submit(spec);
        TaskCheckpoint navigating = task(harness.controller.tick(), spec.getId()).getCheckpoint();
        assertEquals(
            "reload-nav-1",
            navigating.getValues()
                .get("requestId"));

        harness.controller.cancel(spec.getId());
        harness.controller.tick();
        harness.close();

        harness = new Harness();
        harness.controller.restore(spec, navigating);
        TaskSnapshot restored = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, restored.getState());
        assertEquals("reload-nav-2", harness.backend.lastRequest.getRequestId());
        assertTrue(harness.backend.lastLease.isValid());
        assertEquals(restored.getActionEpoch(), harness.backend.lastLease.getEpoch());
    }

    @Test
    public void completedCheckpointCannotRestartFromPersistedRunningState() {
        harness = new Harness();
        TaskSpec spec = GoToTask.create("impossible", 0, 3, 64, 5, 1);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("phase", "completed");
        values.put("attempt", "1");
        values.put("requestId", "impossible-nav-1");
        TaskCheckpoint completed = new TaskCheckpoint(2L, values);
        RestoredTaskSnapshot persisted = new RestoredTaskSnapshot(
            spec,
            TaskState.RUNNING,
            completed,
            0,
            0L,
            TaskSuspensionReason.NONE,
            null,
            0,
            0L,
            "persisted while running",
            null);

        ControllerSnapshot restored = harness.controller
            .restoreState(new TaskControllerState(0L, Collections.singletonList(persisted), SchedulerSnapshot.empty()));

        TaskSnapshot blocked = task(restored, spec.getId());
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertTrue(
            blocked.getDetail()
                .contains("completed GoTo checkpoint cannot be resumed"));
        assertEquals(0, harness.backend.submissions);
        harness.controller.tick();
        assertEquals(0, harness.backend.submissions);
    }

    @Test
    public void backendLossCancelsTheHandleAndQueuesABoundedRetry() {
        harness = new Harness();
        TaskSpec spec = GoToTask.create("backend-loss", 0, 1, 64, 1, 1);
        harness.controller.submit(spec);
        harness.controller.tick();
        RecordingBackend.Handle handle = harness.backend.active;

        harness.backend.available = false;
        harness.backend.diagnostic = "backend disconnected";
        TaskSnapshot retrying = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.QUEUED, retrying.getState());
        assertEquals(1, retrying.getRetryCount());
        assertEquals(1_000L, retrying.getNextEligibleAtMillis());
        assertEquals(NavigationState.CANCELLED, handle.state);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void externalEpochRevocationInterruptsAndBlocksStaleNavigation() {
        harness = new Harness();
        TaskSpec spec = GoToTask.create("stale", 0, 1, 64, 1, 1);
        harness.controller.submit(spec);
        long activeEpoch = task(harness.controller.tick(), spec.getId()).getActionEpoch();
        RecordingBackend.Handle handle = harness.backend.active;

        harness.broker.revokeAll();
        TaskSnapshot blocked = task(harness.controller.snapshot(), spec.getId());

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            BlockedCause.EXTERNAL_FAILURE,
            blocked.getBlockedReason()
                .get()
                .getCause());
        assertTrue(harness.broker.currentEpoch() > activeEpoch);
        assertEquals(NavigationState.CANCELLED, handle.state);
        assertFalse(
            harness.broker.snapshot()
                .getActiveOwners()
                .containsKey(ActionCapability.MOVEMENT));
    }

    @Test
    public void dryRunBlocksBeforeRequestingAnyActionLease() {
        harness = new Harness();
        harness.access.dryRun = true;
        TaskSpec spec = GoToTask.create("dry", 0, 1, 64, 1, 1);
        harness.controller.submit(spec);

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            BlockedCause.MISSING_REQUIREMENT,
            blocked.getBlockedReason()
                .get()
                .getCause());
        assertEquals(0, harness.backend.submissions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String taskId) {
        return snapshot.findTask(taskId)
            .orElseThrow(() -> new AssertionError("missing task " + taskId));
    }

    private static final class Harness implements AutoCloseable {

        private final InMemoryActionBroker broker = new InMemoryActionBroker();
        private final RecordingBackend backend = new RecordingBackend();
        private final Access access = new Access(backend);
        private final TaskOrchestrator controller = new TaskOrchestrator(
            new FixedClock(),
            new RuntimeTaskRunnerFactory(access),
            broker);

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class Access implements NavigationRuntimeAccess {

        private NavigationBackend backend;
        private boolean dryRun;
        private NavigationProgress lastProgress;

        private Access(NavigationBackend backend) {
            this.backend = backend;
        }

        @Override
        public NavigationBackend getNavigationBackend() {
            return backend;
        }

        @Override
        public boolean isDryRun() {
            return dryRun;
        }

        @Override
        public void publishNavigationProgress(NavigationProgress progress) {
            lastProgress = progress;
        }
    }

    private static final class RecordingBackend implements NavigationBackend {

        private boolean available = true;
        private String diagnostic = "recording backend ready";
        private int submissions;
        private NavigationRequest lastRequest;
        private ActionLease lastLease;
        private Handle active;

        @Override
        public BackendAvailability availability() {
            return available ? BackendAvailability.available(diagnostic) : BackendAvailability.unavailable(diagnostic);
        }

        @Override
        public NavigationHandle submit(NavigationRequest request, ActionLease movementLease) {
            assertTrue(
                movementLease.getCapabilities()
                    .containsAll(Collections.unmodifiableSet(EnumSetHolder.MOVEMENT_AND_LOOK)));
            submissions++;
            lastRequest = request;
            lastLease = movementLease;
            active = new Handle(request, movementLease);
            return active;
        }

        private void complete() {
            active.state = NavigationState.COMPLETED;
            active.detail = "target reached";
        }

        private static final class Handle implements NavigationHandle {

            private final NavigationRequest request;
            private final ActionLease lease;
            private NavigationState state = NavigationState.SUBMITTED;
            private String detail = "submitted";

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
                return new NavigationProgress(request.getRequestId(), request.getActionEpoch(), state, detail);
            }

            @Override
            public void cancel() {
                if (state != NavigationState.COMPLETED && state != NavigationState.FAILED) {
                    state = NavigationState.CANCELLED;
                    detail = "cancelled";
                }
                lease.close();
            }
        }
    }

    private static final class EnumSetHolder {

        private static final java.util.EnumSet<ActionCapability> MOVEMENT_AND_LOOK = java.util.EnumSet
            .of(ActionCapability.MOVEMENT, ActionCapability.LOOK);

        private EnumSetHolder() {}
    }

    private static final class FixedClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
