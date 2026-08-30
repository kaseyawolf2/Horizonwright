package io.github.kaseyawolf2.horizonwright.core.action;

public enum ActionRevocationReason {
    EXPLICIT_REVOCATION,
    RESTORE_EPOCH_ADVANCE,
    AUTOMATION_STOP,
    AUTOMATION_REARMED,
    SAFETY_LOCKDOWN,
    SAFETY_LOCKDOWN_RELEASED
}
