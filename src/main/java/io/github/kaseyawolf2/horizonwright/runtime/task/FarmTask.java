package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.ScheduledTaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Persistence-friendly specification for one finite pass over a named farm plot. */
public final class FarmTask {

    public static final String TYPE = "farm-pass";
    static final String PLOT_ID = "plotId";
    static final String MINIMUM_SEED_RESERVE = "minimumSeedReserve";

    private FarmTask() {}

    public static TaskSpec finitePass(String taskId, String plotId, int minimumSeedReserve) {
        return scheduledPass(plotId, minimumSeedReserve).instantiate(taskId);
    }

    public static ScheduledTaskSpec scheduledPass(String plotId, int minimumSeedReserve) {
        String plot = required(plotId, "plot id");
        if (minimumSeedReserve < 0) throw new IllegalArgumentException("minimum seed reserve must not be negative");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(PLOT_ID, plot);
        parameters.put(MINIMUM_SEED_RESERVE, Integer.toString(minimumSeedReserve));
        return new ScheduledTaskSpec(TYPE, "Farm pass: " + plot, TaskLane.CHORE, parameters);
    }

    static String plotId(TaskSpec spec) {
        requireType(spec);
        return required(
            spec.getParameters()
                .get(PLOT_ID),
            "plot id");
    }

    static int minimumSeedReserve(TaskSpec spec) {
        requireType(spec);
        String value = required(
            spec.getParameters()
                .get(MINIMUM_SEED_RESERVE),
            "minimum seed reserve");
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new IllegalArgumentException("minimum seed reserve must not be negative");
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("minimum seed reserve must be a whole number", failure);
        }
    }

    public static boolean isForPlot(TaskSpec spec, String plotId) {
        return spec != null && TYPE.equals(spec.getType()) && samePlot(spec.getParameters(), plotId);
    }

    public static boolean isForPlot(ScheduledTaskSpec spec, String plotId) {
        return spec != null && TYPE.equals(spec.getType()) && samePlot(spec.getParameters(), plotId);
    }

    private static boolean samePlot(Map<String, String> parameters, String plotId) {
        return plotId != null && plotId.trim()
            .equals(parameters.get(PLOT_ID));
    }

    private static void requireType(TaskSpec spec) {
        if (spec == null || !TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("a farm-pass task specification is required");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
