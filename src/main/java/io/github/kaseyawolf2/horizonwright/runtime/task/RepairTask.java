package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Exact Tinkers repair task bound to one station and reserved inventory slot. */
public final class RepairTask {

    public static final String TYPE = "tinkers-repair";
    static final String STATION_ID = "stationId";
    static final String RESERVED_INVENTORY_SLOT = "reservedInventorySlot";
    static final String PREDICTED_WORK_DAMAGE = "predictedWorkDamage";

    private RepairTask() {}

    public static TaskSpec create(String id, String stationId, int reservedInventorySlot, int predictedWorkDamage) {
        if (reservedInventorySlot < 0 || predictedWorkDamage < 0) {
            throw new IllegalArgumentException("reserved slot and predicted work damage must be non-negative");
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(STATION_ID, requireText(stationId, STATION_ID));
        parameters.put(RESERVED_INVENTORY_SLOT, Integer.toString(reservedInventorySlot));
        parameters.put(PREDICTED_WORK_DAMAGE, Integer.toString(predictedWorkDamage));
        return new TaskSpec(id, TYPE, "Repair at " + stationId.trim(), TaskLane.CHORE, parameters);
    }

    static String stationId(TaskSpec spec) {
        requireType(spec);
        return requireText(
            spec.getParameters()
                .get(STATION_ID),
            STATION_ID);
    }

    static int reservedInventorySlot(TaskSpec spec) {
        return nonNegativeInteger(spec, RESERVED_INVENTORY_SLOT);
    }

    static int predictedWorkDamage(TaskSpec spec) {
        return nonNegativeInteger(spec, PREDICTED_WORK_DAMAGE);
    }

    private static int nonNegativeInteger(TaskSpec spec, String field) {
        requireType(spec);
        String value = requireText(
            spec.getParameters()
                .get(field),
            field);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new IllegalArgumentException(field + " must not be negative");
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid repair task parameter " + field, failure);
        }
    }

    private static void requireType(TaskSpec spec) {
        if (spec == null || !TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("Tinkers repair task specification is required");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("missing repair task parameter " + field);
        }
        return value.trim();
    }
}
