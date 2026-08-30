package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Objects;

/** Immutable dimension-bearing block position for operational-base assets. */
public final class BasePosition implements Comparable<BasePosition> {

    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;

    public BasePosition(int dimensionId, int x, int y, int z) {
        if (y < 0 || y > 255) {
            throw new IllegalArgumentException("y must be in the 1.7.10 world range");
        }
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

    @Override
    public int compareTo(BasePosition other) {
        int dimension = Integer.compare(dimensionId, other.dimensionId);
        if (dimension != 0) {
            return dimension;
        }
        int vertical = Integer.compare(y, other.y);
        if (vertical != 0) {
            return vertical;
        }
        int northSouth = Integer.compare(z, other.z);
        return northSouth != 0 ? northSouth : Integer.compare(x, other.x);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BasePosition)) {
            return false;
        }
        BasePosition that = (BasePosition) other;
        return dimensionId == that.dimensionId && x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionId, x, y, z);
    }
}
