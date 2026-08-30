package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

public final class RouteNode {

    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;
    private final String label;

    public RouteNode(int dimensionId, int x, int y, int z, String label) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = PersistenceValidation.normalizeOptionalText(label);
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getLabel() {
        return label;
    }

    public DimensionPosition getPosition() {
        return new DimensionPosition(dimensionId, x, y, z);
    }

    void validate() {}

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteNode)) {
            return false;
        }
        RouteNode that = (RouteNode) other;
        return dimensionId == that.dimensionId && x == that.x
            && y == that.y
            && z == that.z
            && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionId, x, y, z, label);
    }
}
