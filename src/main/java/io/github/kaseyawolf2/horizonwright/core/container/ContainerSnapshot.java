package io.github.kaseyawolf2.horizonwright.core.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable client-thread capture used by the pure-core transaction verifier. */
public final class ContainerSnapshot {

    private final int windowId;
    private final String containerType;
    private final String slotLayout;
    private final long revision;
    private final List<ItemFingerprint> slots;
    private final ItemFingerprint cursor;

    public ContainerSnapshot(int windowId, String containerType, String slotLayout, long revision,
        List<ItemFingerprint> slots, ItemFingerprint cursor) {
        if (windowId < 0 || revision < 0L) {
            throw new IllegalArgumentException("windowId and revision must be non-negative");
        }
        if (containerType == null || containerType.trim()
            .isEmpty()
            || slotLayout == null
            || slotLayout.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("containerType and slotLayout must not be blank");
        }
        if (slots == null) {
            throw new IllegalArgumentException("slots must not be null; null entries represent empty slots");
        }
        this.windowId = windowId;
        this.containerType = containerType.trim();
        this.slotLayout = slotLayout.trim();
        this.revision = revision;
        this.slots = Collections.unmodifiableList(new ArrayList<ItemFingerprint>(slots));
        this.cursor = cursor;
    }

    public int getWindowId() {
        return windowId;
    }

    public String getContainerType() {
        return containerType;
    }

    public String getSlotLayout() {
        return slotLayout;
    }

    public long getRevision() {
        return revision;
    }

    public List<ItemFingerprint> getSlots() {
        return slots;
    }

    public ItemFingerprint getCursor() {
        return cursor;
    }

    public boolean sameIdentityAndLayout(ContainerSnapshot other) {
        return other != null && windowId == other.windowId
            && containerType.equals(other.containerType)
            && slotLayout.equals(other.slotLayout)
            && slots.size() == other.slots.size();
    }

    /** Bounded diagnostic containing item identities and hashes, never raw item NBT. */
    public String describeDifference(ContainerSnapshot observed) {
        if (!sameIdentityAndLayout(observed)) return "container identity or layout differs";
        StringBuilder differences = new StringBuilder();
        int count = 0;
        for (int slot = 0; slot < slots.size(); slot++) {
            if (Objects.equals(slots.get(slot), observed.slots.get(slot))) continue;
            if (count++ >= 4) {
                differences.append("; additional slots differ");
                break;
            }
            if (differences.length() > 0) differences.append("; ");
            differences.append("slot ")
                .append(slot)
                .append(" expected ")
                .append(describe(slots.get(slot)))
                .append(" observed ")
                .append(describe(observed.slots.get(slot)));
        }
        if (!Objects.equals(cursor, observed.cursor)) {
            if (differences.length() > 0) differences.append("; ");
            differences.append("cursor expected ")
                .append(describe(cursor))
                .append(" observed ")
                .append(describe(observed.cursor));
        }
        if (revision != observed.revision) differences.append("; snapshot revision differs");
        return differences.length() == 0 ? "none" : differences.toString();
    }

    private static String describe(ItemFingerprint item) {
        if (item == null) return "empty";
        String hash = item.getDataHash();
        return item.getItemId() + ":"
            + item.getMetadata()
            + " x"
            + item.getCount()
            + " NBT="
            + hash.substring(0, Math.min(hash.length(), 12));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContainerSnapshot)) {
            return false;
        }
        ContainerSnapshot that = (ContainerSnapshot) other;
        return windowId == that.windowId && revision == that.revision
            && containerType.equals(that.containerType)
            && slotLayout.equals(that.slotLayout)
            && slots.equals(that.slots)
            && Objects.equals(cursor, that.cursor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowId, containerType, slotLayout, revision, slots, cursor);
    }
}
