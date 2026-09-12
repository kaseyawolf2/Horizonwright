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
    private long columnCount;
    private long volume;
    private String geometryKey;
    private ExcavationTraversal traversal = ExcavationTraversal.CHUNKS;
    private CircularSpiral circularSpiral;
    private int spiralWidth = 1;

    public int getSpiralWidth() {
        return spiralWidth;
    }

    boolean usesSpiralLookup() {
        return traversal == ExcavationTraversal.CIRCLE_SPIRAL
            || (traversal == ExcavationTraversal.SQUARE_SPIRAL && spiralWidth > 1);
    }

    public ExcavationTraversal getTraversal() {
        return traversal;
    }

    synchronized CircularSpiral circularSpiral() {
        if (circularSpiral == null) circularSpiral = new CircularSpiral(this);
        return circularSpiral;
    }

    public boolean isSpiral() {
        return traversal == ExcavationTraversal.SQUARE_SPIRAL;
    }

    public CylinderExcavationSpec withSpiral() {
        return withTraversal(ExcavationTraversal.SQUARE_SPIRAL);
    }

    public CylinderExcavationSpec withTraversal(ExcavationTraversal order) {
        return withTraversal(order, spiralWidth);
    }

    public CylinderExcavationSpec withTraversal(ExcavationTraversal order, int bandWidth) {
        Objects.requireNonNull(order, "order");
        if (bandWidth < 1 || bandWidth > 64) throw new IllegalArgumentException("spiral width must be 1..64 blocks");
        CylinderExcavationSpec result = isRectangle()
            ? rectangle(dimensionId, minX, maxX, minZ, maxZ, bottomY, topY, mode)
            : new CylinderExcavationSpec(dimensionId, centerX, centerZ, radius, bottomY, topY, mode);
        result.traversal = order;
        result.spiralWidth = bandWidth;
        if (order != ExcavationTraversal.CHUNKS) result.geometryKey += ":" + order.id();
        if (bandWidth > 1 && (order == ExcavationTraversal.CIRCLE_SPIRAL || order == ExcavationTraversal.SQUARE_SPIRAL))
            result.geometryKey += ":width-v1:" + bandWidth;
        return result;
    }

    private boolean rectangle;
    private int minX, maxX, minZ, maxZ;

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

    public static CylinderExcavationSpec rectangle(int dimension, int minX, int maxX, int minZ, int maxZ, int bottom,
        int top, ExcavationMode mode) {
        if (maxX < minX || maxZ < minZ || (long) maxX - minX > 500 || (long) maxZ - minZ > 500)
            throw new IllegalArgumentException("Rectangle spans must be 1-501 blocks");
        int radius = (Math.max(maxX - minX, maxZ - minZ) + 1) / 2;
        CylinderExcavationSpec spec = new CylinderExcavationSpec(
            dimension,
            minX + (maxX - minX) / 2,
            minZ + (maxZ - minZ) / 2,
            radius,
            bottom,
            top,
            mode);
        spec.rectangle = true;
        spec.minX = minX;
        spec.maxX = maxX;
        spec.minZ = minZ;
        spec.maxZ = maxZ;
        spec.columnCount = ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
        spec.volume = spec.columnCount * (top - bottom + 1L);
        spec.geometryKey = "rectangle:" + dimension
            + ":"
            + minX
            + ":"
            + maxX
            + ":"
            + minZ
            + ":"
            + maxZ
            + ":"
            + bottom
            + ":"
            + top
            + ":"
            + mode;
        return spec;
    }

    public boolean isRectangle() {
        return rectangle;
    }

    public int getMinimumX() {
        return rectangle ? minX : centerX - radius;
    }

    public int getMaximumX() {
        return rectangle ? maxX : centerX + radius;
    }

    public int getMinimumZ() {
        return rectangle ? minZ : centerZ - radius;
    }

    public int getMaximumZ() {
        return rectangle ? maxZ : centerZ + radius;
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
        if (rectangle) return position.getX() >= minX && position.getX() <= maxX
            && position.getZ() >= minZ
            && position.getZ() <= maxZ;
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
        return geometryKey.equals(that.geometryKey) && dimensionId == that.dimensionId
            && centerX == that.centerX
            && centerZ == that.centerZ
            && radius == that.radius
            && bottomY == that.bottomY
            && topY == that.topY
            && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(geometryKey);
    }
}
