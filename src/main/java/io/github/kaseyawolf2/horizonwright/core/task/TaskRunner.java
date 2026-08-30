package io.github.kaseyawolf2.horizonwright.core.task;

/**
 * A resumable state machine that advances by one bounded transition per call.
 *
 * <p>
 * {@link #step(TaskStepContext)} and {@link #interrupt(TaskInterruption)} may overlap. Implementations
 * must make interruption thread-safe and bounded. Neither callback runs while the controller monitor
 * is held.
 */
public interface TaskRunner {

    StepResult step(TaskStepContext context);

    /** Called synchronously only when normal safe suspension is intentionally bypassed. */
    default void interrupt(TaskInterruption interruption) {}
}
