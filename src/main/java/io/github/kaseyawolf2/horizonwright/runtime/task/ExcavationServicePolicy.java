package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Map;
import java.util.Optional;

/** Durable named-service bindings used when an excavation reaches a safe service checkpoint. */
public final class ExcavationServicePolicy {

    private final String loadoutId;
    private final String storageId;
    private final String repairStationId;
    private final Integer reservedToolSlot;
    private final Integer predictedWorkDamage;

    private ExcavationServicePolicy(String loadoutId, String storageId, String repairStationId,
        Integer reservedToolSlot, Integer predictedWorkDamage) {
        this.loadoutId = normalizePair(loadoutId, storageId, "loadoutId", "storageId");
        this.storageId = normalizePair(storageId, loadoutId, "storageId", "loadoutId");
        this.repairStationId = normalizeRepair(repairStationId, reservedToolSlot, predictedWorkDamage);
        this.reservedToolSlot = normalizeNonNegative(reservedToolSlot, repairStationId, "reservedToolSlot");
        this.predictedWorkDamage = normalizeNonNegative(predictedWorkDamage, repairStationId, "predictedWorkDamage");
        if (this.reservedToolSlot != null && this.reservedToolSlot > 35) {
            throw new IllegalArgumentException("reservedToolSlot must be a player inventory slot from 0 to 35");
        }
        if (this.loadoutId == null && this.repairStationId == null) {
            throw new IllegalArgumentException("at least one excavation service binding is required");
        }
    }

    public static ExcavationServicePolicy unloadOnly(String loadoutId, String storageId) {
        return new ExcavationServicePolicy(loadoutId, storageId, null, null, null);
    }

    public static ExcavationServicePolicy repairOnly(String repairStationId, int reservedToolSlot,
        int predictedWorkDamage) {
        return new ExcavationServicePolicy(null, null, repairStationId, reservedToolSlot, predictedWorkDamage);
    }

    public static ExcavationServicePolicy unloadAndRepair(String loadoutId, String storageId, String repairStationId,
        int reservedToolSlot, int predictedWorkDamage) {
        return new ExcavationServicePolicy(
            loadoutId,
            storageId,
            repairStationId,
            reservedToolSlot,
            predictedWorkDamage);
    }

    public boolean hasUnload() {
        return loadoutId != null;
    }

    public boolean hasRepair() {
        return repairStationId != null;
    }

    public String getLoadoutId() {
        return requireConfigured(loadoutId, "unload service is not configured");
    }

    public String getStorageId() {
        return requireConfigured(storageId, "unload service is not configured");
    }

    public String getRepairStationId() {
        return requireConfigured(repairStationId, "repair service is not configured");
    }

    public int getReservedToolSlot() {
        return requireConfigured(reservedToolSlot, "repair service is not configured");
    }

    public int getPredictedWorkDamage() {
        return requireConfigured(predictedWorkDamage, "repair service is not configured");
    }

    void writeTo(Map<String, String> parameters) {
        if (hasUnload()) {
            parameters.put(ExcavationTask.LOADOUT_ID, loadoutId);
            parameters.put(ExcavationTask.STORAGE_ID, storageId);
        }
        if (hasRepair()) {
            parameters.put(ExcavationTask.REPAIR_STATION_ID, repairStationId);
            parameters.put(ExcavationTask.RESERVED_TOOL_SLOT, Integer.toString(reservedToolSlot));
            parameters.put(ExcavationTask.PREDICTED_WORK_DAMAGE, Integer.toString(predictedWorkDamage));
        }
    }

    static ExcavationServicePolicy parse(Map<String, String> parameters) {
        String loadout = optionalText(parameters, ExcavationTask.LOADOUT_ID).orElse(null);
        String storage = optionalText(parameters, ExcavationTask.STORAGE_ID).orElse(null);
        String station = optionalText(parameters, ExcavationTask.REPAIR_STATION_ID).orElse(null);
        Integer slot = optionalInteger(parameters, ExcavationTask.RESERVED_TOOL_SLOT).orElse(null);
        Integer damage = optionalInteger(parameters, ExcavationTask.PREDICTED_WORK_DAMAGE).orElse(null);
        if (loadout == null && storage == null && station == null && slot == null && damage == null) {
            return null;
        }
        return new ExcavationServicePolicy(loadout, storage, station, slot, damage);
    }

    private static Optional<String> optionalText(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        return value == null ? Optional.<String>empty() : Optional.of(value);
    }

    private static Optional<Integer> optionalInteger(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid excavation service parameter " + key, failure);
        }
    }

    private static String normalizePair(String value, String companion, String field, String companionField) {
        if (value == null && companion == null) return null;
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " is required when " + companionField + " is configured");
        }
        return value.trim();
    }

    private static String normalizeRepair(String station, Integer slot, Integer damage) {
        if (station == null && slot == null && damage == null) return null;
        if (station == null || station.trim()
            .isEmpty() || slot == null || damage == null) {
            throw new IllegalArgumentException(
                "repairStationId, reservedToolSlot, and predictedWorkDamage are required together");
        }
        return station.trim();
    }

    private static Integer normalizeNonNegative(Integer value, String station, String field) {
        if (station == null && value == null) return null;
        if (value == null || value < 0) throw new IllegalArgumentException(field + " must be non-negative");
        return value;
    }

    private static String requireConfigured(String value, String message) {
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private static int requireConfigured(Integer value, String message) {
        if (value == null) throw new IllegalStateException(message);
        return value;
    }
}
