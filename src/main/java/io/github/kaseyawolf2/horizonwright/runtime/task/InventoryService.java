package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

/**
 * Shared preparation of usable player inventory from portable storage. Observation must never mutate gameplay.
 * Each active operation belongs to the context's task and action epoch; implementations acquire their own leases.
 */
public interface InventoryService {

    /** Read-only decision. Completing unload means its destination container has already closed. */
    boolean needsPreparation(TaskStepContext context, boolean completingUnload);

    /**
     * Advances one bounded preparation step. Null means preparation and all cursor/container/session cleanup are
     * complete. The wrapper persists an uncertain-operation marker before calling this method for the first time.
     * On completion of unload, preparation drains a bag into available player slots for another unload batch.
     * When suspension is requested this method must stop starting actions and release its owned resources.
     */
    StepResult prepare(TaskStepContext context, boolean completingUnload);

    /** Cancels only an operation owned by this task, without starting replacement inventory actions. */
    void interrupt(String taskId, TaskInterruption interruption);

    /**
     * Read-only reconciliation after a restart or interruption. Returning null certifies an empty cursor, closed
     * owned containers, no outstanding clicks, and fresh inventory observation. Never replays a previous click.
     */
    default StepResult reconcileInterruptedPreparation(TaskStepContext context, boolean completingUnload) {
        return StepResult.blocked(
            context.getActionEpoch(),
            context.getCheckpoint(),
            BlockedReason.missingRequirement(
                "Portable inventory preparation was interrupted before its result was recorded.",
                context.getSpec()
                    .getId(),
                "verified portable inventory reconciliation",
                "Finish any carried-item transfer, close the inventory, and resume after checking its contents."));
    }
}
