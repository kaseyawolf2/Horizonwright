package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryIntent;

/** One infrastructure placement bound to the exact world evidence observed by the runner. */
public final class ManagedQuarryActionRequest {

    private final String requestId;
    private final ManagedQuarryObservationRequest observationRequest;
    private final ManagedQuarryIntent intent;
    private final String observedFingerprint;

    public ManagedQuarryActionRequest(String requestId, ManagedQuarryObservationRequest observationRequest,
        ManagedQuarryIntent intent, String observedFingerprint) {
        this.requestId = requireText(requestId, "requestId");
        this.observationRequest = Objects.requireNonNull(observationRequest, "observationRequest");
        this.intent = Objects.requireNonNull(intent, "intent");
        if (!observationRequest.getIntent()
            .equals(intent)) throw new IllegalArgumentException("intent must match the observation request");
        this.observedFingerprint = requireText(observedFingerprint, "observedFingerprint");
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTaskId() {
        return observationRequest.getTaskId();
    }

    public int getDimensionId() {
        return observationRequest.getDimensionId();
    }

    public long getTaskRevision() {
        return observationRequest.getTaskRevision();
    }

    public long getActionEpoch() {
        return observationRequest.getActionEpoch();
    }

    public String getGeometryKey() {
        return observationRequest.getGeometryKey();
    }

    public ExcavationFrontier getStartFrontier() {
        return observationRequest.getStartFrontier();
    }

    public ManagedQuarryIntent getIntent() {
        return intent;
    }

    public String getObservedFingerprint() {
        return observedFingerprint;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
