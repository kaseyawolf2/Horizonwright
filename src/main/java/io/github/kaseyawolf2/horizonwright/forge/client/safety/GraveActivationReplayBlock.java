package io.github.kaseyawolf2.horizonwright.forge.client.safety;

/** Restart-derived permanent block against replaying an already-consumed grave activation. */
public final class GraveActivationReplayBlock {

    private final boolean blocked;

    public GraveActivationReplayBlock(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean isBlocked() {
        return blocked;
    }
}
