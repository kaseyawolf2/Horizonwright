package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryConfiguration;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Validates named profile dependencies before creating a service-enabled excavation specification. */
public final class ExcavationTaskSubmission {

    private ExcavationTaskSubmission() {}

    public static TaskSpec withoutServices(String taskId, int dimensionId, int centerX, int centerZ, int radius,
        int bottomY, int topY) {
        return ExcavationTask.cleanVolumeCylinder(taskId, dimensionId, centerX, centerZ, radius, bottomY, topY);
    }

    public static TaskSpec withServices(ProfileEnvelope profile, String taskId, int dimensionId, int centerX,
        int centerZ, int radius, int bottomY, int topY, String loadoutId, String storageId, String repairStationId,
        int reservedToolSlot, int predictedWorkDamage) {
        return ExcavationTask.cleanVolumeCylinder(
            taskId,
            dimensionId,
            centerX,
            centerZ,
            radius,
            bottomY,
            topY,
            servicePolicy(profile, loadoutId, storageId, repairStationId, reservedToolSlot, predictedWorkDamage));
    }

    public static TaskSpec managedWithoutServices(String taskId, int dimensionId, int centerX, int centerZ, int radius,
        int bottomY, int topY, ManagedQuarryConfiguration configuration) {
        return ExcavationTask
            .managedQuarryCylinder(taskId, dimensionId, centerX, centerZ, radius, bottomY, topY, configuration);
    }

    public static TaskSpec managedWithServices(ProfileEnvelope profile, String taskId, int dimensionId, int centerX,
        int centerZ, int radius, int bottomY, int topY, ManagedQuarryConfiguration configuration, String loadoutId,
        String storageId, String repairStationId, int reservedToolSlot, int predictedWorkDamage) {
        return ExcavationTask.managedQuarryCylinder(
            taskId,
            dimensionId,
            centerX,
            centerZ,
            radius,
            bottomY,
            topY,
            configuration,
            servicePolicy(profile, loadoutId, storageId, repairStationId, reservedToolSlot, predictedWorkDamage));
    }

    private static ExcavationServicePolicy servicePolicy(ProfileEnvelope profile, String loadoutId, String storageId,
        String repairStationId, int reservedToolSlot, int predictedWorkDamage) {
        if (profile == null) throw new IllegalArgumentException("active profile is required for named services");
        NamedLoadout loadout = loadout(profile, loadoutId);
        storage(profile, storageId);
        if ("automatic-inventory".equals(loadoutId)) {
            NamedRepairStation automaticStation = null;
            for (NamedRepairStation candidate : profile.getNamedRepairStations()) if (candidate.getId()
                .equals(repairStationId)) automaticStation = candidate;
            if (automaticStation == null) return ExcavationServicePolicy.unloadOnly(loadout.getId(), storageId);
            return ExcavationServicePolicy.unloadAndRepair(
                loadout.getId(),
                storageId,
                automaticStation.getId(),
                reservedToolSlot,
                predictedWorkDamage);
        }
        NamedRepairStation station = station(profile, repairStationId);
        if (!station.getLoadoutId()
            .equals(loadout.getId())) {
            throw new IllegalArgumentException(
                "repair station '" + station.getId() + "' uses loadout '" + station.getLoadoutId() + "'");
        }
        return ExcavationServicePolicy
            .unloadAndRepair(loadout.getId(), storageId, station.getId(), reservedToolSlot, predictedWorkDamage);
    }

    private static NamedLoadout loadout(ProfileEnvelope profile, String id) {
        String required = requireId(id, "loadout");
        for (NamedLoadout value : profile.getNamedLoadouts()) {
            if (value.getId()
                .equals(required)) return value;
        }
        throw new IllegalArgumentException("active profile has no loadout '" + required + "'");
    }

    private static NamedStorageEndpoint storage(ProfileEnvelope profile, String id) {
        String required = requireId(id, "storage endpoint");
        for (NamedStorageEndpoint value : profile.getNamedStorageEndpoints()) {
            if (value.getId()
                .equals(required)) return value;
        }
        throw new IllegalArgumentException("active profile has no storage endpoint '" + required + "'");
    }

    private static NamedRepairStation station(ProfileEnvelope profile, String id) {
        String required = requireId(id, "repair station");
        for (NamedRepairStation value : profile.getNamedRepairStations()) {
            if (value.getId()
                .equals(required)) return value;
        }
        throw new IllegalArgumentException("active profile has no repair station '" + required + "'");
    }

    private static String requireId(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " id is required");
        return value.trim();
    }
}
