package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;

/** One revision-bound request for an immutable client-thread block observation. */
public final class ExcavationObservationRequest {

    private final String taskId;
    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final BlockPosition position;

    ExcavationObservationRequest(String taskId, long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, BlockPosition position) {
        this.taskId = requireText(taskId, "taskId");
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
        this.position = Objects.requireNonNull(position, "position");
    }

    public String getTaskId() {
        return taskId;
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

    public BlockPosition getPosition() {
        return position;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
