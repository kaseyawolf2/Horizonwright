package io.github.kaseyawolf2.horizonwright.core.task;

/** Reconstructs runners from immutable specifications and persisted checkpoints. */
public interface TaskRunnerFactory {

    TaskRunner create(TaskSpec spec, TaskCheckpoint checkpoint);
}
