package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Objects;

/** Integer block position which always carries its dimension. */
public final class DimensionBlockPosition {

    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;

    public DimensionBlockPosition(int dimensionId, int x, int y, int z) {
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

    public boolean isWithinRadius(DimensionBlockPosition other, int radius) {
        if (other == null || radius < 0 || dimensionId != other.dimensionId) {
            return false;
        }
        double dx = (double) x - other.x;
        double dy = (double) y - other.y;
        double dz = (double) z - other.z;
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DimensionBlockPosition)) {
            return false;
        }
        DimensionBlockPosition that = (DimensionBlockPosition) other;
        return dimensionId == that.dimensionId && x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionId, x, y, z);
    }

    @Override
    public String toString() {
        return dimensionId + ":" + x + "," + y + "," + z;
    }
}
