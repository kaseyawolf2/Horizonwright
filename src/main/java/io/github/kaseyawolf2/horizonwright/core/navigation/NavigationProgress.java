package io.github.kaseyawolf2.horizonwright.core.navigation;

public final class NavigationProgress {

    private final String requestId;
    private final long actionEpoch;
    private final NavigationState state;
    private final String detail;

    public NavigationProgress(String requestId, long actionEpoch, NavigationState state, String detail) {
        if (requestId == null || requestId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.requestId = requestId.trim();
        this.actionEpoch = actionEpoch;
        this.state = state;
        this.detail = detail == null ? "" : detail;
    }

    public String getRequestId() {
        return requestId;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public NavigationState getState() {
        return state;
    }

    public String getDetail() {
        return detail;
    }
}
