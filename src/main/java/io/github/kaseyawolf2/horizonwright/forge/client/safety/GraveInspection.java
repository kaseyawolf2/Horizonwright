package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveCandidate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveResolution;

/** Immutable exact-target evidence used after the authorized grave activation. */
public final class GraveInspection {

    private final GraveResolution resolution;
    private final GraveCandidate candidate;

    public GraveInspection(GraveResolution resolution, GraveCandidate candidate) {
        if (resolution == null) {
            throw new IllegalArgumentException("resolution must not be null");
        }
        if (resolution == GraveResolution.PRESENT && candidate == null) {
            throw new IllegalArgumentException("a present grave requires candidate evidence");
        }
        if (resolution != GraveResolution.PRESENT && candidate != null) {
            throw new IllegalArgumentException("only a present grave may carry candidate evidence");
        }
        this.resolution = resolution;
        this.candidate = candidate;
    }

    public GraveResolution getResolution() {
        return resolution;
    }

    public GraveCandidate getCandidate() {
        return candidate;
    }
}
