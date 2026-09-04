package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Optional;

/** Immutable progress for one managed-quarry infrastructure action. */
public final class ManagedQuarryActionProgress {

    private final String requestId;
    private final ExcavationActionState state;
    private final String detail;
    private final ConfirmedManagedQuarryResult confirmation;

    public ManagedQuarryActionProgress(String requestId, ExcavationActionState state, String detail,
        ConfirmedManagedQuarryResult confirmation) {
        this.requestId = requireText(requestId, "requestId");
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (state == ExcavationActionState.CONFIRMED && confirmation == null) {
            throw new IllegalArgumentException("confirmed progress requires post-action evidence");
        }
        if (state != ExcavationActionState.CONFIRMED && confirmation != null) {
            throw new IllegalArgumentException("only confirmed progress may carry evidence");
        }
        this.state = state;
        this.detail = detail == null ? "" : detail;
        this.confirmation = confirmation;
    }

    public String getRequestId() {
        return requestId;
    }

    public ExcavationActionState getState() {
        return state;
    }

    public String getDetail() {
        return detail;
    }

    public Optional<ConfirmedManagedQuarryResult> getConfirmation() {
        return Optional.ofNullable(confirmation);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
