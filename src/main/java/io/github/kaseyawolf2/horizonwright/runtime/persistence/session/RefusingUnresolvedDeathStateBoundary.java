package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;

/**
 * Temporary production boundary used until the live death controller owns the connection.
 *
 * <p>
 * A clean profile can start, but an unresolved death is never discarded or restored into a runtime which cannot
 * enforce it. Throwing during restore leaves the session failed before it is exposed and preserves runtime.json.
 * </p>
 */
public final class RefusingUnresolvedDeathStateBoundary implements RuntimeSessionDeathStateBoundary {

    private boolean restored;

    @Override
    public synchronized void restore(UnresolvedDeathState state) {
        if (restored) {
            throw new IllegalStateException("unresolved-death restore has already been attempted");
        }
        restored = true;
        if (state != null) {
            throw new IllegalStateException(
                "an unresolved death checkpoint requires the live death-safety runtime before automation can start");
        }
    }

    @Override
    public synchronized UnresolvedDeathState snapshot() {
        if (!restored) {
            throw new IllegalStateException("unresolved-death state has not been restored");
        }
        return null;
    }
}
