package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

/** A dimension-local integer block position with no game-runtime dependencies. */
public final class BlockPosition {

    private final int x;
    private final int y;
    private final int z;

    public BlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockPosition)) {
            return false;
        }
        BlockPosition that = (BlockPosition) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "BlockPosition{" + x + ',' + y + ',' + z + '}';
    }
}
