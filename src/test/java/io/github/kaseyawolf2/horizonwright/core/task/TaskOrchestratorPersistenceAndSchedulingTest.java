package io.github.kaseyawolf2.horizonwright.core.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;

public class TaskOrchestratorPersistenceAndSchedulingTest {

    @Test
    public void dueScheduledChorePreemptsFallbackAtTheSameMonitorFreeTickBoundary() {
        FakeClock clock = new FakeClock();
        TaskRunner fallback = context -> context.isSuspensionRequested()
            ? StepResult.safeSuspension(context.getActionEpoch(), context.getCheckpoint(), "fallback parked")
            : StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "fallback running");
        TaskRunner chore = context -> StepResult
            .completed(context.getActionEpoch(), context.getCheckpoint(), "chore complete");
        TaskOrchestrator orchestrator = orchestrator(
            clock,
            (spec, checkpoint) -> spec.getLane() == TaskLane.FALLBACK ? fallback : chore);
        orchestrator.submitSchedule(
            ScheduleRule.connectedInterval(
                "cleanup",
                ScheduledTaskSpec.of("cleanup", "Cleanup", TaskLane.CHORE),
                100L,
                100L,
                Collections.<String>emptySet(),
                0));
        orchestrator.submit(TaskSpec.of("fallback", "fallback", "Fallback", TaskLane.FALLBACK));

        assertEquals(TaskState.RUNNING, task(orchestrator.tick(connected()), "fallback").getState());
        clock.advance(100L);
        ControllerSnapshot preempted = orchestrator.tick(connected());
        String scheduledId = preempted.getScheduler()
            .findSchedule("cleanup")
            .get()
            .getLastTaskId()
            .get();

        assertEquals(TaskState.SUSPENDED, task(preempted, "fallback").getState());
        assertEquals(TaskSuspensionReason.PREEMPTION, task(preempted, "fallback").getSuspensionReason());
        assertEquals(TaskState.QUEUED, task(preempted, scheduledId).getState());
        assertEquals(
            TaskLane.CHORE,
            task(preempted, scheduledId).getSpec()
                .getLane());

        assertEquals(TaskState.COMPLETED, task(orchestrator.tick(connected()), scheduledId).getState());
        assertEquals(TaskState.RUNNING, task(orchestrator.tick(connected()), "fallback").getState());
    }

    @Test
    public void exportRestoreUsesCheckpointRemainingDelayAndFreshActionEpoch() {
        FakeClock originalClock = new FakeClock();
        TaskCheckpoint checkpoint = new TaskCheckpoint(3L, Collections.singletonMap("phase", "returning"));
        TaskOrchestrator original = orchestrator(
            originalClock,
            (spec, restored) -> context -> StepResult
                .waitFor(context.getActionEpoch(), checkpoint, 500L, "waiting to retry"));
        original.submit(TaskSpec.of("journey", "journey", "Journey", TaskLane.MANUAL));
        TaskSnapshot beforeExport = task(original.tick(), "journey");
        long oldEpoch = beforeExport.getActionEpoch();

        TaskControllerState persisted = original.exportState();
        RestoredTaskSnapshot persistedTask = persisted.getTasks()
            .get(0);
        assertEquals(TaskState.RUNNING, persistedTask.getState());
        assertEquals(checkpoint, persistedTask.getCheckpoint());
        assertEquals(500L, persistedTask.getRemainingDelayMillis());

        FakeClock restoredClock = new FakeClock();
        AtomicInteger restoredSteps = new AtomicInteger();
        TaskOrchestrator restored = orchestrator(restoredClock, (spec, restoredCheckpoint) -> {
            assertEquals(checkpoint, restoredCheckpoint);
            return context -> {
                int step = restoredSteps.incrementAndGet();
                return step == 1
                    ? StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "restored running")
                    : StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "restored complete");
            };
        });
        ControllerSnapshot restoredSnapshot = restored.restoreState(persisted);

        assertEquals(TaskState.QUEUED, task(restoredSnapshot, "journey").getState());
        assertEquals(0L, task(restoredSnapshot, "journey").getActionEpoch());
        restoredClock.advance(499L);
        restored.tick();
        assertEquals(0, restoredSteps.get());
        restoredClock.advance(1L);
        TaskSnapshot running = task(restored.tick(), "journey");
        assertEquals(TaskState.RUNNING, running.getState());
        assertTrue(running.getActionEpoch() > oldEpoch);
        assertFalse(
            restored.acceptRunnerResult(
                "journey",
                StepResult.completed(oldEpoch, checkpoint, "stale pre-reload completion")));
        TaskSnapshot completed = task(restored.tick(), "journey");
        assertEquals(TaskState.COMPLETED, completed.getState());
        assertTrue(completed.getActionEpoch() > oldEpoch);
        assertEquals(checkpoint, completed.getCheckpoint());
    }

    @Test
    public void operatorPauseAndPendingCancellationRestoreSafelyThenComplete() {
        FakeClock clock = new FakeClock();
        TaskRunner progress = context -> StepResult
            .progress(context.getActionEpoch(), context.getCheckpoint(), "running");
        TaskOrchestrator original = orchestrator(clock, (spec, checkpoint) -> progress);
        original.submit(TaskSpec.of("paused", "test", "Paused", TaskLane.CHORE));
        original.pause("paused");
        original.submit(TaskSpec.of("cancelled", "test", "Cancelled", TaskLane.MANUAL));
        original.tick();
        assertEquals(
            TaskState.SUSPENDING,
            original.cancel("cancelled")
                .getState());

        TaskControllerState persisted = original.exportState();
        AtomicInteger restoredCreations = new AtomicInteger();
        AtomicInteger restoredSteps = new AtomicInteger();
        TaskOrchestrator restored = orchestrator(new FakeClock(), (spec, checkpoint) -> {
            restoredCreations.incrementAndGet();
            return context -> {
                restoredSteps.incrementAndGet();
                return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "done");
            };
        });
        ControllerSnapshot snapshot = restored.restoreState(persisted);

        assertEquals(TaskState.SUSPENDED, task(snapshot, "paused").getState());
        assertEquals(TaskSuspensionReason.OPERATOR_PAUSE, task(snapshot, "paused").getSuspensionReason());
        assertEquals(TaskState.CANCELLED, task(snapshot, "cancelled").getState());
        assertEquals(-1, task(snapshot, "cancelled").getQueuePosition());
        assertEquals(1, restoredCreations.get());
        restored.tick();
        assertEquals(0, restoredSteps.get());

        restored.resume("paused");
        assertEquals(TaskState.COMPLETED, task(restored.tick(), "paused").getState());
        assertEquals(1, restoredSteps.get());
    }

    @Test
    public void restoredWorldScheduleCreatesOnlyOneReconnectCatchUpTask() {
        FakeClock clock = new FakeClock();
        TaskOrchestrator original = orchestrator(
            clock,
            (spec, checkpoint) -> context -> StepResult
                .completed(context.getActionEpoch(), context.getCheckpoint(), "done"));
        original.submitSchedule(
            ScheduleRule.worldTimeWindow(
                "farm",
                ScheduledTaskSpec.of("farm", "Farm", TaskLane.CHORE),
                1_000,
                2_000,
                Collections.<String>emptySet(),
                0,
                true));
        original.tick(ScheduleEnvironment.connected(500L, Collections.<String>emptySet()));

        TaskOrchestrator restored = orchestrator(
            new FakeClock(),
            (spec, checkpoint) -> context -> StepResult
                .completed(context.getActionEpoch(), context.getCheckpoint(), "done"));
        restored.restoreState(original.exportState());
        long fiveDaysLater = 5L * ScheduleRule.WORLD_DAY_TICKS + 5_000L;
        ControllerSnapshot catchUp = restored
            .tick(ScheduleEnvironment.reconnected(fiveDaysLater, Collections.<String>emptySet()));

        assertEquals(
            1,
            catchUp.getTasks()
                .size());
        assertEquals(
            TaskState.COMPLETED,
            catchUp.getTasks()
                .get(0)
                .getState());
        assertEquals(
            1L,
            catchUp.getScheduler()
                .findSchedule("farm")
                .get()
                .getCatchUpRuns());
        ControllerSnapshot idempotent = restored
            .tick(ScheduleEnvironment.connected(fiveDaysLater, Collections.<String>emptySet()));
        assertEquals(
            1,
            idempotent.getTasks()
                .size());
        assertEquals(
            1L,
            idempotent.getScheduler()
                .findSchedule("farm")
                .get()
                .getTotalRuns());
    }

    @Test
    public void restorePreservesLaneQueueOrderAndBlockedTasksRemainFailClosed() {
        BlockedReason reason = BlockedReason
            .missingRequirement("container absent", "base", "DROP_OFF", "Register the container.");
        RestoredTaskSnapshot second = restoredTask("second", TaskState.QUEUED, TaskSuspensionReason.NONE, null, 1);
        RestoredTaskSnapshot blocked = restoredTask("blocked", TaskState.BLOCKED, TaskSuspensionReason.NONE, reason, 0);
        TaskControllerState persisted = new TaskControllerState(
            Arrays.asList(second, blocked),
            SchedulerSnapshot.empty());
        AtomicInteger creations = new AtomicInteger();
        TaskOrchestrator restored = orchestrator(new FakeClock(), (spec, checkpoint) -> {
            creations.incrementAndGet();
            return context -> StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "done");
        });

        ControllerSnapshot snapshot = restored.restoreState(persisted);
        assertEquals(
            Arrays.asList("blocked", "second"),
            ids(
                snapshot.getQueue()
                    .getLane(TaskLane.CHORE)));
        assertEquals(1, creations.get());
        assertEquals(TaskState.COMPLETED, task(restored.tick(), "second").getState());
        assertEquals(TaskState.BLOCKED, task(restored.snapshot(), "blocked").getState());

        restored.resume("blocked");
        assertEquals(2, creations.get());
        assertEquals(TaskState.COMPLETED, task(restored.tick(), "blocked").getState());
    }

    @Test
    public void invalidControllerStateAndNonPristineRestoreAreRejected() {
        RestoredTaskSnapshot positionOne = restoredTask(
            "position-one",
            TaskState.QUEUED,
            TaskSuspensionReason.NONE,
            null,
            1);
        assertIllegalArgument(
            () -> new TaskControllerState(Collections.singletonList(positionOne), SchedulerSnapshot.empty()));
        assertIllegalArgument(
            () -> new RestoredTaskSnapshot(
                TaskSpec.of("bad", "test", "Bad", TaskLane.CHORE),
                TaskState.BLOCKED,
                TaskCheckpoint.empty(),
                0,
                0L,
                TaskSuspensionReason.NONE,
                null,
                0,
                0L,
                "",
                null));

        TaskOrchestrator nonPristine = orchestrator(
            new FakeClock(),
            (spec, checkpoint) -> context -> StepResult
                .completed(context.getActionEpoch(), context.getCheckpoint(), "done"));
        nonPristine.submit(TaskSpec.of("existing", "test", "Existing", TaskLane.MANUAL));
        try {
            nonPristine.restoreState(TaskControllerState.empty());
            fail("expected non-pristine restore rejection");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("empty"));
        }
    }

    private static RestoredTaskSnapshot restoredTask(String id, TaskState state, TaskSuspensionReason suspensionReason,
        BlockedReason blockedReason, int queuePosition) {
        return new RestoredTaskSnapshot(
            TaskSpec.of(id, "test", id, TaskLane.CHORE),
            state,
            TaskCheckpoint.empty(),
            0,
            0L,
            suspensionReason,
            blockedReason,
            queuePosition,
            0L,
            "persisted",
            null);
    }

    private static TaskOrchestrator orchestrator(FakeClock clock, TaskRunnerFactory factory) {
        return new TaskOrchestrator(clock, factory, new InMemoryActionBroker());
    }

    private static ScheduleEnvironment connected() {
        return ScheduleEnvironment.connected(ScheduleEnvironment.UNKNOWN_WORLD_TIME, Collections.<String>emptySet());
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String taskId) {
        return snapshot.findTask(taskId)
            .orElseThrow(() -> new AssertionError("missing task " + taskId));
    }

    private static List<String> ids(List<TaskSnapshot> tasks) {
        List<String> ids = new ArrayList<>();
        for (TaskSnapshot task : tasks) {
            ids.add(
                task.getSpec()
                    .getId());
        }
        return ids;
    }

    private static void assertIllegalArgument(Runnable invocation) {
        try {
            invocation.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static final class FakeClock implements MonotonicClock {

        private long now;

        @Override
        public long nowMillis() {
            return now;
        }

        private void advance(long millis) {
            now += millis;
        }
    }
}
