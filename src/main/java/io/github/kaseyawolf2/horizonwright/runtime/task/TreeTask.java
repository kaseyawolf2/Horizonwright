package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.ScheduledTaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Persistence-friendly specification for one bounded pass over a named tree farm. */
public final class TreeTask {

    public static final String TYPE = "tree-pass";
    static final String AREA_ID = "areaId";
    static final String MINIMUM_SAPLING_RESERVE = "minimumSaplingReserve";

    private TreeTask() {}

    public static TaskSpec finitePass(String taskId, String areaId, int minimumSaplingReserve) {
        return scheduledPass(areaId, minimumSaplingReserve).instantiate(taskId);
    }

    public static ScheduledTaskSpec scheduledPass(String areaId, int minimumSaplingReserve) {
        String area = required(areaId, "tree-farm area id");
        if (minimumSaplingReserve < 0) {
            throw new IllegalArgumentException("minimum sapling reserve must not be negative");
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(AREA_ID, area);
        parameters.put(MINIMUM_SAPLING_RESERVE, Integer.toString(minimumSaplingReserve));
        return new ScheduledTaskSpec(TYPE, "Tree pass: " + area, TaskLane.CHORE, parameters);
    }

    static String areaId(TaskSpec spec) {
        requireType(spec);
        return required(
            spec.getParameters()
                .get(AREA_ID),
            "tree-farm area id");
    }

    static int minimumSaplingReserve(TaskSpec spec) {
        requireType(spec);
        String value = required(
            spec.getParameters()
                .get(MINIMUM_SAPLING_RESERVE),
            "minimum sapling reserve");
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new IllegalArgumentException("minimum sapling reserve must not be negative");
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("minimum sapling reserve must be a whole number", failure);
        }
    }

    private static void requireType(TaskSpec spec) {
        if (spec == null || !TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("a tree-pass task specification is required");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
