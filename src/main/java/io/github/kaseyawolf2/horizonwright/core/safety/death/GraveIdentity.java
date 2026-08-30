package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Objects;

/** Stable grave identity used by the one-shot authorization. */
public final class GraveIdentity {

    private final String tileIdentity;
    private final DimensionBlockPosition position;

    public GraveIdentity(String tileIdentity, DimensionBlockPosition position) {
        this.tileIdentity = ConnectionIdentity.requireText(tileIdentity, "tileIdentity");
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        this.position = position;
    }

    public String getTileIdentity() {
        return tileIdentity;
    }

    public DimensionBlockPosition getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraveIdentity)) {
            return false;
        }
        GraveIdentity that = (GraveIdentity) other;
        return tileIdentity.equals(that.tileIdentity) && position.equals(that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tileIdentity, position);
    }
}
