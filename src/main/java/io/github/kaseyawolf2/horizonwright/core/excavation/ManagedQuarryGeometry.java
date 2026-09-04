package io.github.kaseyawolf2.horizonwright.core.excavation;

/** Deterministic infrastructure positions outside a managed cylinder's excavated volume. */
public final class ManagedQuarryGeometry {

    private ManagedQuarryGeometry() {}

    /**
     * Returns one step on a descending square perimeter staircase. Consecutive layers are horizontally adjacent and
     * one block lower, including when the path wraps around a corner or completes a circuit.
     */
    public static BlockPosition rampStep(CylinderExcavationSpec spec, int layerY) {
        requireManagedLayer(spec, layerY);
        int distance = Math.addExact(spec.getRadius(), 1);
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
        requireWorldCoordinate(x, "ramp X");
        requireWorldCoordinate(z, "ramp Z");
        return new BlockPosition(x, layerY, z);
    }

    /** The light occupies the air block immediately above its supporting ramp step. */
    public static BlockPosition lightPosition(CylinderExcavationSpec spec, int layerY) {
        BlockPosition ramp = rampStep(spec, layerY);
        return new BlockPosition(ramp.getX(), Math.addExact(ramp.getY(), 1), ramp.getZ());
    }

    private static void requireManagedLayer(CylinderExcavationSpec spec, int layerY) {
        if (spec == null || spec.getMode() != ExcavationMode.MANAGED_QUARRY) {
            throw new IllegalArgumentException("managed quarry geometry requires a managed cylinder");
        }
        if (layerY < spec.getBottomY() || layerY > spec.getTopY()) {
            throw new IllegalArgumentException("layerY is outside the managed cylinder");
        }
    }

    private static void requireWorldCoordinate(int value, String field) {
        if (Math.abs((long) value) > CylinderExcavationSpec.MAX_ABS_COORDINATE) {
            throw new IllegalArgumentException(field + " exceeds the supported world coordinate range");
        }
    }
}
