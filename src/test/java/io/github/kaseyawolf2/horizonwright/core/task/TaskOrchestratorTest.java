package io.github.kaseyawolf2.horizonwright.core.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;

public class TaskOrchestratorTest {

    @Test
    public void selectsFixedLanesInSafetyFirstOrder() {
        FakeClock clock = new FakeClock();
        List<String> execution = new ArrayList<>();
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, checkpoint) -> context -> {
            execution.add(spec.getId());
            return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "done");
        });

        orchestrator.submit(spec("fallback", TaskLane.FALLBACK));
        orchestrator.submit(spec("chore", TaskLane.CHORE));
        orchestrator.submit(spec("manual", TaskLane.MANUAL));
        orchestrator.submit(spec("safety", TaskLane.SAFETY));

        orchestrator.tick();
        orchestrator.tick();
        orchestrator.tick();
        orchestrator.tick();

        assertEquals(Arrays.asList("safety", "manual", "chore", "fallback"), execution);
    }

    @Test
    public void ordinaryPreemptionWaitsForADeclaredSafePointAndThenResumes() {
        FakeClock clock = new FakeClock();
        SuspensionRunner fallback = new SuspensionRunner();
        TaskRunner chore = context -> StepResult
            .completed(context.getActionEpoch(), context.getCheckpoint(), "chore complete");
        Map<String, TaskRunner> runners = new LinkedHashMap<>();
        runners.put("fallback", fallback);
        runners.put("chore", chore);
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, checkpoint) -> runners.get(spec.getId()));

        orchestrator.submit(spec("fallback", TaskLane.FALLBACK));
        TaskSnapshot initiallyRunning = task(orchestrator.tick(), "fallback");
        long initialEpoch = initiallyRunning.getActionEpoch();

        orchestrator.submit(spec("chore", TaskLane.CHORE));
        ControllerSnapshot firstPreemptionTick = orchestrator.tick();
        assertEquals(TaskState.SUSPENDING, task(firstPreemptionTick, "fallback").getState());
        assertEquals(TaskState.QUEUED, task(firstPreemptionTick, "chore").getState());
        assertEquals(TaskSuspensionReason.PREEMPTION, fallback.requests.get(0));

        ControllerSnapshot safePointTick = orchestrator.tick();
        assertEquals(TaskState.SUSPENDED, task(safePointTick, "fallback").getState());
        assertEquals(TaskSuspensionReason.PREEMPTION, task(safePointTick, "fallback").getSuspensionReason());

        assertEquals(TaskState.COMPLETED, task(orchestrator.tick(), "chore").getState());
        TaskSnapshot resumed = task(orchestrator.tick(), "fallback");
        assertEquals(TaskState.COMPLETED, resumed.getState());
        assertTrue(resumed.getActionEpoch() > initialEpoch);
    }

    @Test
    public void safetySubmissionInterruptsSynchronouslyAndOldEpochResultsAreRejected() {
        FakeClock clock = new FakeClock();
        RecordingRunner fallback = new RecordingRunner(
            context -> StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "moving"));
        TaskRunner safety = context -> StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "safe");
        TaskOrchestrator orchestrator = newOrchestrator(
            clock,
            (spec, checkpoint) -> spec.getLane() == TaskLane.SAFETY ? safety : fallback);

        orchestrator.submit(spec("fallback", TaskLane.FALLBACK));
        long revokedEpoch = task(orchestrator.tick(), "fallback").getActionEpoch();

        orchestrator.submit(spec("safety", TaskLane.SAFETY));

        TaskSnapshot interrupted = task(orchestrator.snapshot(), "fallback");
        assertEquals(TaskState.SUSPENDED, interrupted.getState());
        assertEquals(TaskSuspensionReason.PREEMPTION, interrupted.getSuspensionReason());
        assertEquals(1, fallback.interruptions.size());
        assertEquals(
            TaskInterruptionReason.SAFETY_PREEMPTION,
            fallback.interruptions.get(0)
                .getReason());
        assertEquals(
            revokedEpoch,
            fallback.interruptions.get(0)
                .getRevokedEpoch());

        boolean accepted = orchestrator.acceptRunnerResult(
            "fallback",
            StepResult.completed(revokedEpoch, TaskCheckpoint.empty(), "late completion"));
        assertFalse(accepted);
        assertEquals(1L, task(orchestrator.snapshot(), "fallback").getRejectedStaleResults());

        assertEquals(TaskState.COMPLETED, task(orchestrator.tick(), "safety").getState());
        TaskSnapshot resumed = task(orchestrator.tick(), "fallback");
        assertEquals(TaskState.RUNNING, resumed.getState());
        assertTrue(resumed.getActionEpoch() > revokedEpoch);
    }

    @Test
    public void cancellingQueuedWorkDoesNotRevokeTheUnrelatedActiveTask() {
        FakeClock clock = new FakeClock();
        InMemoryActionBroker actionBroker = new InMemoryActionBroker();
        RecordingRunner active = new RecordingRunner(
            context -> StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "active"));
        RecordingRunner queued = new RecordingRunner(
            context -> StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "unexpected"));
        TaskOrchestrator orchestrator = new TaskOrchestrator(
            clock,
            (spec, checkpoint) -> spec.getId()
                .equals("active") ? active : queued,
            actionBroker);
        orchestrator.submit(spec("active", TaskLane.MANUAL));
        orchestrator.submit(spec("queued", TaskLane.FALLBACK));

        TaskSnapshot running = task(orchestrator.tick(), "active");
        long activeEpoch = running.getActionEpoch();
        assertEquals(activeEpoch, actionBroker.currentEpoch());

        assertEquals(
            TaskState.CANCELLED,
            orchestrator.cancel("queued")
                .getState());
        assertEquals(activeEpoch, actionBroker.currentEpoch());
        assertEquals(activeEpoch, task(orchestrator.snapshot(), "active").getActionEpoch());
        assertTrue(queued.interruptions.isEmpty());
        assertTrue(
            orchestrator.acceptRunnerResult(
                "active",
                StepResult.progress(activeEpoch, TaskCheckpoint.empty(), "still authoritative")));
    }

    @Test
    public void removesQueuedTasksButRefusesActiveWork() {
        FakeClock clock = new FakeClock();
        TaskOrchestrator orchestrator = newOrchestrator(
            clock,
            (spec, checkpoint) -> context -> StepResult
                .progress(context.getActionEpoch(), context.getCheckpoint(), "working"));
        orchestrator.submit(spec("active", TaskLane.MANUAL));
        orchestrator.submit(spec("queued", TaskLane.FALLBACK));
        orchestrator.tick();

        assertEquals(
            "queued",
            orchestrator.remove("queued")
                .getSpec()
                .getId());
        assertFalse(
            orchestrator.inspect("queued")
                .isPresent());
        try {
            orchestrator.remove("active");
            fail("expected active task deletion refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("must finish or be cancelled"));
        }

        assertTrue(
            orchestrator.inspect("active")
                .isPresent());
    }

    @Test
    public void externalActionRevocationBlocksTheTaskAndRejectsItsLateResult() {
        FakeClock clock = new FakeClock();
        InMemoryActionBroker actionBroker = new InMemoryActionBroker();
        RecordingRunner runner = new RecordingRunner(
            context -> StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "active"));
        TaskOrchestrator orchestrator = new TaskOrchestrator(clock, (spec, checkpoint) -> runner, actionBroker);
        orchestrator.submit(spec("manual", TaskLane.MANUAL));
        long revokedEpoch = task(orchestrator.tick(), "manual").getActionEpoch();

        actionBroker.revokeAll();

        TaskSnapshot blocked = task(orchestrator.snapshot(), "manual");
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            BlockedCause.EXTERNAL_FAILURE,
            blocked.getBlockedReason()
                .get()
                .getCause());
        assertEquals(
            actionBroker.currentEpoch(),
            orchestrator.snapshot()
                .getActionEpoch());
        assertEquals(
            actionBroker.currentEpoch(),
            orchestrator.snapshot()
                .getActionAuthority()
                .getEpoch());
        assertEquals(1, runner.interruptions.size());
        assertEquals(
            TaskInterruptionReason.ACTION_AUTHORITY_REVOCATION,
            runner.interruptions.get(0)
                .getReason());
        assertFalse(
            orchestrator.acceptRunnerResult(
                "manual",
                StepResult.completed(revokedEpoch, TaskCheckpoint.empty(), "late completion")));
        assertEquals(1L, task(orchestrator.snapshot(), "manual").getRejectedStaleResults());
    }

    @Test
    public void unresolvedSafetyFailureHoldsAllLowerLanes() {
        FakeClock clock = new FakeClock();
        AtomicInteger fallbackSteps = new AtomicInteger();
        TaskRunner fallback = context -> {
            fallbackSteps.incrementAndGet();
            return StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "fallback active");
        };
        TaskRunner safety = context -> StepResult
            .failed(context.getActionEpoch(), context.getCheckpoint(), "unsafe state unresolved", true);
        TaskOrchestrator orchestrator = newOrchestrator(
            clock,
            (spec, checkpoint) -> spec.getLane() == TaskLane.SAFETY ? safety : fallback);
        orchestrator.submit(spec("fallback", TaskLane.FALLBACK));
        orchestrator.tick();
        assertEquals(1, fallbackSteps.get());

        orchestrator.submit(spec("safety", TaskLane.SAFETY));
        assertEquals(TaskState.BLOCKED, task(orchestrator.tick(), "safety").getState());
        ControllerSnapshot held = orchestrator.tick();

        assertFalse(
            held.getActiveTaskId()
                .isPresent());
        assertEquals(TaskState.SUSPENDED, task(held, "fallback").getState());
        assertEquals(1, fallbackSteps.get());
    }

    @Test
    public void pauseResumeAndCancelUseSafeSuspensionPoints() {
        FakeClock clock = new FakeClock();
        RecordingRunner runner = new RecordingRunner(context -> {
            if (context.isSuspensionRequested()) {
                return StepResult.safeSuspension(context.getActionEpoch(), context.getCheckpoint(), "safe");
            }
            return StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "working");
        });
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, checkpoint) -> runner);

        orchestrator.submit(spec("manual", TaskLane.MANUAL));
        long firstEpoch = task(orchestrator.tick(), "manual").getActionEpoch();

        assertEquals(
            TaskState.SUSPENDING,
            orchestrator.pause("manual")
                .getState());
        TaskSnapshot paused = task(orchestrator.tick(), "manual");
        assertEquals(TaskState.SUSPENDED, paused.getState());
        assertEquals(TaskSuspensionReason.OPERATOR_PAUSE, paused.getSuspensionReason());

        assertEquals(
            TaskState.QUEUED,
            orchestrator.resume("manual")
                .getState());
        TaskSnapshot resumed = task(orchestrator.tick(), "manual");
        assertEquals(TaskState.RUNNING, resumed.getState());
        assertTrue(resumed.getActionEpoch() > firstEpoch);

        assertEquals(
            TaskState.SUSPENDING,
            orchestrator.cancel("manual")
                .getState());
        TaskSnapshot cancelled = task(orchestrator.tick(), "manual");
        assertEquals(TaskState.CANCELLED, cancelled.getState());
        assertEquals(-1, cancelled.getQueuePosition());
        assertEquals(1, runner.interruptions.size());
        assertEquals(
            TaskInterruptionReason.CANCELLATION,
            runner.interruptions.get(0)
                .getReason());
    }

    @Test
    public void reorderChangesSelectionWithinOneLaneAndSnapshotsAreImmutable() {
        FakeClock clock = new FakeClock();
        List<String> execution = new ArrayList<>();
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, checkpoint) -> context -> {
            execution.add(spec.getId());
            return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "done");
        });
        orchestrator.submit(spec("a", TaskLane.MANUAL));
        orchestrator.submit(spec("b", TaskLane.MANUAL));
        orchestrator.submit(spec("c", TaskLane.MANUAL));

        assertEquals(
            0,
            orchestrator.reorder("c", 0)
                .getQueuePosition());
        ControllerSnapshot beforeTick = orchestrator.snapshot();
        assertEquals(
            Arrays.asList("c", "a", "b"),
            ids(
                beforeTick.getQueue()
                    .getLane(TaskLane.MANUAL)));
        assertUnsupported(
            () -> beforeTick.getTasks()
                .clear());
        assertUnsupported(
            () -> beforeTick.getQueue()
                .getLane(TaskLane.MANUAL)
                .remove(0));
        assertUnsupported(
            () -> beforeTick.getQueue()
                .getLanes()
                .clear());

        orchestrator.tick();
        assertEquals(Collections.singletonList("c"), execution);
    }

    @Test
    public void retriesAfterOneFiveAndThirtySecondsThenBlocks() {
        FakeClock clock = new FakeClock();
        AtomicInteger creations = new AtomicInteger();
        AtomicInteger steps = new AtomicInteger();
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, checkpoint) -> {
            creations.incrementAndGet();
            return context -> {
                steps.incrementAndGet();
                return StepResult
                    .failed(context.getActionEpoch(), context.getCheckpoint(), "temporary backend failure", true);
            };
        });
        orchestrator.submit(spec("retry", TaskLane.MANUAL));

        TaskSnapshot first = task(orchestrator.tick(), "retry");
        assertRetry(first, 1, 1_000L);
        orchestrator.tick();
        assertEquals(1, steps.get());

        clock.advance(1_000L);
        TaskSnapshot second = task(orchestrator.tick(), "retry");
        assertRetry(second, 2, 6_000L);

        clock.advance(5_000L);
        TaskSnapshot third = task(orchestrator.tick(), "retry");
        assertRetry(third, 3, 36_000L);

        clock.advance(30_000L);
        TaskSnapshot exhausted = task(orchestrator.tick(), "retry");
        assertEquals(TaskState.BLOCKED, exhausted.getState());
        assertEquals(3, exhausted.getRetryCount());
        BlockedReason reason = exhausted.getBlockedReason()
            .orElse(null);
        assertNotNull(reason);
        assertEquals(BlockedCause.RETRY_EXHAUSTED, reason.getCause());
        assertEquals(3, reason.getRetryCount());
        assertEquals(4, steps.get());
        assertEquals(4, creations.get());
    }

    @Test
    public void safetyFailuresBlockWithoutRetrying() {
        FakeClock clock = new FakeClock();
        AtomicInteger creations = new AtomicInteger();
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, checkpoint) -> {
            creations.incrementAndGet();
            return context -> StepResult
                .failed(context.getActionEpoch(), context.getCheckpoint(), "unsafe recovery state", true);
        });

        orchestrator.submit(spec("death-safety", TaskLane.SAFETY));
        TaskSnapshot blocked = task(orchestrator.tick(), "death-safety");

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            BlockedCause.SAFETY_FAILURE,
            blocked.getBlockedReason()
                .get()
                .getCause());
        clock.advance(60_000L);
        orchestrator.tick();
        assertEquals(1, creations.get());
    }

    @Test
    public void waitDelaysStepsAndASafePointWithoutARequestDoesNotSuspend() {
        FakeClock clock = new FakeClock();
        AtomicInteger steps = new AtomicInteger();
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, checkpoint) -> context -> {
            int step = steps.incrementAndGet();
            if (step == 1) {
                return StepResult.waitFor(context.getActionEpoch(), context.getCheckpoint(), 500L, "waiting");
            }
            if (step == 2) {
                return StepResult.safeSuspension(context.getActionEpoch(), context.getCheckpoint(), "safe boundary");
            }
            return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "done");
        });
        orchestrator.submit(spec("wait", TaskLane.CHORE));

        assertEquals(TaskState.RUNNING, task(orchestrator.tick(), "wait").getState());
        clock.advance(499L);
        orchestrator.tick();
        assertEquals(1, steps.get());
        clock.advance(1L);
        assertEquals(TaskState.RUNNING, task(orchestrator.tick(), "wait").getState());
        assertEquals(TaskState.COMPLETED, task(orchestrator.tick(), "wait").getState());
    }

    @Test
    public void typedBlockingCanBeAcknowledgedAndResumedFromItsCheckpoint() {
        FakeClock clock = new FakeClock();
        AtomicInteger generations = new AtomicInteger();
        TaskCheckpoint checkpoint = new TaskCheckpoint(4L, singleton("phase", "unload"));
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, restored) -> {
            int generation = generations.incrementAndGet();
            if (generation == 1) {
                return context -> StepResult.blocked(
                    context.getActionEpoch(),
                    checkpoint,
                    BlockedReason.missingRequirement(
                        "drop-off chest is missing",
                        "base",
                        "named container DROP_OFF",
                        "Register a drop-off container."));
            }
            assertEquals(checkpoint, restored);
            return context -> StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "unloaded");
        });
        orchestrator.submit(spec("unload", TaskLane.CHORE));

        TaskSnapshot blocked = task(orchestrator.tick(), "unload");
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            "named container DROP_OFF",
            blocked.getBlockedReason()
                .get()
                .getMissingRequirement());

        assertEquals(
            TaskState.QUEUED,
            orchestrator.resume("unload")
                .getState());
        TaskSnapshot completed = task(orchestrator.tick(), "unload");
        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(checkpoint, completed.getCheckpoint());
    }

    @Test
    public void restorePassesPersistedCheckpointToRunnerFactory() {
        FakeClock clock = new FakeClock();
        TaskCheckpoint restored = new TaskCheckpoint(8L, singleton("frontier", "12,44"));
        TaskOrchestrator orchestrator = newOrchestrator(clock, (spec, checkpoint) -> {
            assertEquals(restored, checkpoint);
            return context -> StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "restored");
        });

        orchestrator.restore(spec("excavate", TaskLane.FALLBACK), restored);

        assertEquals(restored, task(orchestrator.tick(), "excavate").getCheckpoint());
    }

    @Test
    public void rejectsAClockThatMovesBackwards() {
        FakeClock clock = new FakeClock();
        TaskOrchestrator orchestrator = newOrchestrator(
            clock,
            (spec, checkpoint) -> context -> StepResult
                .progress(context.getActionEpoch(), context.getCheckpoint(), "working"));
        orchestrator.submit(spec("clock", TaskLane.MANUAL));
        clock.advance(10L);
        orchestrator.snapshot();
        clock.set(9L);

        try {
            orchestrator.snapshot();
            fail("expected backwards clock rejection");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("backwards"));
        }
    }

    private static TaskSpec spec(String id, TaskLane lane) {
        return TaskSpec.of(id, "test", id, lane);
    }

    private static TaskOrchestrator newOrchestrator(MonotonicClock clock, TaskRunnerFactory runnerFactory) {
        return new TaskOrchestrator(clock, runnerFactory, new InMemoryActionBroker());
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

    private static void assertRetry(TaskSnapshot snapshot, int retryCount, long nextEligibleAtMillis) {
        assertEquals(TaskState.QUEUED, snapshot.getState());
        assertEquals(retryCount, snapshot.getRetryCount());
        assertEquals(nextEligibleAtMillis, snapshot.getNextEligibleAtMillis());
    }

    private static Map<String, String> singleton(String key, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(key, value);
        return values;
    }

    private static void assertUnsupported(Runnable mutation) {
        try {
            mutation.run();
            fail("expected immutable collection");
        } catch (UnsupportedOperationException expected) {
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

        private void set(long millis) {
            now = millis;
        }
    }

    private static final class RecordingRunner implements TaskRunner {

        private final java.util.function.Function<TaskStepContext, StepResult> step;
        private final List<TaskInterruption> interruptions = new ArrayList<>();

        private RecordingRunner(java.util.function.Function<TaskStepContext, StepResult> step) {
            this.step = step;
        }

        @Override
        public StepResult step(TaskStepContext context) {
            return step.apply(context);
        }

        @Override
        public void interrupt(TaskInterruption interruption) {
            interruptions.add(interruption);
        }
    }

    private static final class SuspensionRunner implements TaskRunner {

        private final List<TaskSuspensionReason> requests = new ArrayList<>();
        private int ordinarySteps;

        @Override
        public StepResult step(TaskStepContext context) {
            if (context.isSuspensionRequested()) {
                requests.add(context.getSuspensionRequest());
                if (requests.size() == 1) {
                    return StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "not safe yet");
                }
                return StepResult.safeSuspension(context.getActionEpoch(), context.getCheckpoint(), "safe now");
            }
            ordinarySteps++;
            if (ordinarySteps == 1) {
                return StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "fallback running");
            }
            return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "fallback complete");
        }
    }
}
