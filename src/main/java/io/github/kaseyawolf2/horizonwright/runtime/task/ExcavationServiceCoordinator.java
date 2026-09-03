package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Locale;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

/** Composes exact excavation suspension checkpoints with durable unload or repair child tasks. */
public final class ExcavationServiceCoordinator {

    private static final String CHILD_PREFIX = "hw-service-";

    private final IHorizonwrightController controller;

    public ExcavationServiceCoordinator(IHorizonwrightController controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
    }

    /** Performs only bounded controller mutations and never directly invokes an action backend. */
    public int coordinate(ControllerSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        int mutations = 0;
        for (TaskSnapshot parent : snapshot.getTasks()) {
            if (!isBlockedExcavation(parent)) continue;
            ExcavationCheckpoint checkpoint = decode(parent);
            ExcavationSuspensionReason reason = checkpoint.getSuspensionReason();
            if (!isServiceReason(reason)) continue;
            ExcavationServicePolicy policy = ExcavationTask.servicePolicy(parent.getSpec());
            if (!supports(policy, reason)) continue;

            TaskSpec expectedChild = childSpec(parent, checkpoint, policy, reason);
            Optional<TaskSnapshot> existing = snapshot.findTask(expectedChild.getId());
            if (!existing.isPresent()) {
                controller.submit(expectedChild);
                mutations++;
                continue;
            }
            TaskSnapshot child = existing.get();
            if (!sameLinkedChild(child.getSpec(), expectedChild, parent, checkpoint, reason)) continue;
            if (child.getState() == TaskState.COMPLETED) {
                controller.resume(
                    parent.getSpec()
                        .getId());
                mutations++;
            }
        }
        return mutations;
    }

    static String childId(String parentTaskId, long checkpointRevision, ExcavationSuspensionReason reason) {
        if (parentTaskId == null || parentTaskId.trim()
            .isEmpty() || checkpointRevision < 1L || !isServiceReason(reason)) {
            throw new IllegalArgumentException("a service child requires an exact parent checkpoint");
        }
        return CHILD_PREFIX + parentTaskId.trim()
            + '-'
            + checkpointRevision
            + '-'
            + reason.name()
                .toLowerCase(Locale.ROOT);
    }

    private static TaskSpec childSpec(TaskSnapshot parent, ExcavationCheckpoint checkpoint,
        ExcavationServicePolicy policy, ExcavationSuspensionReason reason) {
        String parentId = parent.getSpec()
            .getId();
        long revision = checkpoint.getTaskRevision();
        String childId = childId(parentId, revision, reason);
        if (reason == ExcavationSuspensionReason.UNLOADING_REQUIRED) {
            return UnloadTask.createLinked(childId, policy.getLoadoutId(), policy.getStorageId(), parentId, revision);
        }
        return RepairTask.createLinked(
            childId,
            policy.getRepairStationId(),
            repairToolSlot(parent, policy),
            policy.getPredictedWorkDamage(),
            parentId,
            revision);
    }

    private static int repairToolSlot(TaskSnapshot parent, ExcavationServicePolicy policy) {
        if (!parent.getBlockedReason()
            .isPresent()) return policy.getReservedToolSlot();
        String location = parent.getBlockedReason()
            .get()
            .getLocation();
        String prefix = "repair-tool-slot:";
        if (!location.startsWith(prefix)) return policy.getReservedToolSlot();
        try {
            int slot = Integer.parseInt(location.substring(prefix.length()));
            if (slot < 0 || slot > 35) return policy.getReservedToolSlot();
            return slot;
        } catch (NumberFormatException invalid) {
            return policy.getReservedToolSlot();
        }
    }

    private static ExcavationCheckpoint decode(TaskSnapshot parent) {
        CylinderExcavationSpec spec = ExcavationTask.parse(parent.getSpec());
        ExcavationCheckpoint checkpoint = ExcavationTaskCheckpointCodec.decode(spec, parent.getCheckpoint());
        if (checkpoint == null) throw new IllegalArgumentException("blocked excavation has no checkpoint");
        return checkpoint;
    }

    private static boolean sameLinkedChild(TaskSpec actual, TaskSpec expected, TaskSnapshot parent,
        ExcavationCheckpoint checkpoint, ExcavationSuspensionReason reason) {
        return actual.getType()
            .equals(expected.getType())
            && actual.getParameters()
                .equals(expected.getParameters())
            && LinkedServiceTask.matches(
                actual,
                parent.getSpec()
                    .getId(),
                checkpoint.getTaskRevision(),
                reason);
    }

    private static boolean isBlockedExcavation(TaskSnapshot task) {
        return task.getState() == TaskState.BLOCKED && ExcavationTask.TYPE.equals(
            task.getSpec()
                .getType());
    }

    private static boolean supports(ExcavationServicePolicy policy, ExcavationSuspensionReason reason) {
        return policy != null
            && (reason == ExcavationSuspensionReason.UNLOADING_REQUIRED ? policy.hasUnload() : policy.hasRepair());
    }

    private static boolean isServiceReason(ExcavationSuspensionReason reason) {
        return reason == ExcavationSuspensionReason.UNLOADING_REQUIRED
            || reason == ExcavationSuspensionReason.REPAIR_REQUIRED;
    }
}
