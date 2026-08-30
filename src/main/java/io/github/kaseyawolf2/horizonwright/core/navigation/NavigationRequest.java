package io.github.kaseyawolf2.horizonwright.core.navigation;

public final class NavigationRequest {

    private final String requestId;
    private final long actionEpoch;
    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;
    private final int tolerance;

    public NavigationRequest(String requestId, long actionEpoch, int dimensionId, int x, int y, int z, int tolerance) {
        if (requestId == null || requestId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        if (tolerance < 0) {
            throw new IllegalArgumentException("tolerance must not be negative");
        }
        this.requestId = requestId.trim();
        this.actionEpoch = actionEpoch;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tolerance = tolerance;
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
}
