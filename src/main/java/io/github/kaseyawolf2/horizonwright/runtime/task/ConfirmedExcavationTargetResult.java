package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetResult;

/** Post-action confirmation for exactly one bound excavation request. */
public final class ConfirmedExcavationTargetResult {

    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final String observedFingerprint;
    private final ExcavationTargetResult targetResult;

    public ConfirmedExcavationTargetResult(long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, String observedFingerprint, ExcavationTargetResult targetResult) {
        if (taskRevision < 1L) {
            throw new IllegalArgumentException("taskRevision must be positive");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        this.taskRevision = taskRevision;
        this.actionEpoch = actionEpoch;
        this.geometryKey = requireText(geometryKey, "geometryKey");
        this.startFrontier = Objects.requireNonNull(startFrontier, "startFrontier");
        this.observedFingerprint = requireText(observedFingerprint, "observedFingerprint");
        this.targetResult = Objects.requireNonNull(targetResult, "targetResult");
    }

    public long getTaskRevision() {
        return taskRevision;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public String getGeometryKey() {
        return geometryKey;
    }

    public ExcavationFrontier getStartFrontier() {
        return startFrontier;
    }

    public String getObservedFingerprint() {
        return observedFingerprint;
    }

    public ExcavationTargetResult getTargetResult() {
        return targetResult;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
