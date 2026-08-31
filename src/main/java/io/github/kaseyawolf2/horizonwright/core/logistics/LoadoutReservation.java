package io.github.kaseyawolf2.horizonwright.core.logistics;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;

/** Durable reservation matched by bounded item identity rather than implementation class. */
public final class LoadoutReservation {

    private final String id;
    private final LoadoutRole role;
    private final String itemId;
    private final int metadata;
    private final String dataHash;
    private final int minimumCount;

    public LoadoutReservation(String id, LoadoutRole role, String itemId, int metadata, String dataHash,
        int minimumCount) {
        this.id = requireText(id, "id");
        this.role = role;
        this.itemId = requireText(itemId, "itemId");
        if (metadata < -1) {
            throw new IllegalArgumentException("metadata must be -1 (any) or non-negative");
        }
        this.metadata = metadata;
        this.dataHash = normalizeOptional(dataHash);
        if (minimumCount <= 0) {
            throw new IllegalArgumentException("minimumCount must be positive");
        }
        this.minimumCount = minimumCount;
        validate();
    }

    public String getId() {
        return id;
    }

    public LoadoutRole getRole() {
        return role;
    }

    public String getItemId() {
        return itemId;
    }

    public int getMetadata() {
        return metadata;
    }

    public String getDataHash() {
        return dataHash;
    }

    public int getMinimumCount() {
        return minimumCount;
    }

    public boolean matches(ItemFingerprint item) {
        return item != null && itemId.equals(item.getItemId())
            && (metadata == -1 || metadata == item.getMetadata())
            && (dataHash == null || dataHash.equals(item.getDataHash()));
    }

    public void validate() {
        requireText(id, "loadout reservation id");
        if (role == null) {
            throw new IllegalArgumentException("loadout reservation role must not be null");
        }
        requireText(itemId, "loadout reservation itemId");
        if (metadata < -1 || minimumCount <= 0) {
            throw new IllegalArgumentException("loadout reservation metadata or minimumCount is invalid");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LoadoutReservation)) {
            return false;
        }
        LoadoutReservation that = (LoadoutReservation) other;
        return metadata == that.metadata && minimumCount == that.minimumCount
            && id.equals(that.id)
            && role == that.role
            && itemId.equals(that.itemId)
            && Objects.equals(dataHash, that.dataHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, role, itemId, metadata, dataHash, minimumCount);
    }
}
