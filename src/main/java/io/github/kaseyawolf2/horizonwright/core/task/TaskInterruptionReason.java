package io.github.kaseyawolf2.horizonwright.core.task;

/** Reasons that permit the orchestrator to bypass the normal safe-suspension handshake. */
public enum TaskInterruptionReason {
    SAFETY_PREEMPTION,
    ACTION_AUTHORITY_REVOCATION,
    CANCELLATION
}
