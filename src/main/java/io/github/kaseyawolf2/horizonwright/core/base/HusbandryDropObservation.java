package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Objects;

/** Typed, position-bound item-entity candidate from a complete named-pen scan. */
public final class HusbandryDropObservation {

    private final String identity;
    private final String itemFingerprint;
    private final BasePosition position;

    public HusbandryDropObservation(String identity, String itemFingerprint, BasePosition position) {
        if (identity == null || identity.trim()
            .isEmpty()
            || itemFingerprint == null
            || itemFingerprint.trim()
                .isEmpty()
            || position == null) {
            throw new IllegalArgumentException("drop identity, item fingerprint, and position are required");
        }
        this.identity = identity.trim();
        this.itemFingerprint = itemFingerprint.trim();
        this.position = position;
    }

    public String getIdentity() {
        return identity;
    }

    public String getItemFingerprint() {
        return itemFingerprint;
    }

    public BasePosition getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HusbandryDropObservation)) {
            return false;
        }
        HusbandryDropObservation that = (HusbandryDropObservation) other;
        return identity.equals(that.identity) && itemFingerprint.equals(that.itemFingerprint)
            && position.equals(that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, itemFingerprint, position);
    }
}
