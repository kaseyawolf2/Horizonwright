package io.github.kaseyawolf2.horizonwright.core.excavation;

/** Deterministic retained infrastructure positions inside a managed cylinder. */
public final class ManagedQuarryGeometry {

    private ManagedQuarryGeometry() {}

    /**
     * Returns one step on a descending square staircase inscribed near the cylinder perimeter. Consecutive layers are
     * horizontally adjacent and one block lower, including when the path wraps around a corner or completes a circuit.
     */
    public static BlockPosition rampStep(CylinderExcavationSpec spec, int layerY) {
        requireManagedLayer(spec, layerY);
        if (spec.isRectangle()) {
            int w = spec.getMaximumX() - spec.getMinimumX(), d = spec.getMaximumZ() - spec.getMinimumZ();
            if (w < 2 || d < 2)
                throw new IllegalArgumentException("Managed rectangles need at least 3x3 blocks for a ramp");
            int i = Math.floorMod(spec.getTopY() - layerY, 2 * (w + d));
            if (i < d) return new BlockPosition(spec.getMaximumX(), layerY, spec.getMinimumZ() + i);
            i -= d;
            if (i < w) return new BlockPosition(spec.getMaximumX() - i, layerY, spec.getMaximumZ());
            i -= w;
            if (i < d) return new BlockPosition(spec.getMinimumX(), layerY, spec.getMaximumZ() - i);
            return new BlockPosition(spec.getMinimumX() + i - d, layerY, spec.getMinimumZ());
        }
        if (spec.getRadius() < 2) throw new IllegalArgumentException("managed quarry ramps require radius 2 or larger");
        int distance = (int) Math.floor(spec.getRadius() / Math.sqrt(2.0D));
        int sideLength = Math.multiplyExact(distance, 2);
        int perimeterLength = Math.multiplyExact(sideLength, 4);
        int depth = spec.getTopY() - layerY;
        int index = Math.floorMod(depth, perimeterLength);
        int x;
        int z;
        if (index < sideLength) {
            x = spec.getCenterX() + distance;
            z = spec.getCenterZ() - distance + index;
        } else if (index < sideLength * 2) {
            int offset = index - sideLength;
            x = spec.getCenterX() + distance - offset;
            z = spec.getCenterZ() + distance;
        } else if (index < sideLength * 3) {
            int offset = index - sideLength * 2;
            x = spec.getCenterX() - distance;
            z = spec.getCenterZ() + distance - offset;
        } else {
            int offset = index - sideLength * 3;
            x = spec.getCenterX() - distance + offset;
            z = spec.getCenterZ() - distance;
        }
        return new BlockPosition(x, layerY, z);
    }

    /** The light occupies the air block immediately above its supporting ramp step. */
    public static BlockPosition lightPosition(CylinderExcavationSpec spec, int layerY) {
        BlockPosition ramp = rampStep(spec, layerY);
        return new BlockPosition(ramp.getX(), Math.addExact(ramp.getY(), 1), ramp.getZ());
    }

    /** True when the position is the retained stair step for its layer. */
    public static boolean isRampStep(CylinderExcavationSpec spec, BlockPosition position) {
        if (position == null) return false;
        requireManagedLayer(spec, position.getY());
        return rampStep(spec, position.getY()).equals(position);
    }

    /** True when the position is reserved for a light supported by the layer below it. */
    public static boolean isScheduledLightPosition(CylinderExcavationSpec spec,
        ManagedQuarryConfiguration configuration, BlockPosition position) {
        if (configuration == null || position == null) return false;
        requireManagedLayer(spec, position.getY());
        int supportLayer = position.getY() - 1;
        return supportLayer >= spec.getBottomY()
            && (spec.getTopY() - supportLayer) % configuration.getLightLayerInterval() == 0
            && lightPosition(spec, supportLayer).equals(position);
    }

    private static void requireManagedLayer(CylinderExcavationSpec spec, int layerY) {
        if (spec == null || spec.getMode() != ExcavationMode.MANAGED_QUARRY) {
            throw new IllegalArgumentException("managed quarry geometry requires a managed cylinder");
        }
        if (layerY < spec.getBottomY() || layerY > spec.getTopY()) {
            throw new IllegalArgumentException("layerY is outside the managed cylinder");
        }
    }
}
