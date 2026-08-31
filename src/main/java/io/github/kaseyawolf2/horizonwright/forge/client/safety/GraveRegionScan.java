package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveCandidate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchStatus;

/** Immutable result of scanning loaded tile entities inside the bounded grave region. */
public final class GraveRegionScan {

    private final GraveSearchStatus status;
    private final List<GraveCandidate> candidates;

    public GraveRegionScan(GraveSearchStatus status, List<GraveCandidate> candidates) {
        if (status == null || candidates == null || candidates.contains(null)) {
            throw new IllegalArgumentException("scan status and candidates must not be null or contain null");
        }
        if ((status == GraveSearchStatus.REGION_UNLOADED || status == GraveSearchStatus.EVIDENCE_UNAVAILABLE)
            && !candidates.isEmpty()) {
            throw new IllegalArgumentException("an unavailable scan cannot provide reliable grave candidates");
        }
        this.status = status;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    public GraveSearchStatus getStatus() {
        return status;
    }

    public List<GraveCandidate> getCandidates() {
        return candidates;
    }
}
