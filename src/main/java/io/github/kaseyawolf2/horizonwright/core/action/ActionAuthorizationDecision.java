package io.github.kaseyawolf2.horizonwright.core.action;

public enum ActionAuthorizationDecision {

    /** No Horizonwright action is active, so the packet belongs to normal player control. */
    PLAYER_PASSTHROUGH(true),
    AUTHORIZED(true),
    BLOCKED_MISSING_CAPABILITY(false),
    BLOCKED_REVOKED_EPOCH(false);

    private final boolean allowed;

    ActionAuthorizationDecision(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean isAllowed() {
        return allowed;
    }
}
