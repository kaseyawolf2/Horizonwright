package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One tick of grave discovery or stability evidence. */
public final class GraveSearchObservation {

    private final GraveSearchStatus status;
    private final List<GraveCandidate> candidates;
    private final InventoryManifest currentInventory;
    private final boolean emptyHotbarHandAvailable;

    public GraveSearchObservation(GraveSearchStatus status, List<GraveCandidate> candidates,
        InventoryManifest currentInventory, boolean emptyHotbarHandAvailable) {
        if (status == null || candidates == null || candidates.contains(null) || currentInventory == null) {
            throw new IllegalArgumentException("grave search evidence must not be null or contain null");
        }
        this.status = status;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.currentInventory = currentInventory;
        this.emptyHotbarHandAvailable = emptyHotbarHandAvailable;
    }

    public GraveSearchStatus getStatus() {
        return status;
    }

    public List<GraveCandidate> getCandidates() {
        return candidates;
    }

    public InventoryManifest getCurrentInventory() {
        return currentInventory;
    }

    public boolean isEmptyHotbarHandAvailable() {
        return emptyHotbarHandAvailable;
    }
}
