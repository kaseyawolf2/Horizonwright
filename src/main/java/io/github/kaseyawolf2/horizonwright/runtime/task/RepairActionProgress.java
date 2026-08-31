package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Optional;

public final class RepairActionProgress {

    private final String requestId;
    private final RepairActionState state;
    private final String detail;
    private final RepairActionConfirmation confirmation;

    public RepairActionProgress(String requestId, RepairActionState state, String detail,
        RepairActionConfirmation confirmation) {
        if (requestId == null || requestId.trim()
            .isEmpty() || state == null || (state == RepairActionState.CONFIRMED) != (confirmation != null)) {
            throw new IllegalArgumentException("repair progress confirmation must match its state");
        }
        this.requestId = requestId.trim();
        this.state = state;
        this.detail = detail == null ? "" : detail;
        this.confirmation = confirmation;
    }

    public String getRequestId() {
        return requestId;
    }

    public RepairActionState getState() {
        return state;
    }

    public String getDetail() {
        return detail;
    }

    public Optional<RepairActionConfirmation> getConfirmation() {
        return Optional.ofNullable(confirmation);
    }
}
