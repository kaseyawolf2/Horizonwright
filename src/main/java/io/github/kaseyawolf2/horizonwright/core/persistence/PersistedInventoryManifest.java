package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryStack;

/** Durable, constructor-validated form of an inventory manifest. */
public final class PersistedInventoryManifest {

    private final int slotCount;
    private final List<PersistedInventoryStack> stacks;

    public PersistedInventoryManifest(int slotCount, List<PersistedInventoryStack> stacks) {
        this.slotCount = slotCount;
        this.stacks = stacks == null ? null : Collections.unmodifiableList(new ArrayList<>(stacks));
        validate();
    }

    public static PersistedInventoryManifest fromManifest(InventoryManifest manifest) {
        if (manifest == null) {
            return null;
        }
        List<PersistedInventoryStack> persisted = new ArrayList<>();
        for (InventoryStack stack : manifest.getStacks()) {
            persisted.add(
                new PersistedInventoryStack(stack.getItemFingerprint(), stack.getCount(), stack.getMaximumStackSize()));
        }
        return new PersistedInventoryManifest(manifest.getSlotCount(), persisted);
    }

    public InventoryManifest toManifest() {
        List<InventoryStack> restored = new ArrayList<>();
        for (PersistedInventoryStack stack : stacks) {
            restored.add(stack.toInventoryStack());
        }
        return new InventoryManifest(slotCount, restored);
    }

    public int getSlotCount() {
        return slotCount;
    }

    public List<PersistedInventoryStack> getStacks() {
        return stacks;
    }

    void validate() {
        if (slotCount < 0 || stacks == null || stacks.contains(null) || stacks.size() > slotCount) {
            throw new IllegalArgumentException("persisted inventory manifest has invalid slots or stacks");
        }
        for (PersistedInventoryStack stack : stacks) {
            stack.validate();
        }
        toManifest();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PersistedInventoryManifest)) {
            return false;
        }
        PersistedInventoryManifest that = (PersistedInventoryManifest) other;
        return slotCount == that.slotCount && stacks.equals(that.stacks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotCount, stacks);
    }

    public static final class PersistedInventoryStack {

        private final String itemFingerprint;
        private final int count;
        private final int maximumStackSize;

        public PersistedInventoryStack(String itemFingerprint, int count, int maximumStackSize) {
            this.itemFingerprint = PersistenceValidation.requireText(itemFingerprint, "itemFingerprint");
            this.count = count;
            this.maximumStackSize = maximumStackSize;
            validate();
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

        InventoryStack toInventoryStack() {
            return new InventoryStack(itemFingerprint, count, maximumStackSize);
        }

        void validate() {
            PersistenceValidation.requireText(itemFingerprint, "persisted inventory itemFingerprint");
            toInventoryStack();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PersistedInventoryStack)) {
                return false;
            }
            PersistedInventoryStack that = (PersistedInventoryStack) other;
            return count == that.count && maximumStackSize == that.maximumStackSize
                && itemFingerprint.equals(that.itemFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemFingerprint, count, maximumStackSize);
        }
    }
}
