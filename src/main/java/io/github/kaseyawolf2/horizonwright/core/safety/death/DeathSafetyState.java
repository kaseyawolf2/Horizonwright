package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** High-level per-connection death-safety state. */
public enum DeathSafetyState {
    ACTIVE,
    CRITICAL,
    DEATH_LATCHED,
    RESPAWN_REQUESTED,
    POST_RESPAWN_QUARANTINE,
    RECOVERY_READY,
    MANUAL_HOLD
}
