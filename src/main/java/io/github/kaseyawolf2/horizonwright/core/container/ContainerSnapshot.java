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
