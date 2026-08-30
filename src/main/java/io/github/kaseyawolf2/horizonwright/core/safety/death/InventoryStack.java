package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Objects;

/** Observable stack represented by a stable item-and-NBT fingerprint. */
public final class InventoryStack {

    private final String itemFingerprint;
    private final int count;
    private final int maximumStackSize;

    public InventoryStack(String itemFingerprint, int count, int maximumStackSize) {
        this.itemFingerprint = ConnectionIdentity.requireText(itemFingerprint, "itemFingerprint");
        if (maximumStackSize <= 0) {
            throw new IllegalArgumentException("maximumStackSize must be positive");
        }
        if (count <= 0 || count > maximumStackSize) {
            throw new IllegalArgumentException("count must be positive and no greater than maximumStackSize");
        }
        this.count = count;
        this.maximumStackSize = maximumStackSize;
    }

    public String getItemFingerprint() {
        return itemFingerprint;
    }

    public int getCount() {
        return count;
    }

    public int getMaximumStackSize() {
        return maximumStackSize;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InventoryStack)) {
            return false;
        }
        InventoryStack that = (InventoryStack) other;
        return count == that.count && maximumStackSize == that.maximumStackSize
            && itemFingerprint.equals(that.itemFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemFingerprint, count, maximumStackSize);
    }
}
