package io.github.kaseyawolf2.horizonwright.core.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;

public class TaskOrchestratorConcurrencyTest {

    @Test
    public void runnerConstructionDoesNotHoldTheControllerMonitor() throws Exception {
        CountDownLatch constructionStarted = new CountDownLatch(1);
        CountDownLatch releaseConstruction = new CountDownLatch(1);
        AtomicReference<TaskOrchestrator> controller = new AtomicReference<>();
        AtomicBoolean factoryHeldMonitor = new AtomicBoolean();
        TaskRunnerFactory factory = (spec, checkpoint) -> {
            factoryHeldMonitor.set(Thread.holdsLock(controller.get()));
            constructionStarted.countDown();
            await(releaseConstruction);
            return context -> StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "constructed");
        };
        TaskOrchestrator orchestrator = new TaskOrchestrator(
            new ConcurrentClock(),
            factory,
            new InMemoryActionBroker());
        controller.set(orchestrator);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<TaskSnapshot> submission = workers
                .submit(() -> orchestrator.submit(spec("building", TaskLane.MANUAL)));
            assertTrue(constructionStarted.await(1L, TimeUnit.SECONDS));

            Future<TaskSnapshot> cancellation = workers.submit(() -> orchestrator.cancel("building"));
            assertEquals(
                TaskState.CANCELLED,
                cancellation.get(1L, TimeUnit.SECONDS)
                    .getState());
            assertFalse(factoryHeldMonitor.get());

            releaseConstruction.countDown();
            assertEquals(
                TaskState.CANCELLED,
                submission.get(1L, TimeUnit.SECONDS)
                    .getState());
        } finally {
            releaseConstruction.countDown();
            workers.shutdownNow();
            orchestrator.close();
        }
    }

    @Test
    public void safetySubmissionInterruptsAConcurrentStepWithoutWaitingForIt() throws Exception {
        CountDownLatch stepStarted = new CountDownLatch(1);
        CountDownLatch releaseStep = new CountDownLatch(1);
        AtomicReference<TaskOrchestrator> controller = new AtomicReference<>();
        AtomicBoolean stepHeldMonitor = new AtomicBoolean();
        AtomicBoolean interruptionHeldMonitor = new AtomicBoolean();
        TaskRunner fallback = new TaskRunner() {

            @Override
            public StepResult step(TaskStepContext context) {
                stepHeldMonitor.set(Thread.holdsLock(controller.get()));
                stepStarted.countDown();
                await(releaseStep);
                return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "late completion");
            }

            @Override
            public void interrupt(TaskInterruption interruption) {
                interruptionHeldMonitor.set(Thread.holdsLock(controller.get()));
                releaseStep.countDown();
            }
        };
        TaskRunner safety = context -> StepResult
            .completed(context.getActionEpoch(), context.getCheckpoint(), "safety complete");
        TaskOrchestrator orchestrator = new TaskOrchestrator(
            new ConcurrentClock(),
            (spec, checkpoint) -> spec.getLane() == TaskLane.SAFETY ? safety : fallback,
            new InMemoryActionBroker());
        controller.set(orchestrator);
        orchestrator.submit(spec("fallback", TaskLane.FALLBACK));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ControllerSnapshot> runningTick = workers.submit(() -> orchestrator.tick());
            assertTrue(stepStarted.await(1L, TimeUnit.SECONDS));

            Future<TaskSnapshot> safetySubmission = workers
                .submit(() -> orchestrator.submit(spec("safety", TaskLane.SAFETY)));
            assertEquals(
                TaskState.QUEUED,
                safetySubmission.get(1L, TimeUnit.SECONDS)
                    .getState());
            runningTick.get(1L, TimeUnit.SECONDS);

            TaskSnapshot interrupted = orchestrator.inspect("fallback")
                .get();
            assertEquals(TaskState.SUSPENDED, interrupted.getState());
            assertEquals(1L, interrupted.getRejectedStaleResults());
            assertFalse(stepHeldMonitor.get());
            assertFalse(interruptionHeldMonitor.get());
            assertEquals(TaskState.COMPLETED, task(orchestrator.tick(), "safety").getState());
        } finally {
            releaseStep.countDown();
            workers.shutdownNow();
            orchestrator.close();
        }
    }

    @Test
    public void externalSafetyLockdownInterruptsAConcurrentStepAndRejectsItsResult() throws Exception {
        CountDownLatch stepStarted = new CountDownLatch(1);
        CountDownLatch releaseStep = new CountDownLatch(1);
        AtomicReference<TaskOrchestrator> controller = new AtomicReference<>();
        AtomicBoolean interruptionHeldMonitor = new AtomicBoolean();
        TaskRunner runner = new TaskRunner() {

            @Override
            public StepResult step(TaskStepContext context) {
                stepStarted.countDown();
                await(releaseStep);
                return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "late completion");
            }

            @Override
            public void interrupt(TaskInterruption interruption) {
                interruptionHeldMonitor.set(Thread.holdsLock(controller.get()));
                assertFalse(
                    controller.get()
                        .snapshot()
                        .getActiveTaskId()
                        .isPresent());
                releaseStep.countDown();
            }
        };
        InMemoryActionBroker actionBroker = new InMemoryActionBroker();
        TaskOrchestrator orchestrator = new TaskOrchestrator(
            new ConcurrentClock(),
            (spec, checkpoint) -> runner,
            actionBroker);
        controller.set(orchestrator);
        orchestrator.submit(spec("emergency", TaskLane.MANUAL));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ControllerSnapshot> runningTick = workers.submit(() -> orchestrator.tick());
            assertTrue(stepStarted.await(1L, TimeUnit.SECONDS));

            Future<?> emergency = workers.submit(actionBroker::enterSafetyLockdown);
            emergency.get(1L, TimeUnit.SECONDS);
            runningTick.get(1L, TimeUnit.SECONDS);

            TaskSnapshot blocked = orchestrator.inspect("emergency")
                .get();
            assertEquals(TaskState.BLOCKED, blocked.getState());
            assertEquals(1L, blocked.getRejectedStaleResults());
            assertFalse(interruptionHeldMonitor.get());
            assertTrue(actionBroker.isSafetyLocked());
        } finally {
            releaseStep.countDown();
            workers.shutdownNow();
            orchestrator.close();
        }
    }

    @Test
    public void reentrantPauseInvalidatesTheInFlightStepResult() {
        AtomicReference<TaskOrchestrator> controller = new AtomicReference<>();
        AtomicBoolean stepHeldMonitor = new AtomicBoolean();
        TaskRunner runner = context -> {
            stepHeldMonitor.set(Thread.holdsLock(controller.get()));
            if (context.isSuspensionRequested()) {
                return StepResult.safeSuspension(context.getActionEpoch(), context.getCheckpoint(), "paused safely");
            }
            controller.get()
                .pause(
                    context.getSpec()
                        .getId());
            return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "must be rejected");
        };
        TaskOrchestrator orchestrator = new TaskOrchestrator(
            new ConcurrentClock(),
            (spec, checkpoint) -> runner,
            new InMemoryActionBroker());
        controller.set(orchestrator);
        try {
            orchestrator.submit(spec("reentrant", TaskLane.MANUAL));

            TaskSnapshot suspending = task(orchestrator.tick(), "reentrant");
            assertEquals(TaskState.SUSPENDING, suspending.getState());
            assertEquals(1L, suspending.getRejectedStaleResults());
            assertFalse(stepHeldMonitor.get());
            assertEquals(TaskState.SUSPENDED, task(orchestrator.tick(), "reentrant").getState());
        } finally {
            orchestrator.close();
        }
    }

    @Test
    public void taskActionGatewayIsTypedTaskBoundAndRevokedWithItsEpoch() {
        InMemoryActionBroker actionBroker = new InMemoryActionBroker();
        AtomicReference<TaskActionGateway> gateway = new AtomicReference<>();
        AtomicReference<ActionLease> lease = new AtomicReference<>();
        TaskRunner runner = context -> {
            gateway.set(context.getActions());
            Optional<ActionLease> acquired = context.getActions()
                .tryAcquire(EnumSet.of(ActionCapability.MOVEMENT));
            assertTrue(acquired.isPresent());
            lease.set(acquired.get());
            return StepResult.progress(context.getActionEpoch(), context.getCheckpoint(), "lease acquired");
        };
        TaskOrchestrator orchestrator = new TaskOrchestrator(
            new ConcurrentClock(),
            (spec, checkpoint) -> runner,
            actionBroker);
        try {
            orchestrator.submit(spec("gateway", TaskLane.MANUAL));
            orchestrator.tick();

            assertNotNull(gateway.get());
            assertEquals(
                "gateway",
                gateway.get()
                    .getTaskId());
            assertEquals(
                actionBroker.currentEpoch(),
                gateway.get()
                    .getActionEpoch());
            assertTrue(
                gateway.get()
                    .isAuthoritative());
            assertEquals(
                "task:gateway",
                lease.get()
                    .getOwner());
            assertTrue(
                lease.get()
                    .isValid());

            actionBroker.revokeAll();

            assertFalse(
                lease.get()
                    .isValid());
            assertFalse(
                gateway.get()
                    .isAuthoritative());
            assertFalse(
                gateway.get()
                    .tryAcquire(EnumSet.of(ActionCapability.MOVEMENT))
                    .isPresent());
            assertEquals(
                TaskState.BLOCKED,
                orchestrator.inspect("gateway")
                    .get()
                    .getState());
        } finally {
            orchestrator.close();
        }
    }

    private static TaskSpec spec(String id, TaskLane lane) {
        return TaskSpec.of(id, "test", id, lane);
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String taskId) {
        return snapshot.findTask(taskId)
            .orElseThrow(() -> new AssertionError("missing task " + taskId));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2L, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for deterministic test latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new AssertionError("interrupted while waiting for deterministic test latch", interrupted);
        }
    }

    private static final class ConcurrentClock implements MonotonicClock {

        private final AtomicLong now = new AtomicLong();

        @Override
        public long nowMillis() {
            return now.get();
        }
    }
}
