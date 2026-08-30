package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;
import java.util.Optional;

/** One volume-target intent and its exact successor checkpoint. */
public final class ExcavationIntent {

    private final BlockPosition position;
    private final ExcavationIntentKind kind;
    private final String observedFingerprint;
    private final String approvedMaterial;
    private final ExcavationFrontier nextFrontier;

    ExcavationIntent(BlockPosition position, ExcavationIntentKind kind, String observedFingerprint,
        String approvedMaterial, ExcavationFrontier nextFrontier) {
        this.position = Objects.requireNonNull(position, "position");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.observedFingerprint = requireText(observedFingerprint, "observedFingerprint");
        if (kind == ExcavationIntentKind.CONTAIN_FLUID) {
            this.approvedMaterial = requireText(approvedMaterial, "approvedMaterial");
        } else if (approvedMaterial != null) {
            throw new IllegalArgumentException("only fluid-containment intents carry an approved material");
        } else {
            this.approvedMaterial = null;
        }
        this.nextFrontier = Objects.requireNonNull(nextFrontier, "nextFrontier");
    }

    public BlockPosition getPosition() {
        return position;
    }

    public ExcavationIntentKind getKind() {
        return kind;
    }

    public String getObservedFingerprint() {
        return observedFingerprint;
    }

    public Optional<String> getApprovedMaterial() {
        return Optional.ofNullable(approvedMaterial);
    }

    public ExcavationFrontier getNextFrontier() {
        return nextFrontier;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExcavationIntent)) {
            return false;
        }
        ExcavationIntent that = (ExcavationIntent) other;
        return position.equals(that.position) && kind == that.kind
            && observedFingerprint.equals(that.observedFingerprint)
            && Objects.equals(approvedMaterial, that.approvedMaterial)
            && nextFrontier.equals(that.nextFrontier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, kind, observedFingerprint, approvedMaterial, nextFrontier);
    }
}
