package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Detailed phase within an unresolved death. */
public enum RecoveryPhase {
    NONE,
    AWAITING_RESPAWN,
    REVALIDATING_RESPAWN,
    RESPAWN_STABILIZING,
    NAVIGATING_WITH_INTERACTIONS_DISABLED,
    SEARCHING_FOR_GRAVE,
    STABILIZING_GRAVE,
    AWAITING_SCOPED_ACTIVATION,
    VERIFYING_RECOVERY,
    MANUAL_HOLD
}
