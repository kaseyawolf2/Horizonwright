package io.github.kaseyawolf2.horizonwright.core.logistics;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;

/** Bounded item identity used by a storage destination filter. */
public final class StorageItemRule {

    private final String itemId;
    private final int metadata;
    private final String dataHash;

    public StorageItemRule(String itemId, int metadata, String dataHash) {
        if (itemId == null || itemId.trim()
            .isEmpty() || metadata < -1) {
            throw new IllegalArgumentException("itemId and metadata (-1 or non-negative) are required");
        }
        this.itemId = itemId.trim();
        this.metadata = metadata;
        this.dataHash = dataHash == null || dataHash.trim()
            .isEmpty() ? null : dataHash.trim();
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

    public boolean matches(ItemFingerprint item) {
        return item != null && itemId.equals(item.getItemId())
            && (metadata == -1 || metadata == item.getMetadata())
            && (dataHash == null || dataHash.equals(item.getDataHash()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StorageItemRule)) return false;
        StorageItemRule that = (StorageItemRule) other;
        return metadata == that.metadata && itemId.equals(that.itemId) && Objects.equals(dataHash, that.dataHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, metadata, dataHash);
    }
}
