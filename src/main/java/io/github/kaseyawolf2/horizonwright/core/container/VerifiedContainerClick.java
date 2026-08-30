package io.github.kaseyawolf2.horizonwright.core.container;

/** One non-idempotent click with exact snapshots on both sides. */
public final class VerifiedContainerClick {

    private final String clickId;
    private final int slot;
    private final int mouseButton;
    private final int clickMode;
    private final ContainerSnapshot expectedBefore;
    private final ContainerSnapshot expectedAfter;

    public VerifiedContainerClick(String clickId, int slot, int mouseButton, int clickMode,
        ContainerSnapshot expectedBefore, ContainerSnapshot expectedAfter) {
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
        this.clickId = clickId.trim();
        this.slot = slot;
        this.mouseButton = mouseButton;
        this.clickMode = clickMode;
        this.expectedBefore = expectedBefore;
        this.expectedAfter = expectedAfter;
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
