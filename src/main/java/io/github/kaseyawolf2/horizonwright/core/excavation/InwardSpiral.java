package io.github.kaseyawolf2.horizonwright.core.excavation;

/** Stateless clockwise rectangular rings, clipped to a circle by the normal geometry filter. */
final class InwardSpiral {

    private InwardSpiral() {}

    static BlockPosition next(CylinderExcavationSpec spec, BlockPosition p) {
        int x = p.getX(), z = p.getZ();
        int ring = Math.min(
            Math.min(x - spec.getMinimumX(), spec.getMaximumX() - x),
            Math.min(z - spec.getMinimumZ(), spec.getMaximumZ() - z));
        int left = spec.getMinimumX() + ring, right = spec.getMaximumX() - ring;
        int top = spec.getMinimumZ() + ring, bottom = spec.getMaximumZ() - ring;
        if (left == right) return z < bottom ? new BlockPosition(x, p.getY(), z + 1) : null;
        if (top == bottom) return x < right ? new BlockPosition(x + 1, p.getY(), z) : null;
        if (z == top && x < right) return new BlockPosition(x + 1, p.getY(), z);
        if (x == right && z < bottom) return new BlockPosition(x, p.getY(), z + 1);
        if (z == bottom && x > left) return new BlockPosition(x - 1, p.getY(), z);
        if (z > top + 1) return new BlockPosition(x, p.getY(), z - 1);
        return left + 1 <= right - 1 && top + 1 <= bottom - 1 ? new BlockPosition(left + 1, p.getY(), top + 1) : null;
    }
}
