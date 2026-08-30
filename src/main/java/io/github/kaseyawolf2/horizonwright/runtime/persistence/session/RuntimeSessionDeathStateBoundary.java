package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;

/** Restart-relevant death state owned by the live safety runtime for one connection. */
public interface RuntimeSessionDeathStateBoundary {

    void restore(UnresolvedDeathState state);

    UnresolvedDeathState snapshot();
}
