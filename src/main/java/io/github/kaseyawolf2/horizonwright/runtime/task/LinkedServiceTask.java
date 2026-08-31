package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Self-authenticating parent-checkpoint linkage carried by generated service tasks. */
final class LinkedServiceTask {

    static final String PARENT_TASK_ID = "service.parentTaskId";
    static final String PARENT_CHECKPOINT_REVISION = "service.parentCheckpointRevision";
    static final String SUSPENSION_REASON = "service.suspensionReason";

    private LinkedServiceTask() {}

    static void write(Map<String, String> parameters, String parentTaskId, long parentCheckpointRevision,
        ExcavationSuspensionReason reason) {
        if (parentTaskId == null || parentTaskId.trim()
            .isEmpty()
            || parentCheckpointRevision < 1L
            || reason == null
            || reason == ExcavationSuspensionReason.NONE) {
            throw new IllegalArgumentException("an exact parent checkpoint link is required");
        }
        parameters.put(PARENT_TASK_ID, parentTaskId.trim());
        parameters.put(PARENT_CHECKPOINT_REVISION, Long.toString(parentCheckpointRevision));
        parameters.put(SUSPENSION_REASON, reason.name());
    }

    static boolean matches(TaskSpec child, String parentTaskId, long parentCheckpointRevision,
        ExcavationSuspensionReason reason) {
        if (child == null || parentTaskId == null || reason == null) return false;
        Map<String, String> parameters = child.getParameters();
        return parentTaskId.equals(parameters.get(PARENT_TASK_ID)) && Long.toString(parentCheckpointRevision)
            .equals(parameters.get(PARENT_CHECKPOINT_REVISION))
            && reason.name()
                .equals(parameters.get(SUSPENSION_REASON));
    }
}
