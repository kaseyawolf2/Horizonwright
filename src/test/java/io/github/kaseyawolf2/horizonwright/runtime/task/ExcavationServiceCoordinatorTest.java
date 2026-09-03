package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunnerFactory;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class ExcavationServiceCoordinatorTest {

    private TaskOrchestrator controller;

    @After
    public void closeController() {
        if (controller != null) controller.close();
    }

    @Test
    public void completedUnloadChildResumesTheExactParentFrontier() {
        controller = controller(ChildOutcome.COMPLETED);
        TaskSpec parent = ExcavationTask.cleanVolumeCylinder(
            "quarry",
            0,
            8,
            8,
            1,
            12,
            12,
            ExcavationServicePolicy.unloadOnly("mining", "ore-chest"));
        controller.submit(parent);
        ControllerSnapshot blocked = tickUntil(parent.getId(), TaskState.BLOCKED);
        TaskCheckpoint suspended = task(blocked, parent.getId()).getCheckpoint();
        String frontier = frontierKey(suspended);
        ExcavationServiceCoordinator coordinator = new ExcavationServiceCoordinator(controller);

        assertEquals(1, coordinator.coordinate(blocked));
        String childId = ExcavationServiceCoordinator
            .childId(parent.getId(), suspended.getRevision(), ExcavationSuspensionReason.UNLOADING_REQUIRED);
        ControllerSnapshot childCompleted = tickUntil(childId, TaskState.COMPLETED);
        assertEquals(1, coordinator.coordinate(childCompleted));
        TaskSnapshot resumed = task(controller.snapshot(), parent.getId());
        assertEquals(TaskState.QUEUED, resumed.getState());

        ControllerSnapshot running = tickUntil(parent.getId(), TaskState.RUNNING);
        TaskSnapshot rebound = task(running, parent.getId());
        assertEquals(
            "active",
            rebound.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(frontier, frontierKey(rebound.getCheckpoint()));
    }

    @Test
    public void persistedCompletedChildResumesWithoutCreatingADuplicate() {
        controller = controller(ChildOutcome.COMPLETED);
        TaskSpec parent = ExcavationTask.cleanVolumeCylinder(
            "restart-parent",
            0,
            8,
            8,
            1,
            12,
            12,
            ExcavationServicePolicy.repairOnly("tool-forge", 4, 100));
        controller.submit(parent);
        ControllerSnapshot blocked = tickUntil(parent.getId(), TaskState.BLOCKED);
        ExcavationServiceCoordinator coordinator = new ExcavationServiceCoordinator(controller);
        coordinator.coordinate(blocked);
        TaskCheckpoint suspended = task(blocked, parent.getId()).getCheckpoint();
        String childId = ExcavationServiceCoordinator
            .childId(parent.getId(), suspended.getRevision(), ExcavationSuspensionReason.REPAIR_REQUIRED);
        tickUntil(childId, TaskState.COMPLETED);
        TaskControllerState saved = controller.exportState();
        controller.close();

        controller = controller(ChildOutcome.COMPLETED);
        ControllerSnapshot restored = controller.restoreState(saved);
        assertEquals(
            2,
            restored.getTasks()
                .size());
        assertEquals(1, new ExcavationServiceCoordinator(controller).coordinate(restored));
        assertEquals(
            2,
            controller.snapshot()
                .getTasks()
                .size());
        assertEquals(TaskState.QUEUED, task(controller.snapshot(), parent.getId()).getState());
    }

    @Test
    public void failedChildNeverResumesParent() {
        controller = controller(ChildOutcome.FAILED);
        TaskSpec parent = ExcavationTask.cleanVolumeCylinder(
            "failed-child",
            0,
            8,
            8,
            1,
            12,
            12,
            ExcavationServicePolicy.unloadOnly("mining", "ore-chest"));
        controller.submit(parent);
        ControllerSnapshot blocked = tickUntil(parent.getId(), TaskState.BLOCKED);
        ExcavationServiceCoordinator coordinator = new ExcavationServiceCoordinator(controller);
        coordinator.coordinate(blocked);
        String childId = ExcavationServiceCoordinator.childId(
            parent.getId(),
            task(blocked, parent.getId()).getCheckpoint()
                .getRevision(),
            ExcavationSuspensionReason.UNLOADING_REQUIRED);
        ControllerSnapshot failed = tickUntil(childId, TaskState.FAILED);

        assertEquals(0, coordinator.coordinate(failed));
        assertEquals(TaskState.BLOCKED, task(controller.snapshot(), parent.getId()).getState());
    }

    @Test
    public void legacyBlockedRepairChildIsResumedIntoLiveStationWaiting() {
        controller = controller(ChildOutcome.LEGACY_STATION_WAIT);
        TaskSpec parent = ExcavationTask.cleanVolumeCylinder(
            "legacy-repair-wait",
            0,
            8,
            8,
            1,
            12,
            12,
            ExcavationServicePolicy.repairOnly("tool-forge", 4, 100));
        controller.submit(parent);
        ControllerSnapshot blocked = tickUntil(parent.getId(), TaskState.BLOCKED);
        ExcavationServiceCoordinator coordinator = new ExcavationServiceCoordinator(controller);
        assertEquals(1, coordinator.coordinate(blocked));
        String childId = ExcavationServiceCoordinator.childId(
            parent.getId(),
            task(blocked, parent.getId()).getCheckpoint()
                .getRevision(),
            ExcavationSuspensionReason.REPAIR_REQUIRED);
        ControllerSnapshot childBlocked = tickUntil(childId, TaskState.BLOCKED);

        assertEquals(1, coordinator.coordinate(childBlocked));
        assertEquals(TaskState.QUEUED, task(controller.snapshot(), childId).getState());
        assertEquals(TaskState.BLOCKED, task(controller.snapshot(), parent.getId()).getState());
    }

    @Test
    public void repairChildUsesTheDamagedToolSlotRecordedByTheParent() {
        controller = controller(ChildOutcome.COMPLETED);
        TaskSpec parent = ExcavationTask.cleanVolumeCylinder(
            "dynamic-repair-slot",
            0,
            8,
            8,
            1,
            12,
            12,
            ExcavationServicePolicy.repairOnly("tool-forge", 0, 100));
        controller.submit(parent);
        ControllerSnapshot blocked = tickUntil(parent.getId(), TaskState.BLOCKED);

        assertEquals(1, new ExcavationServiceCoordinator(controller).coordinate(blocked));
        String childId = ExcavationServiceCoordinator.childId(
            parent.getId(),
            task(blocked, parent.getId()).getCheckpoint()
                .getRevision(),
            ExcavationSuspensionReason.REPAIR_REQUIRED);
        assertEquals(
            "7",
            task(controller.snapshot(), childId).getSpec()
                .getParameters()
                .get(RepairTask.RESERVED_INVENTORY_SLOT));
    }

    @Test
    public void incompleteOrInvalidServicePoliciesAreRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ExcavationServicePolicy.unloadAndRepair("mining", "ore-chest", "forge", -1, 100));
        assertThrows(IllegalArgumentException.class, () -> ExcavationServicePolicy.repairOnly("forge", 36, 100));
        TaskSpec ordinary = ExcavationTask.cleanVolumeCylinder("ordinary", 0, 0, 0, 1, 10, 10);
        assertFalse(
            ordinary.getParameters()
                .containsKey(ExcavationTask.LOADOUT_ID));
    }

    @Test
    public void collidingButUnlinkedChildIdCannotResumeParent() {
        controller = controller(ChildOutcome.COMPLETED);
        TaskSpec parent = ExcavationTask.cleanVolumeCylinder(
            "collision-parent",
            0,
            8,
            8,
            1,
            12,
            12,
            ExcavationServicePolicy.unloadOnly("mining", "ore-chest"));
        controller.submit(parent);
        ControllerSnapshot blocked = tickUntil(parent.getId(), TaskState.BLOCKED);
        String childId = ExcavationServiceCoordinator.childId(
            parent.getId(),
            task(blocked, parent.getId()).getCheckpoint()
                .getRevision(),
            ExcavationSuspensionReason.UNLOADING_REQUIRED);
        controller.submit(UnloadTask.create(childId, "other-loadout", "other-chest"));
        tickUntil(childId, TaskState.COMPLETED);

        assertEquals(0, new ExcavationServiceCoordinator(controller).coordinate(controller.snapshot()));
        assertEquals(TaskState.BLOCKED, task(controller.snapshot(), parent.getId()).getState());
    }

    private TaskOrchestrator controller(ChildOutcome childOutcome) {
        return new TaskOrchestrator(
            new FixedClock(),
            new CoordinatedRunnerFactory(childOutcome),
            new InMemoryActionBroker());
    }

    private ControllerSnapshot tickUntil(String taskId, TaskState state) {
        for (int i = 0; i < 12; i++) {
            ControllerSnapshot snapshot = controller.tick();
            if (snapshot.findTask(taskId)
                .isPresent() && task(snapshot, taskId).getState() == state) return snapshot;
        }
        TaskSnapshot last = controller.snapshot()
            .findTask(taskId)
            .orElse(null);
        throw new AssertionError(
            "task did not reach " + state + "; last=" + (last == null ? "missing" : last.getState()));
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String taskId) {
        TaskSnapshot task = snapshot.findTask(taskId)
            .orElse(null);
        assertNotNull("missing task " + taskId, task);
        return task;
    }

    private static String frontierKey(TaskCheckpoint checkpoint) {
        return checkpoint.getValues()
            .get("frontier.layerY") + ":"
            + checkpoint.getValues()
                .get("frontier.chunkX")
            + ":"
            + checkpoint.getValues()
                .get("frontier.chunkZ")
            + ":"
            + checkpoint.getValues()
                .get("frontier.band")
            + ":"
            + checkpoint.getValues()
                .get("frontier.offset");
    }

    private enum ChildOutcome {
        COMPLETED,
        FAILED,
        LEGACY_STATION_WAIT
    }

    private static final class CoordinatedRunnerFactory implements TaskRunnerFactory {

        private final ChildOutcome childOutcome;

        private CoordinatedRunnerFactory(ChildOutcome childOutcome) {
            this.childOutcome = childOutcome;
        }

        @Override
        public TaskRunner create(TaskSpec spec, TaskCheckpoint checkpoint) {
            if (ExcavationTask.TYPE.equals(spec.getType())) return excavationRunner(spec, checkpoint);
            return context -> {
                if (childOutcome == ChildOutcome.COMPLETED) {
                    return StepResult.completed(context.getActionEpoch(), context.getCheckpoint(), "service verified");
                }
                if (childOutcome == ChildOutcome.LEGACY_STATION_WAIT) {
                    return StepResult.blocked(
                        context.getActionEpoch(),
                        context.getCheckpoint(),
                        BlockedReason.missingRequirement(
                            "Open the exact pinned Tool Station or Tool Forge",
                            "tool-forge",
                            "the pinned, compatible Tinkers repair station",
                            "Open the configured Tool Station or Tool Forge, then resume this task."));
                }
                return StepResult.failed(context.getActionEpoch(), context.getCheckpoint(), "service failed", false);
            };
        }

        private static TaskRunner excavationRunner(TaskSpec spec, TaskCheckpoint createdCheckpoint) {
            return context -> {
                CylinderExcavationSpec cylinder = ExcavationTask.parse(spec);
                ExcavationCheckpoint current = ExcavationTaskCheckpointCodec.decode(cylinder, createdCheckpoint);
                if (current == null) {
                    ExcavationCheckpoint start = ExcavationCheckpoint.start(cylinder, 1L, context.getActionEpoch());
                    ExcavationSuspensionReason reason = ExcavationTask.servicePolicy(spec)
                        .hasRepair() ? ExcavationSuspensionReason.REPAIR_REQUIRED
                            : ExcavationSuspensionReason.UNLOADING_REQUIRED;
                    ExcavationCheckpoint suspended = ExcavationCheckpoint.restore(
                        cylinder,
                        start.getTaskRevision(),
                        start.getActionEpoch(),
                        start.getFrontier(),
                        start.getProgress(),
                        reason);
                    String location = "dynamic-repair-slot".equals(spec.getId()) ? "repair-tool-slot:7" : "frontier";
                    return StepResult.blocked(
                        context.getActionEpoch(),
                        ExcavationTaskCheckpointCodec.encode(cylinder, suspended),
                        BlockedReason.missingRequirement("service required", location, reason.name(), "wait"));
                }
                ExcavationCheckpoint resumed = ExcavationCheckpoint.restore(
                    cylinder,
                    current.getTaskRevision() + 1L,
                    context.getActionEpoch(),
                    current.getFrontier(),
                    current.getProgress(),
                    ExcavationSuspensionReason.NONE);
                return StepResult.progress(
                    context.getActionEpoch(),
                    ExcavationTaskCheckpointCodec.encode(cylinder, resumed),
                    "exact frontier resumed");
            };
        }
    }

    private static final class FixedClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 100L;
        }
    }
}
