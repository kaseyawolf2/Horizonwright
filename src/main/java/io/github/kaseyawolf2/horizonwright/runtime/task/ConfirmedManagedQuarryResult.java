package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryIntent;

/** Post-action proof that the exact approved material occupies one infrastructure position. */
public final class ConfirmedManagedQuarryResult {

    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final ManagedQuarryIntent intent;
    private final String observedFingerprint;

    public ConfirmedManagedQuarryResult(long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, ManagedQuarryIntent intent, String observedFingerprint) {
        if (taskRevision < 1L) throw new IllegalArgumentException("taskRevision must be positive");
        if (actionEpoch < 1L) throw new IllegalArgumentException("actionEpoch must be positive");
        this.taskRevision = taskRevision;
        this.actionEpoch = actionEpoch;
        this.geometryKey = requireText(geometryKey, "geometryKey");
        this.startFrontier = Objects.requireNonNull(startFrontier, "startFrontier");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.observedFingerprint = requireText(observedFingerprint, "observedFingerprint");
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
