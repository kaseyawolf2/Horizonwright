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

    static String penId(TaskSpec spec) {
        requireType(spec);
        return required(
            spec.getParameters()
                .get(PEN_ID),
            "pen id");
    }

    static LivestockSpecies species(TaskSpec spec) {
        requireType(spec);
        try {
            return LivestockSpecies.valueOf(
                required(
                    spec.getParameters()
                        .get(SPECIES),
                    "species"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid livestock species", failure);
        }
    }

    static int minimumAdults(TaskSpec spec) {
        return integer(spec, MINIMUM_ADULTS, 2, 1_000);
    }

    static int maximumAdults(TaskSpec spec) {
        int maximum = integer(spec, MAXIMUM_ADULTS, 2, 1_000);
        if (maximum < minimumAdults(spec)) throw new IllegalArgumentException("maximum adults is below minimum");
        return maximum;
    }

    static int maximumActions(TaskSpec spec) {
        return integer(spec, MAXIMUM_ACTIONS, 1, 256);
    }

    private static int integer(TaskSpec spec, String key, int minimum, int maximum) {
        requireType(spec);
        try {
            int value = Integer.parseInt(
                required(
                    spec.getParameters()
                        .get(key),
                    key));
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

    private static String required(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
