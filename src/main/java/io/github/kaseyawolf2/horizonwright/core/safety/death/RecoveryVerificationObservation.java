package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Evidence after the one permitted grave activation. */
public final class RecoveryVerificationObservation {

    private final GraveResolution graveResolution;
    private final GraveCandidate graveCandidate;
    private final InventoryManifest currentInventory;

    public RecoveryVerificationObservation(GraveResolution graveResolution, GraveCandidate graveCandidate,
        InventoryManifest currentInventory) {
        if (graveResolution == null || currentInventory == null) {
            throw new IllegalArgumentException("graveResolution and currentInventory must not be null");
        }
        if (graveResolution == GraveResolution.PRESENT && graveCandidate == null) {
            throw new IllegalArgumentException("a present grave requires graveCandidate evidence");
        }
        if (graveResolution != GraveResolution.PRESENT && graveCandidate != null) {
            throw new IllegalArgumentException("only a present grave may carry graveCandidate evidence");
        }
        this.graveResolution = graveResolution;
        this.graveCandidate = graveCandidate;
        this.currentInventory = currentInventory;
    }

    public GraveResolution getGraveResolution() {
        return graveResolution;
    }

    public GraveCandidate getGraveCandidate() {
        return graveCandidate;
    }

    public InventoryManifest getCurrentInventory() {
        return currentInventory;
    }
}
