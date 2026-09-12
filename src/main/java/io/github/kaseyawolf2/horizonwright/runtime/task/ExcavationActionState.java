package io.github.kaseyawolf2.horizonwright.runtime.task;

public enum ExcavationActionState {
    SUBMITTED,
    EXECUTING,
    PENDING_CONFIRMATION,
    RETRY_REQUIRED,
    CONFIRMED,
    CANCELLED,
    FAILED
}
