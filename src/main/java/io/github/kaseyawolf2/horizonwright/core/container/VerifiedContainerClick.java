package io.github.kaseyawolf2.horizonwright.core.container;

/** One non-idempotent click with explicit snapshot verification on both sides. */
public final class VerifiedContainerClick {

    private final int extractionStorageSlots;
    private final String clickId;
    private final int slot;
    private final int mouseButton;
    private final int clickMode;
    private final ContainerSnapshot expectedBefore;
    private final ContainerSnapshot expectedAfter;

    public VerifiedContainerClick(String clickId, int slot, int mouseButton, int clickMode,
        ContainerSnapshot expectedBefore, ContainerSnapshot expectedAfter) {
        this(clickId, slot, mouseButton, clickMode, expectedBefore, expectedAfter, 0);
    }

    private VerifiedContainerClick(String clickId, int slot, int mouseButton, int clickMode,
        ContainerSnapshot expectedBefore, ContainerSnapshot expectedAfter, int extractionStorageSlots) {
        if (clickId == null || clickId.trim()
            .isEmpty() || expectedBefore == null || expectedAfter == null) {
            throw new IllegalArgumentException("clickId and snapshots are required");
        }
        if (slot < -999 || mouseButton < 0 || clickMode < 0) {
            throw new IllegalArgumentException("invalid click parameters");
        }
        if (!expectedBefore.sameIdentityAndLayout(expectedAfter)) {
            throw new IllegalArgumentException("click snapshots must describe the same container layout");
        }
        if (expectedAfter.getRevision() <= expectedBefore.getRevision()) {
            throw new IllegalArgumentException("the expected after snapshot must advance the revision");
        }
        this.extractionStorageSlots = extractionStorageSlots;
        this.clickId = clickId.trim();
        this.slot = slot;
        this.mouseButton = mouseButton;
        this.clickMode = clickMode;
        this.expectedBefore = expectedBefore;
        this.expectedAfter = expectedAfter;
    }

    /**
     * Opt in only for a player-to-storage quick move. The transaction must contain one
     * click so the remaining cargo is replanned after storage extraction.
     */
    public VerifiedContainerClick allowingStorageExtraction(int storageSlots) {
        if (storageSlots <= 0 || storageSlots >= expectedBefore.getSlots()
            .size()
            || slot < storageSlots
            || slot >= expectedBefore.getSlots()
                .size()
            || clickMode != 1
            || mouseButton != 0
            || expectedBefore.getCursor() != null
            || expectedAfter.getCursor() != null) {
            throw new IllegalArgumentException("extraction tolerance requires a player-to-storage quick move");
        }
        ItemFingerprint source = expectedBefore.getSlots()
            .get(slot);
        ItemFingerprint remainder = expectedAfter.getSlots()
            .get(slot);
        if (source == null || (remainder != null
            && (!source.hasSameIdentity(remainder) || remainder.getCount() >= source.getCount()))) {
            throw new IllegalArgumentException("the unload click must reduce its source stack");
        }
        for (int index = storageSlots; index < expectedBefore.getSlots()
            .size(); index++) {
            if (index != slot && !java.util.Objects.equals(
                expectedBefore.getSlots()
                    .get(index),
                expectedAfter.getSlots()
                    .get(index))) {
                throw new IllegalArgumentException("the unload click must preserve other player slots");
            }
        }
        return new VerifiedContainerClick(
            clickId,
            slot,
            mouseButton,
            clickMode,
            expectedBefore,
            expectedAfter,
            storageSlots);
    }

    public int getExtractionStorageSlots() {
        return extractionStorageSlots;
    }

    public boolean matchesBefore(ContainerSnapshot observed) {
        return extractionStorageSlots == 0 ? expectedBefore.equals(observed)
            : StorageExtractionSnapshot.matches(expectedBefore, observed, extractionStorageSlots, slot, false);
    }

    public boolean matchesAfter(ContainerSnapshot observed) {
        return extractionStorageSlots == 0 ? expectedAfter.equals(observed)
            : StorageExtractionSnapshot.matches(expectedAfter, observed, extractionStorageSlots, slot, true);
    }

    public String getClickId() {
        return clickId;
    }

    public int getSlot() {
        return slot;
    }

    public int getMouseButton() {
        return mouseButton;
    }

    public int getClickMode() {
        return clickMode;
    }

    public ContainerSnapshot getExpectedBefore() {
        return expectedBefore;
    }

    public ContainerSnapshot getExpectedAfter() {
        return expectedAfter;
    }
}
