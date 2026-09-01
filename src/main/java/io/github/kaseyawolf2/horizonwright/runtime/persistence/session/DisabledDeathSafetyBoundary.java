package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;

/**
 * Inert session boundary used while the death-safety latch is disabled.
 *
 * <p>
 * Legacy unresolved-death state is deliberately retired during restore and is not written back. This boundary
 * never observes health, revokes action authority, installs packet gates, or restricts player input.
 * </p>
 */
public final class DisabledDeathSafetyBoundary implements RuntimeSessionDeathStateBoundary {

    private boolean restored;

    @Override
    public synchronized void restore(UnresolvedDeathState state) {
        if (restored) {
            throw new IllegalStateException("disabled death boundary restore has already been attempted");
        }
        restored = true;
    }

    @Override
    public synchronized void clientTick() {
        requireRestored();
    }

    @Override
    public synchronized void disconnect() {
        requireRestored();
    }

    @Override
    public synchronized UnresolvedDeathState snapshot() {
        requireRestored();
        return null;
    }

    @Override
    public synchronized void close() {}

    private void requireRestored() {
        if (!restored) {
            throw new IllegalStateException("disabled death boundary has not been restored");
        }
    }
}
