package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

/** Lifecycle of one explicitly bound profile/world runtime session. */
public enum RuntimeSessionState {
    UNBOUND,
    WAITING_FOR_WORLD,
    ACTIVE,
    RETIRED,
    FAILED
}
