package io.github.kaseyawolf2.horizonwright.core.task;

/** Why a runner is being asked to reach, or has reached, a safe suspension point. */
public enum TaskSuspensionReason {
    NONE,
    OPERATOR_PAUSE,
    PREEMPTION,
    CANCELLATION
}
