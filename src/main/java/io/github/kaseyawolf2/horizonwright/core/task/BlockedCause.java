package io.github.kaseyawolf2.horizonwright.core.task;

public enum BlockedCause {
    RETRY_EXHAUSTED,
    SAFETY_FAILURE,
    MISSING_REQUIREMENT,
    INVALID_CONFIGURATION,
    EXTERNAL_FAILURE,
    UNSAFE_TO_CONTINUE,
    MANUAL_INTERVENTION
}
