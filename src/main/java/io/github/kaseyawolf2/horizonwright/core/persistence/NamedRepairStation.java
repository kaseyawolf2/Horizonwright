package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceValidation.IdentifiedPersistenceValue;

/** Named Tinkers repair station bound to one location and material loadout. */
public final class NamedRepairStation implements IdentifiedPersistenceValue {

    private final String id;
    private final String displayName;
    private final String locationId;
    private final String loadoutId;

    public NamedRepairStation(String id, String displayName, String locationId, String loadoutId) {
        this.id = PersistenceValidation.requireStableId(id, "id");
        this.displayName = PersistenceValidation.requireText(displayName, "displayName");
        this.locationId = PersistenceValidation.requireStableId(locationId, "locationId");
        this.loadoutId = PersistenceValidation.requireStableId(loadoutId, "loadoutId");
    }

    @Override
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getLoadoutId() {
        return loadoutId;
    }

    void validate() {
        PersistenceValidation.requireStableId(id, "repair station id");
        PersistenceValidation.requireText(displayName, "repair station displayName");
        PersistenceValidation.requireStableId(locationId, "repair station locationId");
        PersistenceValidation.requireStableId(loadoutId, "repair station loadoutId");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NamedRepairStation)) return false;
        NamedRepairStation that = (NamedRepairStation) other;
        return id.equals(that.id) && displayName.equals(that.displayName)
            && locationId.equals(that.locationId)
            && loadoutId.equals(that.loadoutId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, locationId, loadoutId);
    }
}
