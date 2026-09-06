package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Objects;

/** Inclusive, dimension-scoped operational area. */
public final class NamedArea {

    private final String id;
    private final String displayName;
    private final BasePosition minimum;
    private final BasePosition maximum;
    private final AreaKind kind;
    private final String storageId;
    private Integer circleRadius;
    private Integer circleCenterY;

    public NamedArea(String id, String displayName, BasePosition first, BasePosition second) {
        this(id, displayName, first, second, AreaKind.UNASSIGNED, null);
    }

    public NamedArea(String id, String displayName, BasePosition first, BasePosition second, AreaKind kind,
        String storageId) {
        this.kind = kind == null ? AreaKind.UNASSIGNED : kind;
        this.storageId = storageId == null || storageId.trim()
            .isEmpty() ? null : storageId.trim();
        if (id == null || id.trim()
            .isEmpty()
            || displayName == null
            || displayName.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("id and displayName must not be blank");
        }
        if (first == null || second == null || first.getDimensionId() != second.getDimensionId()) {
            throw new IllegalArgumentException("area corners must be in one dimension");
        }
        this.id = id.trim();
        this.displayName = displayName.trim();
        this.minimum = new BasePosition(
            first.getDimensionId(),
            Math.min(first.getX(), second.getX()),
            Math.min(first.getY(), second.getY()),
            Math.min(first.getZ(), second.getZ()));
        this.maximum = new BasePosition(
            first.getDimensionId(),
            Math.max(first.getX(), second.getX()),
            Math.max(first.getY(), second.getY()),
            Math.max(first.getZ(), second.getZ()));
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BasePosition getMinimum() {
        return minimum;
    }

    public BasePosition getMaximum() {
        return maximum;
    }

    public AreaKind getKind() {
        return kind == null ? AreaKind.UNASSIGNED : kind;
    }

    public String getStorageId() {
        return storageId;
    }

    public String resolvedStorageId() {
        return storageId == null ? "default-chest" : storageId;
    }

    public NamedArea withSettings(AreaKind selectedKind, String chest) {
        NamedArea result = new NamedArea(id, displayName, minimum, maximum, selectedKind, chest);
        result.circleRadius = circleRadius;
        result.circleCenterY = circleCenterY;
        return result;
    }

    public static NamedArea circle(String id, String name, BasePosition center, int radius, int bottomY, int topY) {
        if (radius < 0 || radius > 250 || bottomY < 0 || topY > 255 || bottomY > topY)
            throw new IllegalArgumentException("Circle radius must be 0-250 and Y limits within 0-255");
        NamedArea area = new NamedArea(
            id,
            name,
            new BasePosition(
                center.getDimensionId(),
                Math.subtractExact(center.getX(), radius),
                bottomY,
                Math.subtractExact(center.getZ(), radius)),
            new BasePosition(
                center.getDimensionId(),
                Math.addExact(center.getX(), radius),
                topY,
                Math.addExact(center.getZ(), radius)));
        area.circleRadius = radius;
        area.circleCenterY = center.getY();
        return area;
    }

    public boolean isCircular() {
        return circleRadius != null;
    }

    /** Validate shape fields after JSON deserialization, which bypasses constructors. */
    public void validateShape() {
        if (minimum == null || maximum == null || minimum.getDimensionId() != maximum.getDimensionId())
            throw new IllegalArgumentException("Area must have bounds in one dimension");
        if (isCircular()) {
            if (circleCenterY == null) throw new IllegalArgumentException("Circle center Y is missing");
            NamedArea expected = circle(id, displayName, getCenter(), circleRadius, minimum.getY(), maximum.getY());
            if (!minimum.equals(expected.minimum) || !maximum.equals(expected.maximum))
                throw new IllegalArgumentException("Circle bounds do not match its radius");
        }
    }

    public int getRadius() {
        if (!isCircular()) throw new IllegalStateException("Rectangle has no radius");
        return circleRadius;
    }

    public BasePosition getCenter() {
        return new BasePosition(
            minimum.getDimensionId(),
            minimum.getX() + (maximum.getX() - minimum.getX()) / 2,
            circleCenterY == null ? minimum.getY() : circleCenterY,
            minimum.getZ() + (maximum.getZ() - minimum.getZ()) / 2);
    }

    public boolean contains(BasePosition position) {
        return position != null && position.getDimensionId() == minimum.getDimensionId()
            && position.getX() >= minimum.getX()
            && position.getX() <= maximum.getX()
            && position.getY() >= minimum.getY()
            && position.getY() <= maximum.getY()
            && position.getZ() >= minimum.getZ()
            && position.getZ() <= maximum.getZ()
            && (!isCircular()
                || squaredDistance(position.getX(), position.getZ()) <= (long) circleRadius * circleRadius);
    }

    private long squaredDistance(int x, int z) {
        long dx = (long) x - getCenter().getX(), dz = (long) z - getCenter().getZ();
        return dx * dx + dz * dz;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NamedArea)) {
            return false;
        }
        NamedArea that = (NamedArea) other;
        return id.equals(that.id) && displayName.equals(that.displayName)
            && minimum.equals(that.minimum)
            && maximum.equals(that.maximum)
            && getKind() == that.getKind()
            && Objects.equals(storageId, that.storageId)
            && Objects.equals(circleRadius, that.circleRadius)
            && Objects.equals(circleCenterY, that.circleCenterY);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, minimum, maximum, getKind(), storageId, circleRadius, circleCenterY);
    }

    @Override
    public String toString() {
        return "NamedArea{" + id + ':' + minimum + ".." + maximum + '}';
    }
}
