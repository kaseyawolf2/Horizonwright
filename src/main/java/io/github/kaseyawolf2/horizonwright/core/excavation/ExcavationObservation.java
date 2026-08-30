package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

/** One immutable observation captured on the client thread. */
public final class ExcavationObservation {

    private final BlockPosition position;
    private final ExcavationBlockClassification classification;
    private final String blockFingerprint;

    public ExcavationObservation(BlockPosition position, ExcavationBlockClassification classification,
        String blockFingerprint) {
        this.position = Objects.requireNonNull(position, "position");
        this.classification = Objects.requireNonNull(classification, "classification");
        this.blockFingerprint = requireFingerprint(blockFingerprint);
    }

    public BlockPosition getPosition() {
        return position;
    }

    public ExcavationBlockClassification getClassification() {
        return classification;
    }

    /** Opaque identity used by an adapter to verify the world did not change before acting. */
    public String getBlockFingerprint() {
        return blockFingerprint;
    }

    private static String requireFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("blockFingerprint must not be blank");
        }
        return fingerprint.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExcavationObservation)) {
            return false;
        }
        ExcavationObservation that = (ExcavationObservation) other;
        return position.equals(that.position) && classification == that.classification
            && blockFingerprint.equals(that.blockFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, classification, blockFingerprint);
    }
}
