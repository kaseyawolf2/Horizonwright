package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceValidation.IdentifiedPersistenceValue;

/** Named storage policy bound to one named world location. */
public final class NamedStorageEndpoint implements IdentifiedPersistenceValue {

    private final String id;
    private final String displayName;
    private final String locationId;
    private final StorageItemFilter destinationFilter;

    public NamedStorageEndpoint(String id, String displayName, String locationId, StorageItemFilter destinationFilter) {
        this.id = PersistenceValidation.requireStableId(id, "id");
        this.displayName = PersistenceValidation.requireText(displayName, "displayName");
        this.locationId = PersistenceValidation.requireStableId(locationId, "locationId");
        if (destinationFilter == null) {
            throw new IllegalArgumentException("destinationFilter must not be null");
        }
        this.destinationFilter = destinationFilter;
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

    public StorageItemFilter getDestinationFilter() {
        return destinationFilter;
    }

    void validate() {
        PersistenceValidation.requireStableId(id, "storage endpoint id");
        PersistenceValidation.requireText(displayName, "storage endpoint displayName");
        PersistenceValidation.requireStableId(locationId, "storage endpoint locationId");
        if (destinationFilter == null) {
            throw new IllegalArgumentException("storage endpoint destinationFilter must not be null");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NamedStorageEndpoint)) return false;
        NamedStorageEndpoint that = (NamedStorageEndpoint) other;
        return id.equals(that.id) && displayName.equals(that.displayName)
            && locationId.equals(that.locationId)
            && destinationFilter.equals(that.destinationFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, locationId, destinationFilter);
    }
}
