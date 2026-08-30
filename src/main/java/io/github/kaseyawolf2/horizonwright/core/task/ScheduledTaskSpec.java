package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Immutable task template instantiated with a scheduler-owned run identifier. */
public final class ScheduledTaskSpec {

    private final String type;
    private final String displayName;
    private final TaskLane lane;
    private final Map<String, String> parameters;

    public ScheduledTaskSpec(String type, String displayName, TaskLane lane, Map<String, String> parameters) {
        this.type = requireText(type, "type");
        this.displayName = requireText(displayName, "displayName");
        if (lane == null) {
            throw new IllegalArgumentException("lane must not be null");
        }
        this.lane = lane;
        this.parameters = TaskSpec.immutableStrings(parameters, "parameters");
    }

    public static ScheduledTaskSpec of(String type, String displayName, TaskLane lane) {
        return new ScheduledTaskSpec(type, displayName, lane, Collections.<String, String>emptyMap());
    }

    public TaskSpec instantiate(String taskId) {
        return new TaskSpec(taskId, type, displayName, lane, parameters);
    }

    public String getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TaskLane getLane() {
        return lane;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScheduledTaskSpec)) {
            return false;
        }
        ScheduledTaskSpec that = (ScheduledTaskSpec) other;
        return type.equals(that.type) && displayName.equals(that.displayName)
            && lane == that.lane
            && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, displayName, lane, parameters);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
