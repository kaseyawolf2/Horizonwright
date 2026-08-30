package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

/** Availability state of the explicitly observed client world/profile binding. */
public enum ClientProfileBindingState {
    NO_WORLD,
    NEEDS_EXPLICIT_ENROLLMENT,
    NEEDS_EXPLICIT_REASSOCIATION,
    READY,
    FAILED
}
