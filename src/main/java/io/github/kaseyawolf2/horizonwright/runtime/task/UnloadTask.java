package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** First-class verified unload task bound to one named loadout and storage destination. */
public final class UnloadTask {

    public static final String TYPE = "unload";
    static final String LOADOUT_ID = "loadoutId";
    static final String STORAGE_ID = "storageId";

    private UnloadTask() {}

    public static TaskSpec create(String id, String loadoutId, String storageId) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(LOADOUT_ID, requireText(loadoutId, LOADOUT_ID));
        parameters.put(STORAGE_ID, requireText(storageId, STORAGE_ID));
        return new TaskSpec(id, TYPE, "Unload at " + storageId.trim(), TaskLane.CHORE, parameters);
    }

    static String loadoutId(TaskSpec spec) {
        requireType(spec);
        return requireText(
            spec.getParameters()
                .get(LOADOUT_ID),
            LOADOUT_ID);
    }

    static String storageId(TaskSpec spec) {
        requireType(spec);
        return requireText(
            spec.getParameters()
                .get(STORAGE_ID),
            STORAGE_ID);
    }

    private static void requireType(TaskSpec spec) {
        if (spec == null || !TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("unload task specification is required");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("missing unload task parameter " + field);
        }
        return value.trim();
    }
}
