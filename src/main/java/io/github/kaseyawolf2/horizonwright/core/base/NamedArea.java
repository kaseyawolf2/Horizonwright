package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Objects;

/** Inclusive, dimension-scoped operational area. */
public final class NamedArea {

    private final String id;
    private final String displayName;
    private final BasePosition minimum;
    private final BasePosition maximum;

    public NamedArea(String id, String displayName, BasePosition first, BasePosition second) {
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

    public boolean contains(BasePosition position) {
        return position != null && position.getDimensionId() == minimum.getDimensionId()
            && position.getX() >= minimum.getX()
            && position.getX() <= maximum.getX()
            && position.getY() >= minimum.getY()
            && position.getY() <= maximum.getY()
            && position.getZ() >= minimum.getZ()
            && position.getZ() <= maximum.getZ();
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
            && maximum.equals(that.maximum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, minimum, maximum);
    }

    @Override
    public String toString() {
        return "NamedArea{" + id + ':' + minimum + ".." + maximum + '}';
    }
}
