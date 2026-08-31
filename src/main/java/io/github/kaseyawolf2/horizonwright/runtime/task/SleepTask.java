package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.ScheduledTaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Persistence-friendly specification for one normal bed sleep attempt. */
public final class SleepTask {

    public static final String TYPE = "sleep";
    static final String BED_LOCATION_ID = "bedLocationId";

    private SleepTask() {}

    public static TaskSpec once(String taskId, String bedLocationId) {
        return scheduled(bedLocationId).instantiate(taskId);
    }

    public static ScheduledTaskSpec scheduled(String bedLocationId) {
        String bed = required(bedLocationId);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(BED_LOCATION_ID, bed);
        return new ScheduledTaskSpec(TYPE, "Sleep at: " + bed, TaskLane.CHORE, parameters);
    }

    static String bedLocationId(TaskSpec spec) {
        if (spec == null || !TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("a sleep task specification is required");
        }
        return required(
            spec.getParameters()
                .get(BED_LOCATION_ID));
    }

    private static String required(String value) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("bed location id is required");
        }
        return value.trim();
    }
}
