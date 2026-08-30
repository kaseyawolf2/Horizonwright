package io.github.kaseyawolf2.horizonwright.runtime.persistence;

import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;

/**
 * Narrow persistence callback owned by the runtime composition root.
 *
 * <p>
 * Restoring through this boundary lets the runtime restore controller state and reseed any
 * runtime-owned identifiers derived from that state in one operation.
 */
@FunctionalInterface
public interface RuntimeStateRestoreBoundary {

    void restoreControllerState(TaskControllerState state);
}
