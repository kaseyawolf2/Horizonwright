package io.github.kaseyawolf2.horizonwright.core.task;

/** Fixed scheduler lanes in descending priority order. */
public enum TaskLane {

    SAFETY,
    MANUAL,
    CHORE,
    FALLBACK;

    public boolean hasPriorityOver(TaskLane other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        return ordinal() < other.ordinal();
    }
}
