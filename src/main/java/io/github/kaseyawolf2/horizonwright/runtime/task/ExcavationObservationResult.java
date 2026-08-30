package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;

/** Immutable observation tagged with the exact request authority that produced it. */
public final class ExcavationObservationResult {

    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final ExcavationObservation observation;

    public ExcavationObservationResult(long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, ExcavationObservation observation) {
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
        this.observation = Objects.requireNonNull(observation, "observation");
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

    public ExcavationObservation getObservation() {
        return observation;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
