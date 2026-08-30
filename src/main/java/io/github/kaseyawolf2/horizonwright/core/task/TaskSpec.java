package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, persistence-friendly description of a task. */
public final class TaskSpec {

    private final String id;
    private final String type;
    private final String displayName;
    private final TaskLane lane;
    private final Map<String, String> parameters;

    public TaskSpec(String id, String type, String displayName, TaskLane lane, Map<String, String> parameters) {
        this.id = requireText(id, "id");
        this.type = requireText(type, "type");
        this.displayName = requireText(displayName, "displayName");
        if (lane == null) {
            throw new IllegalArgumentException("lane must not be null");
        }
        this.lane = lane;
        this.parameters = immutableStrings(parameters, "parameters");
    }

    public static TaskSpec of(String id, String type, String displayName, TaskLane lane) {
        return new TaskSpec(id, type, displayName, lane, Collections.<String, String>emptyMap());
    }

    public String getId() {
        return id;
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
        if (!(other instanceof TaskSpec)) {
            return false;
        }
        TaskSpec that = (TaskSpec) other;
        return id.equals(that.id) && type.equals(that.type)
            && displayName.equals(that.displayName)
            && lane == that.lane
            && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, displayName, lane, parameters);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    static Map<String, String> immutableStrings(Map<String, String> source, String field) {
        if (source == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), field + " key");
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(field + " values must not be null");
            }
            copy.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
