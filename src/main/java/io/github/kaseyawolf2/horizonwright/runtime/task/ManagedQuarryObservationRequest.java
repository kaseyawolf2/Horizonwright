package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryIntent;

/** One revision-, epoch-, and frontier-bound infrastructure observation. */
public final class ManagedQuarryObservationRequest {

    private final String taskId;
    private final int dimensionId;
    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final ManagedQuarryIntent intent;

    public ManagedQuarryObservationRequest(String taskId, int dimensionId, long taskRevision, long actionEpoch,
        String geometryKey, ExcavationFrontier startFrontier, ManagedQuarryIntent intent) {
        this.taskId = requireText(taskId, "taskId");
        this.dimensionId = dimensionId;
        if (taskRevision < 1L) throw new IllegalArgumentException("taskRevision must be positive");
        if (actionEpoch < 1L) throw new IllegalArgumentException("actionEpoch must be positive");
        this.taskRevision = taskRevision;
        this.actionEpoch = actionEpoch;
        this.geometryKey = requireText(geometryKey, "geometryKey");
        this.startFrontier = Objects.requireNonNull(startFrontier, "startFrontier");
        this.intent = Objects.requireNonNull(intent, "intent");
    }

    public String getTaskId() {
        return taskId;
    }

    public int getDimensionId() {
        return dimensionId;
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

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
