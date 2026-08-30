package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Immutable persisted progress owned by a task runner. */
public final class TaskCheckpoint {

    private static final TaskCheckpoint EMPTY = new TaskCheckpoint(0L, Collections.<String, String>emptyMap());

    private final long revision;
    private final Map<String, String> values;

    public TaskCheckpoint(long revision, Map<String, String> values) {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        this.revision = revision;
        this.values = TaskSpec.immutableStrings(values, "values");
    }

    public static TaskCheckpoint empty() {
        return EMPTY;
    }

    public long getRevision() {
        return revision;
    }

    public Map<String, String> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskCheckpoint)) {
            return false;
        }
        TaskCheckpoint that = (TaskCheckpoint) other;
        return revision == that.revision && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revision, values);
    }
}
