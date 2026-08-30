package io.github.kaseyawolf2.horizonwright.testfixtures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FakeInventory {

    private final ItemStack[] slots;

    public FakeInventory(int slotCount) {
        if (slotCount < 1) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        this.slots = new ItemStack[slotCount];
    }

    private FakeInventory(Snapshot snapshot) {
        this.slots = new ItemStack[snapshot.size()];
        for (int slot = 0; slot < slots.length; slot++) {
            this.slots[slot] = snapshot.getSlot(slot)
                .orElse(null);
        }
    }

    public synchronized int size() {
        return slots.length;
    }

    public synchronized Optional<ItemStack> getSlot(int slot) {
        checkSlot(slot);
        return Optional.ofNullable(slots[slot]);
    }

    public synchronized void setSlot(int slot, ItemStack stack) {
        checkSlot(slot);
        slots[slot] = stack;
    }

    public synchronized void clearSlot(int slot) {
        setSlot(slot, null);
    }

    public synchronized void move(int fromSlot, int toSlot, int count) {
        checkSlot(fromSlot);
        checkSlot(toSlot);
        if (fromSlot == toSlot) {
            throw new IllegalArgumentException("source and destination slots must differ");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        ItemStack source = slots[fromSlot];
        if (source == null || source.getCount() < count) {
            throw new IllegalStateException("source slot does not contain enough items");
        }
        ItemStack destination = slots[toSlot];
        if (destination != null && !source.sameIdentity(destination)) {
            throw new IllegalStateException("destination contains a different item identity");
        }

        int remaining = source.getCount() - count;
        slots[fromSlot] = remaining == 0 ? null : source.withCount(remaining);
        slots[toSlot] = destination == null ? source.withCount(count)
            : destination.withCount(Math.addExact(destination.getCount(), count));
    }

    public synchronized Snapshot snapshot() {
        List<ItemStack> copy = new ArrayList<>(slots.length);
        Collections.addAll(copy, slots);
        return new Snapshot(copy);
    }

    public synchronized void restore(Snapshot snapshot) {
        if (snapshot == null || snapshot.size() != slots.length) {
            throw new IllegalArgumentException("snapshot has the wrong slot count");
        }
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = snapshot.getSlot(slot)
                .orElse(null);
        }
    }

    public static FakeInventory fromSnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return new FakeInventory(snapshot);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= slots.length) {
            throw new IndexOutOfBoundsException("slot " + slot + " outside 0.." + (slots.length - 1));
        }
    }

    public static final class Snapshot {

        private final List<ItemStack> slots;

        private Snapshot(List<ItemStack> slots) {
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
        }

        public int size() {
            return slots.size();
        }

        public Optional<ItemStack> getSlot(int slot) {
            if (slot < 0 || slot >= slots.size()) {
                throw new IndexOutOfBoundsException("slot " + slot + " outside snapshot");
            }
            return Optional.ofNullable(slots.get(slot));
        }

        public List<ItemStack> getSlots() {
            return slots;
        }

        @Override
        public boolean equals(Object candidate) {
            return this == candidate || candidate instanceof Snapshot && slots.equals(((Snapshot) candidate).slots);
        }

        @Override
        public int hashCode() {
            return slots.hashCode();
        }

        @Override
        public String toString() {
            return slots.toString();
        }
    }

    public static final class ItemStack {

        private final String registryName;
        private final int metadata;
        private final int count;
        private final String fingerprint;

        public ItemStack(String registryName, int metadata, int count, String fingerprint) {
            if (registryName == null || registryName.trim()
                .isEmpty()) {
                throw new IllegalArgumentException("registryName must not be blank");
            }
            if (metadata < 0 || count < 1) {
                throw new IllegalArgumentException("metadata must be nonnegative and count positive");
            }
            if (fingerprint == null || fingerprint.trim()
                .isEmpty()) {
                throw new IllegalArgumentException("fingerprint must not be blank");
            }
            this.registryName = registryName.trim();
            this.metadata = metadata;
            this.count = count;
            this.fingerprint = fingerprint.trim();
        }

        public String getRegistryName() {
            return registryName;
        }

        public int getMetadata() {
            return metadata;
        }

        public int getCount() {
            return count;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public ItemStack withCount(int newCount) {
            return new ItemStack(registryName, metadata, newCount, fingerprint);
        }

        public boolean sameIdentity(ItemStack other) {
            return other != null && metadata == other.metadata
                && registryName.equals(other.registryName)
                && fingerprint.equals(other.fingerprint);
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof ItemStack)) {
                return false;
            }
            ItemStack other = (ItemStack) candidate;
            return metadata == other.metadata && count == other.count
                && registryName.equals(other.registryName)
                && fingerprint.equals(other.fingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(registryName, metadata, count, fingerprint);
        }

        @Override
        public String toString() {
            return registryName + ':' + metadata + 'x' + count + '#' + fingerprint;
        }
    }
}
