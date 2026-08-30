package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

/** Immutable, bounded cylinder geometry and policy. */
public final class CylinderExcavationSpec {

    public static final int MAX_RADIUS = 250;
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 255;
    public static final int MAX_ABS_COORDINATE = 29_999_984;

    private final int dimensionId;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final int bottomY;
    private final int topY;
    private final ExcavationMode mode;
    private final long columnCount;
    private final long volume;
    private final String geometryKey;

    public CylinderExcavationSpec(int dimensionId, int centerX, int centerZ, int radius, int bottomY, int topY,
        ExcavationMode mode) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("radius must be between 0 and " + MAX_RADIUS);
        }
        if (bottomY < MIN_Y || topY > MAX_Y || topY < bottomY) {
            throw new IllegalArgumentException(
                "bottomY and topY must form an ascending range within " + MIN_Y + ".." + MAX_Y);
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        requireWorldCoordinate(centerX, radius, "centerX");
        requireWorldCoordinate(centerZ, radius, "centerZ");
        this.dimensionId = dimensionId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.bottomY = bottomY;
        this.topY = topY;
        this.mode = mode;
        this.columnCount = countColumns(radius);
        this.volume = Math.multiplyExact(columnCount, (long) topY - bottomY + 1L);
        this.geometryKey = "cylinder:" + dimensionId
            + ':'
            + centerX
            + ':'
            + centerZ
            + ':'
            + radius
            + ':'
            + bottomY
            + ':'
            + topY
            + ':'
            + mode.name();
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getRadius() {
        return radius;
    }

    public int getBottomY() {
        return bottomY;
    }

    public int getTopY() {
        return topY;
    }

    public ExcavationMode getMode() {
        return mode;
    }

    public long getColumnCount() {
        return columnCount;
    }

    public long getVolume() {
        return volume;
    }

    public String getGeometryKey() {
        return geometryKey;
    }

    public boolean contains(BlockPosition position) {
        if (position == null || position.getY() < bottomY || position.getY() > topY) {
            return false;
        }
        long deltaX = (long) position.getX() - centerX;
        long deltaZ = (long) position.getZ() - centerZ;
        return deltaX * deltaX + deltaZ * deltaZ <= (long) radius * radius;
    }

    private static void requireWorldCoordinate(int center, int radius, String name) {
        long minimum = (long) center - radius;
        long maximum = (long) center + radius;
        if (minimum < -MAX_ABS_COORDINATE || maximum > MAX_ABS_COORDINATE) {
            throw new IllegalArgumentException(name + " and radius exceed the supported world coordinate range");
        }
    }

    private static long countColumns(int radius) {
        long radiusSquared = (long) radius * radius;
        long count = 0L;
        for (int deltaX = -radius; deltaX <= radius; deltaX++) {
            long remaining = radiusSquared - (long) deltaX * deltaX;
            long maximumZ = integerSquareRoot(remaining);
            count = Math.addExact(count, Math.addExact(Math.multiplyExact(2L, maximumZ), 1L));
        }
        return count;
    }

    private static long integerSquareRoot(long value) {
        long candidate = (long) Math.sqrt(value);
        while ((candidate + 1L) * (candidate + 1L) <= value) {
            candidate++;
        }
        while (candidate * candidate > value) {
            candidate--;
        }
        return candidate;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CylinderExcavationSpec)) {
            return false;
        }
        CylinderExcavationSpec that = (CylinderExcavationSpec) other;
        return dimensionId == that.dimensionId && centerX == that.centerX
            && centerZ == that.centerZ
            && radius == that.radius
            && bottomY == that.bottomY
            && topY == that.topY
            && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionId, centerX, centerZ, radius, bottomY, topY, mode);
    }
}
