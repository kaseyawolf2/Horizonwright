package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Immutable status of one outstanding verified unload transaction. */
public final class UnloadActionProgress {

    private final String requestId;
    private final UnloadActionState state;
    private final String detail;

    public UnloadActionProgress(String requestId, UnloadActionState state, String detail) {
        if (requestId == null || requestId.trim()
            .isEmpty() || state == null) {
            throw new IllegalArgumentException("requestId and state are required");
        }
        this.requestId = requestId.trim();
        this.state = state;
        this.detail = detail == null ? "" : detail;
    }

    public String getRequestId() {
        return requestId;
    }

    public UnloadActionState getState() {
        return state;
    }

    public String getDetail() {
        return detail;
    }
}
