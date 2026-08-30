package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationIntent;

/** One fingerprint-, frontier-, revision-, and epoch-bound excavation action. */
public final class ExcavationActionRequest {

    private final String requestId;
    private final String taskId;
    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final ExcavationIntent intent;

    ExcavationActionRequest(String requestId, String taskId, long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, ExcavationIntent intent) {
        this.requestId = requireText(requestId, "requestId");
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
        this.intent = Objects.requireNonNull(intent, "intent");
    }

    public String getRequestId() {
        return requestId;
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

    public ExcavationIntent getIntent() {
        return intent;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
