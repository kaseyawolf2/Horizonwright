package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

public class InventoryPreparingTaskRunnerTest {

    @Test
    public void persistsUncertaintyBeforeAnyInventoryActionAndKeepsDelegateCheckpointSeparate() {
        try (Harness h = new Harness(false)) {
            TaskSnapshot pending = h.tick();
            assertTrue(InventoryPreparingTaskRunner.isWrapped(pending.getCheckpoint()));
            assertEquals(
                "true",
                pending.getCheckpoint()
                    .getValues()
                    .get("horizonwright.inventory.preparing"));
            assertEquals(0, h.service.preparations);
            assertEquals(0, h.delegateSteps);
            assertEquals(TaskCheckpoint.empty(), InventoryPreparingTaskRunner.unwrap(pending.getCheckpoint()));
            h.tick();
            assertEquals(1, h.service.preparations);
            assertEquals(0, h.delegateSteps);
            TaskSnapshot done = h.tick();
            assertEquals(TaskState.COMPLETED, done.getState());
            assertEquals(1, h.delegateSteps);
            assertEquals(TaskCheckpoint.empty(), h.observedDelegateCheckpoints.get(0));
            assertTrue(
                done.getCheckpoint()
                    .getRevision()
                    > pending.getCheckpoint()
                        .getRevision());
        }
    }

    @Test
    public void neverPreparesBetweenActionSubmissionAndItsVerification() {
        try (Harness h = new Harness(false)) {
            h.safe = false;
            h.tick();
            assertEquals(1, h.delegateSteps);
            assertEquals(0, h.service.observations);
            assertEquals(0, h.service.preparations);
            h.safe = true;
            h.tick();
            assertEquals(1, h.delegateSteps);
            assertEquals(0, h.service.preparations);
            h.tick();
            assertEquals(1, h.service.preparations);
        }
    }

    @Test
    public void restartRequiresReadOnlyReconciliationAndNeverReplaysPendingPreparation() {
        TaskCheckpoint persisted;
        try (Harness original = new Harness(false)) {
            persisted = original.tick()
                .getCheckpoint();
        }
        try (Harness restored = new Harness(false, persisted)) {
            TaskSnapshot blocked = restored.tick();
            assertEquals(TaskState.BLOCKED, blocked.getState());
            assertEquals(1, restored.service.reconciliations);
            assertEquals(0, restored.service.preparations);
            assertEquals(0, restored.delegateSteps);
            assertEquals(
                "true",
                blocked.getCheckpoint()
                    .getValues()
                    .get("horizonwright.inventory.preparing"));
        }
    }

    @Test
    public void verifiedReconciliationCanResumeWithoutRepeatingAnInventoryClick() {
        TaskCheckpoint persisted;
        try (Harness original = new Harness(false)) {
            persisted = original.tick()
                .getCheckpoint();
        }
        try (Harness restored = new Harness(false, persisted)) {
            restored.service.canReconcile = true;
            restored.tick();
            assertEquals(1, restored.service.reconciliations);
            assertEquals(0, restored.service.preparations);
            assertEquals(
                TaskState.COMPLETED,
                restored.tick()
                    .getState());
            assertEquals(1, restored.delegateSteps);
        }
    }

    @Test
    public void interruptCancelsOnlyTheActiveTasksPreparationAndRetainsJournal() {
        try (Harness h = new Harness(false)) {
            h.tick();
            h.service.holdPreparation = true;
            h.tick();
            h.broker.revokeAll();
            assertEquals(1, h.service.interruptions);
            assertEquals(h.spec.getId(), h.service.interruptedTask);
            assertEquals(1, h.service.preparations);
        }
    }

    @Test
    public void dryRunNeverStartsOrEvenPlansInventoryMutation() {
        try (Harness h = new Harness(false)) {
            h.dryRun = true;
            TaskSnapshot done = h.tick();
            assertEquals(TaskState.COMPLETED, done.getState());
            assertEquals(0, h.service.observations);
            assertEquals(0, h.service.preparations);
            assertFalse(InventoryPreparingTaskRunner.isWrapped(done.getCheckpoint()));
        }
    }

    @Test
    public void unchangedInventoryWaitReusesItsDurableCheckpoint() {
        try (Harness h = new Harness(false)) {
            TaskCheckpoint pending = h.tick()
                .getCheckpoint();
            h.service.holdPreparation = true;
            assertEquals(
                pending,
                h.tick()
                    .getCheckpoint());
            assertEquals(
                pending,
                h.tick()
                    .getCheckpoint());
        }
    }

    @Test
    public void interruptionInvalidatesIdleServicePreparationCaches() {
        try (Harness h = new Harness(false)) {
            h.service.needed = false;
            h.safe = false;
            h.tick();
            h.broker.revokeAll();
            assertEquals(1, h.service.interruptions);
            assertEquals(0, h.service.preparations);
        }
    }

    @Test
    public void unloadingRepeatsClosedStorageAndBagDrainUntilAllBatchesAreDelivered() {
        try (Harness h = new Harness(true)) {
            h.service.bagBatches = 2;
            long revision = -1L;
            TaskSnapshot state = null;
            for (int tick = 0; tick < 15; tick++) {
                state = h.tick();
                assertTrue(
                    state.getCheckpoint()
                        .getRevision() >= revision);
                revision = state.getCheckpoint()
                    .getRevision();
                if (state.getState() == TaskState.COMPLETED) break;
            }
            assertEquals(TaskState.COMPLETED, state.getState());
            assertEquals(3, h.delegateSteps);
            assertEquals(2, h.service.preparations);
            assertEquals(3, h.delegateBuilds);
            assertEquals(0, h.service.bagBatches);
            for (TaskCheckpoint checkpoint : h.observedDelegateCheckpoints)
                assertEquals(TaskCheckpoint.empty(), checkpoint);
        }
    }

    @Test
    public void restartAfterBagDrainStillRunsTheNextUnloadBatch() {
        TaskCheckpoint persisted;
        try (Harness h = new Harness(true)) {
            h.service.bagBatches = 1;
            h.tick();
            persisted = h.tick()
                .getCheckpoint();
            assertEquals(
                "false",
                persisted.getValues()
                    .get("horizonwright.inventory.preparing"));
            assertEquals(1, h.service.preparations);
        }
        try (Harness restored = new Harness(true, persisted)) {
            assertEquals(
                TaskState.COMPLETED,
                restored.tick()
                    .getState());
            assertEquals(1, restored.delegateSteps);
            assertEquals(0, restored.service.preparations);
        }
    }

    private static final class Harness implements AutoCloseable {

        final TaskSpec spec;
        final Service service = new Service();
        final InMemoryActionBroker broker = new InMemoryActionBroker();
        final TaskOrchestrator controller;
        final List<TaskCheckpoint> observedDelegateCheckpoints = new ArrayList<>();
        int delegateSteps;
        int delegateBuilds;
        boolean dryRun;
        boolean safe = true;

        Harness(boolean unload) {
            this(unload, TaskCheckpoint.empty());
        }

        Harness(boolean unload, TaskCheckpoint checkpoint) {
            spec = unload ? UnloadTask.create("inventory-task", "loadout", "chest")
                : TaskSpec.of("inventory-task", "test-work", "Test inventory preparation", TaskLane.MANUAL);
            controller = new TaskOrchestrator(
                () -> 1L,
                (task, restored) -> new InventoryPreparingTaskRunner(
                    task,
                    restored,
                    () -> service,
                    () -> dryRun,
                    (innerSpec, innerCheckpoint) -> {
                        delegateBuilds++;
                        return new TaskRunner() {

                            @Override
                            public boolean isInventoryPreparationSafe() {
                                return safe;
                            }

                            @Override
                            public StepResult step(TaskStepContext context) {
                                delegateSteps++;
                                observedDelegateCheckpoints.add(context.getCheckpoint());
                                assertEquals(innerCheckpoint, context.getCheckpoint());
                                if (!safe) return StepResult.waitFor(
                                    context.getActionEpoch(),
                                    innerCheckpoint,
                                    0L,
                                    "awaiting action verification");
                                return StepResult
                                    .completed(context.getActionEpoch(), innerCheckpoint, "task completed");
                            }
                        };
                    }),
                broker);
            controller.restore(spec, checkpoint);
        }

        TaskSnapshot tick() {
            return controller.tick()
                .findTask(spec.getId())
                .orElseThrow(AssertionError::new);
        }

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class Service implements InventoryService {

        int observations;
        int preparations;
        int reconciliations;
        int interruptions;
        int bagBatches;
        String interruptedTask;
        boolean needed = true;
        boolean canReconcile;
        boolean holdPreparation;

        @Override
        public boolean needsPreparation(TaskStepContext context, boolean completingUnload) {
            observations++;
            return UnloadTask.TYPE.equals(
                context.getSpec()
                    .getType()) ? completingUnload && bagBatches > 0 : needed;
        }

        @Override
        public StepResult prepare(TaskStepContext context, boolean completingUnload) {
            preparations++;
            assertEquals(TaskCheckpoint.empty(), context.getCheckpoint());
            if (holdPreparation) return StepResult
                .waitFor(context.getActionEpoch(), context.getCheckpoint(), 0L, "awaiting inventory confirmation");
            if (completingUnload) bagBatches--;
            else needed = false;
            return null;
        }

        @Override
        public void interrupt(String taskId, TaskInterruption interruption) {
            interruptions++;
            interruptedTask = taskId;
        }

        @Override
        public StepResult reconcileInterruptedPreparation(TaskStepContext context, boolean completingUnload) {
            reconciliations++;
            if (!canReconcile) return InventoryService.super.reconcileInterruptedPreparation(context, completingUnload);
            needed = false;
            return null;
        }
    }
}
