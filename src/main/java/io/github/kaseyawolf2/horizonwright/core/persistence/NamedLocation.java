package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceValidation.IdentifiedPersistenceValue;

public final class NamedLocation implements IdentifiedPersistenceValue {

    private final String id;
    private final String displayName;
    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;

    public NamedLocation(String id, String displayName, int dimensionId, int x, int y, int z) {
        this.id = PersistenceValidation.requireStableId(id, "id");
        this.displayName = PersistenceValidation.requireText(displayName, "displayName");
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
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

    public DimensionPosition getPosition() {
        return new DimensionPosition(dimensionId, x, y, z);
    }

    void validate() {
        PersistenceValidation.requireStableId(id, "named location id");
        PersistenceValidation.requireText(displayName, "named location displayName");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NamedLocation)) {
            return false;
        }
        NamedLocation that = (NamedLocation) other;
        return dimensionId == that.dimensionId && x == that.x
            && y == that.y
            && z == that.z
            && Objects.equals(id, that.id)
            && Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, dimensionId, x, y, z);
    }
}
