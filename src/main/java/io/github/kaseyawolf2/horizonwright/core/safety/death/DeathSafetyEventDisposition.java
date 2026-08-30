package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Whether an observation was accepted by the current safety authority. */
public enum DeathSafetyEventDisposition {
    ACCEPTED,
    IGNORED_IN_CURRENT_STATE,
    STALE_CONNECTION_EPOCH,
    STALE_EVENT_SEQUENCE,
    STALE_CLIENT_TICK,
    STALE_DEATH_EPOCH,
    DISCONNECTED
}
