package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.base.LivestockSpecies;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduledTaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Typed specification for one bounded, freshly observed livestock chore. */
public final class HusbandryTask {

    public static final String TYPE = "husbandry-pass";
    static final String PEN_ID = "penId";
    static final String SPECIES = "species";
    static final String MINIMUM_ADULTS = "minimumAdults";
    static final String MAXIMUM_ADULTS = "maximumAdults";
    static final String MAXIMUM_ACTIONS = "maximumActions";

    private HusbandryTask() {}

    public static TaskSpec finitePass(String taskId, String penId, LivestockSpecies species, int minimumAdults,
        int maximumAdults, int maximumActions) {
        return scheduledPass(penId, species, minimumAdults, maximumAdults, maximumActions).instantiate(taskId);
    }

    public static ScheduledTaskSpec scheduledPass(String penId, LivestockSpecies species, int minimumAdults,
        int maximumAdults, int maximumActions) {
        String pen = required(penId, "pen id");
        if (species == null) throw new IllegalArgumentException("livestock species is required");
        if (minimumAdults < 2 || maximumAdults < minimumAdults) {
            throw new IllegalArgumentException("adult bounds must preserve a breeding pair");
        }
        if (maximumActions < 1 || maximumActions > 256) {
            throw new IllegalArgumentException("maximum actions must be from 1 to 256");
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(PEN_ID, pen);
        parameters.put(SPECIES, species.name());
        parameters.put(MINIMUM_ADULTS, Integer.toString(minimumAdults));
        parameters.put(MAXIMUM_ADULTS, Integer.toString(maximumAdults));
        parameters.put(MAXIMUM_ACTIONS, Integer.toString(maximumActions));
        return new ScheduledTaskSpec(
            TYPE,
            "Husbandry pass: " + pen + " / " + species.name(),
            TaskLane.CHORE,
            parameters);
    }

    public static String penId(TaskSpec spec) {
        requireType(spec);
        return penId(spec.getParameters());
    }

    public static String penId(ScheduledTaskSpec spec) {
        requireType(spec);
        return penId(spec.getParameters());
    }

    public static LivestockSpecies species(TaskSpec spec) {
        requireType(spec);
        return species(spec.getParameters());
    }

    public static LivestockSpecies species(ScheduledTaskSpec spec) {
        requireType(spec);
        return species(spec.getParameters());
    }

    private static LivestockSpecies species(Map<String, String> parameters) {
        try {
            return LivestockSpecies.valueOf(required(parameters.get(SPECIES), "species"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid livestock species", failure);
        }
    }

    public static int minimumAdults(TaskSpec spec) {
        requireType(spec);
        return integer(spec.getParameters(), MINIMUM_ADULTS, 2, 1_000);
    }

    public static int minimumAdults(ScheduledTaskSpec spec) {
        requireType(spec);
        return integer(spec.getParameters(), MINIMUM_ADULTS, 2, 1_000);
    }

    public static int maximumAdults(TaskSpec spec) {
        requireType(spec);
        int maximum = integer(spec.getParameters(), MAXIMUM_ADULTS, 2, 1_000);
        if (maximum < minimumAdults(spec)) throw new IllegalArgumentException("maximum adults is below minimum");
        return maximum;
    }

    public static int maximumAdults(ScheduledTaskSpec spec) {
        requireType(spec);
        int maximum = integer(spec.getParameters(), MAXIMUM_ADULTS, 2, 1_000);
        if (maximum < minimumAdults(spec)) throw new IllegalArgumentException("maximum adults is below minimum");
        return maximum;
    }

    public static int maximumActions(TaskSpec spec) {
        requireType(spec);
        return integer(spec.getParameters(), MAXIMUM_ACTIONS, 1, 256);
    }

    public static int maximumActions(ScheduledTaskSpec spec) {
        requireType(spec);
        return integer(spec.getParameters(), MAXIMUM_ACTIONS, 1, 256);
    }

    public static boolean isForPen(TaskSpec spec, String penId) {
        return spec != null && TYPE.equals(spec.getType())
            && penId != null
            && penId.trim()
                .equals(penId(spec));
    }

    public static boolean isForPen(ScheduledTaskSpec spec, String penId) {
        return spec != null && TYPE.equals(spec.getType())
            && penId != null
            && penId.trim()
                .equals(penId(spec));
    }

    private static int integer(Map<String, String> parameters, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(required(parameters.get(key), key));
            if (value < minimum || value > maximum) throw new IllegalArgumentException(key + " is out of range");
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(key + " must be a whole number", failure);
        }
    }

    private static void requireType(TaskSpec spec) {
        if (spec == null || !TYPE.equals(spec.getType()))
            throw new IllegalArgumentException("a husbandry-pass task is required");
    }

    private static void requireType(ScheduledTaskSpec spec) {
        if (spec == null || !TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("a scheduled husbandry-pass task is required");
        }
    }

    private static String penId(Map<String, String> parameters) {
        return required(parameters.get(PEN_ID), "pen id");
    }

    private static String required(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
