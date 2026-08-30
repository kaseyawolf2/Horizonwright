package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Objects;

/** Immutable structured explanation of why unattended execution cannot continue. */
public final class BlockedReason {

    private final BlockedCause cause;
    private final String detail;
    private final String location;
    private final int retryCount;
    private final String missingRequirement;
    private final String requiredUserAction;

    public BlockedReason(BlockedCause cause, String detail, String location, int retryCount, String missingRequirement,
        String requiredUserAction) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        this.cause = cause;
        this.detail = nullToEmpty(detail);
        this.location = nullToEmpty(location);
        this.retryCount = retryCount;
        this.missingRequirement = nullToEmpty(missingRequirement);
        this.requiredUserAction = nullToEmpty(requiredUserAction);
    }

    public static BlockedReason missingRequirement(String detail, String location, String missingRequirement,
        String requiredUserAction) {
        return new BlockedReason(
            BlockedCause.MISSING_REQUIREMENT,
            detail,
            location,
            0,
            missingRequirement,
            requiredUserAction);
    }

    public BlockedCause getCause() {
        return cause;
    }

    public String getDetail() {
        return detail;
    }

    public String getLocation() {
        return location;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getMissingRequirement() {
        return missingRequirement;
    }

    public String getRequiredUserAction() {
        return requiredUserAction;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockedReason)) {
            return false;
        }
        BlockedReason that = (BlockedReason) other;
        return retryCount == that.retryCount && cause == that.cause
            && detail.equals(that.detail)
            && location.equals(that.location)
            && missingRequirement.equals(that.missingRequirement)
            && requiredUserAction.equals(that.requiredUserAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cause, detail, location, retryCount, missingRequirement, requiredUserAction);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
