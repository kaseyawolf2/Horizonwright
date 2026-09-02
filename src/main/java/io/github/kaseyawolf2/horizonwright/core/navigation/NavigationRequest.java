package io.github.kaseyawolf2.horizonwright.core.navigation;

import java.util.concurrent.TimeUnit;

public final class NavigationRequest {

    public static final int MIN_Y = 0;
    public static final int MAX_Y = 255;
    public static final int MAX_ABS_COORDINATE = 29_999_984;
    public static final int MAX_TOLERANCE = 8;
    public static final long MAX_RUNTIME_NANOS = TimeUnit.MINUTES.toNanos(5L);

    private final String requestId;
    private final long actionEpoch;
    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;
    private final int tolerance;
    private final NavigationGoalKind goalKind;
    private final long createdAtNanos;
    private final long deadlineNanos;

    public NavigationRequest(String requestId, long actionEpoch, int dimensionId, int x, int y, int z, int tolerance) {
        this(requestId, actionEpoch, dimensionId, x, y, z, tolerance, System.nanoTime(), MAX_RUNTIME_NANOS);
    }

    public NavigationRequest(String requestId, long actionEpoch, int dimensionId, int x, int y, int z, int tolerance,
        long createdAtNanos, long timeoutNanos) {
        this(
            requestId,
            actionEpoch,
            dimensionId,
            x,
            y,
            z,
            tolerance,
            createdAtNanos,
            timeoutNanos,
            NavigationGoalKind.RANGE);
    }

    public static NavigationRequest adjacentTo(String requestId, long actionEpoch, int dimensionId, int x, int y, int z,
        long createdAtNanos, long timeoutNanos) {
        return new NavigationRequest(
            requestId,
            actionEpoch,
            dimensionId,
            x,
            y,
            z,
            0,
            createdAtNanos,
            timeoutNanos,
            NavigationGoalKind.ADJACENT);
    }

    private NavigationRequest(String requestId, long actionEpoch, int dimensionId, int x, int y, int z, int tolerance,
        long createdAtNanos, long timeoutNanos, NavigationGoalKind goalKind) {
        if (requestId == null || requestId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        if (x < -MAX_ABS_COORDINATE || x > MAX_ABS_COORDINATE || z < -MAX_ABS_COORDINATE || z > MAX_ABS_COORDINATE) {
            throw new IllegalArgumentException("target is outside the supported world coordinate range");
        }
        if (y < MIN_Y || y > MAX_Y) {
            throw new IllegalArgumentException("target Y must be between " + MIN_Y + " and " + MAX_Y);
        }
        if (tolerance < 0 || tolerance > MAX_TOLERANCE) {
            throw new IllegalArgumentException("tolerance must be between 0 and " + MAX_TOLERANCE);
        }
        if (timeoutNanos <= 0L || timeoutNanos > MAX_RUNTIME_NANOS) {
            throw new IllegalArgumentException("navigation timeout is outside the supported range");
        }
        if (goalKind == null) throw new IllegalArgumentException("goalKind must not be null");
        this.requestId = requestId.trim();
        this.actionEpoch = actionEpoch;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tolerance = tolerance;
        this.goalKind = goalKind;
        this.createdAtNanos = createdAtNanos;
        this.deadlineNanos = saturatingAdd(createdAtNanos, timeoutNanos);
    }

    public String getRequestId() {
        return requestId;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getTolerance() {
        return tolerance;
    }

    public NavigationGoalKind getGoalKind() {
        return goalKind;
    }

    public long getCreatedAtNanos() {
        return createdAtNanos;
    }

    public long getDeadlineNanos() {
        return deadlineNanos;
    }

    public boolean isExpired(long nowNanos) {
        return nowNanos - deadlineNanos >= 0L;
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0L) {
            return Long.MAX_VALUE;
        }
        return result;
    }
}
