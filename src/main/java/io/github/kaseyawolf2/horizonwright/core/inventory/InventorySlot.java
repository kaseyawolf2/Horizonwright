package io.github.kaseyawolf2.horizonwright.core.inventory;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;

/** An observed slot. Capacity includes both the slot limit and the item's legal stack limit. */
public final class InventorySlot {

    private final int index;
    private final ItemFingerprint item;
    private final ToIntFunction<ItemFingerprint> capacity;
    private final Predicate<ItemFingerprint> accepts;
    private final int restrictionPriority;
    private final boolean extractable;

    public InventorySlot(int index, ItemFingerprint item, int stackLimit, Predicate<ItemFingerprint> accepts,
        int restrictionPriority, boolean extractable) {
        this(index, item, ignored -> stackLimit, accepts, restrictionPriority, extractable);
        if (stackLimit < 0) throw new IllegalArgumentException("Slot capacity must not be negative");
    }

    public InventorySlot(int index, ItemFingerprint item, ToIntFunction<ItemFingerprint> capacity,
        Predicate<ItemFingerprint> accepts, int restrictionPriority, boolean extractable) {
        if (index < 0) throw new IllegalArgumentException("Slot index must not be negative");
        this.index = index;
        this.item = item;
        this.capacity = Objects.requireNonNull(capacity, "capacity");
        this.accepts = Objects.requireNonNull(accepts, "accepts");
        this.restrictionPriority = restrictionPriority;
        this.extractable = extractable;
    }

    public int getIndex() {
        return index;
    }

    public ItemFingerprint getItem() {
        return item;
    }

    public int capacityFor(ItemFingerprint value) {
        return accepts.test(value) ? Math.max(0, capacity.applyAsInt(value)) : 0;
    }

    public boolean isFull(ItemFingerprint value) {
        return value != null && capacity.applyAsInt(value) > 0 && value.getCount() >= capacity.applyAsInt(value);
    }

    /** Higher priorities represent narrower filters and are filled before general-purpose slots. */
    public int getRestrictionPriority() {
        return restrictionPriority;
    }

    public boolean isExtractable() {
        return extractable;
    }
}
