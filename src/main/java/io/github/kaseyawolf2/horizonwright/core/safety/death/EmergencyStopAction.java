package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Operations required synchronously on the first death latch. */
public enum EmergencyStopAction {
    FORCE_CHECKPOINT_ACTIVE_TASK,
    CANCEL_NAVIGATION_AND_PENDING_WORK,
    REVOKE_ALL_ACTION_LEASES,
    CLEAR_GLOBAL_AND_REAL_INPUT,
    CLEAR_NAVIGATION_PRIVATE_INPUT,
    RELEASE_ALL_HELD_USE,
    INVALIDATE_ACTION_AND_CONTAINER_EPOCHS,
    DENY_ALL_REACQUISITION
}
