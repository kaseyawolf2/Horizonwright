package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

public final class DimensionPosition {

    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;

    public DimensionPosition(int dimensionId, int x, int y, int z) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
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

    void validate() {}

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DimensionPosition)) {
            return false;
        }
        DimensionPosition that = (DimensionPosition) other;
        return dimensionId == that.dimensionId && x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionId, x, y, z);
    }

    @Override
    public String toString() {
        return "DimensionPosition{" + "dimensionId=" + dimensionId + ", x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
