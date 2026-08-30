package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable lane-organized view of all nonterminal tasks. */
public final class QueueSnapshot {

    private final Map<TaskLane, List<TaskSnapshot>> lanes;

    QueueSnapshot(Map<TaskLane, List<TaskSnapshot>> lanes) {
        EnumMap<TaskLane, List<TaskSnapshot>> copy = new EnumMap<>(TaskLane.class);
        for (TaskLane lane : TaskLane.values()) {
            List<TaskSnapshot> tasks = lanes.get(lane);
            if (tasks == null) {
                tasks = Collections.emptyList();
            }
            copy.put(lane, Collections.unmodifiableList(new ArrayList<>(tasks)));
        }
        this.lanes = Collections.unmodifiableMap(copy);
    }

    public List<TaskSnapshot> getLane(TaskLane lane) {
        if (lane == null) {
            throw new IllegalArgumentException("lane must not be null");
        }
        return lanes.get(lane);
    }

    public Map<TaskLane, List<TaskSnapshot>> getLanes() {
        return lanes;
    }
}
