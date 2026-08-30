package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Objects;

/** Bounded client-thread observation; optional-mod objects never cross this boundary. */
public final class CropObservation {

    private final BasePosition position;
    private final CropFamily family;
    private final String observationFingerprint;
    private final String requiredSeedFingerprint;
    private final boolean maturityKnown;
    private final boolean mature;
    private final boolean protectedBlock;

    public CropObservation(BasePosition position, CropFamily family, String observationFingerprint,
        String requiredSeedFingerprint, boolean maturityKnown, boolean mature, boolean protectedBlock) {
        if (position == null || family == null
            || observationFingerprint == null
            || observationFingerprint.trim()
                .isEmpty()
            || requiredSeedFingerprint == null
            || requiredSeedFingerprint.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("position, family, observation, and required seed are required");
        }
        if (!maturityKnown && mature) {
            throw new IllegalArgumentException("an observation cannot be mature when maturity is unknown");
        }
        this.position = position;
        this.family = family;
        this.observationFingerprint = observationFingerprint.trim();
        this.requiredSeedFingerprint = requiredSeedFingerprint.trim();
        this.maturityKnown = maturityKnown;
        this.mature = mature;
        this.protectedBlock = protectedBlock;
    }

    public BasePosition getPosition() {
        return position;
    }

    public CropFamily getFamily() {
        return family;
    }

    /** Opaque adapter fingerprint covering the target position and all decision-relevant block state. */
    public String getObservationFingerprint() {
        return observationFingerprint;
    }

    /** Exact inventory material identity required to restore this crop after a destructive harvest. */
    public String getRequiredSeedFingerprint() {
        return requiredSeedFingerprint;
    }

    public boolean isMaturityKnown() {
        return maturityKnown;
    }

    public boolean isMature() {
        return mature;
    }

    public boolean isProtectedBlock() {
        return protectedBlock;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CropObservation)) {
            return false;
        }
        CropObservation that = (CropObservation) other;
        return maturityKnown == that.maturityKnown && mature == that.mature
            && protectedBlock == that.protectedBlock
            && position.equals(that.position)
            && family == that.family
            && observationFingerprint.equals(that.observationFingerprint)
            && requiredSeedFingerprint.equals(that.requiredSeedFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            position,
            family,
            observationFingerprint,
            requiredSeedFingerprint,
            maturityKnown,
            mature,
            protectedBlock);
    }
}
