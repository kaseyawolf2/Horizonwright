package io.github.kaseyawolf2.horizonwright.core.container;

import java.util.Objects;

/** A bounded item identity that deliberately stores only a hash of any item data. */
public final class ItemFingerprint {

    private final String itemId;
    private final int metadata;
    private final String dataHash;
    private final int count;

    public ItemFingerprint(String itemId, int metadata, String dataHash, int count) {
        if (itemId == null || itemId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (metadata < 0 || count <= 0) {
            throw new IllegalArgumentException("metadata must be non-negative and count must be positive");
        }
        if (dataHash == null || dataHash.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("dataHash must not be blank");
        }
        this.itemId = itemId.trim();
        this.metadata = metadata;
        this.dataHash = dataHash.trim();
        this.count = count;
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

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemFingerprint)) {
            return false;
        }
        ItemFingerprint that = (ItemFingerprint) other;
        return metadata == that.metadata && count == that.count
            && itemId.equals(that.itemId)
            && dataHash.equals(that.dataHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, metadata, dataHash, count);
    }

    @Override
    public String toString() {
        return itemId + ":" + metadata + "#" + dataHash + "x" + count;
    }
}
