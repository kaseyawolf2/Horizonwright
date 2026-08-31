package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationMode;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Persistence-friendly specification for one clean-volume cylinder excavation. */
public final class ExcavationTask {

    public static final String TYPE = "excavation";
    public static final String SHAPE = "shape";
    public static final String CYLINDER = "cylinder";
    public static final String MODE = "mode";
    public static final String DIMENSION = "dimension";
    public static final String CENTER_X = "centerX";
    public static final String CENTER_Z = "centerZ";
    public static final String RADIUS = "radius";
    public static final String BOTTOM_Y = "bottomY";
    public static final String TOP_Y = "topY";
    static final String LOADOUT_ID = "service.loadoutId";
    static final String STORAGE_ID = "service.storageId";
    static final String REPAIR_STATION_ID = "service.repairStationId";
    static final String RESERVED_TOOL_SLOT = "service.reservedToolSlot";
    static final String PREDICTED_WORK_DAMAGE = "service.predictedWorkDamage";

    private ExcavationTask() {}

    public static TaskSpec cleanVolumeCylinder(String taskId, int dimensionId, int centerX, int centerZ, int radius,
        int bottomY, int topY) {
        return cleanVolumeCylinder(taskId, dimensionId, centerX, centerZ, radius, bottomY, topY, null);
    }

    /** Creates a clean-volume excavation with optional durable unload and repair service bindings. */
    public static TaskSpec cleanVolumeCylinder(String taskId, int dimensionId, int centerX, int centerZ, int radius,
        int bottomY, int topY, ExcavationServicePolicy servicePolicy) {
        CylinderExcavationSpec cylinder = new CylinderExcavationSpec(
            dimensionId,
            centerX,
            centerZ,
            radius,
            bottomY,
            topY,
            ExcavationMode.CLEAN_VOLUME);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(SHAPE, CYLINDER);
        parameters.put(MODE, ExcavationMode.CLEAN_VOLUME.name());
        parameters.put(DIMENSION, Integer.toString(cylinder.getDimensionId()));
        parameters.put(CENTER_X, Integer.toString(cylinder.getCenterX()));
        parameters.put(CENTER_Z, Integer.toString(cylinder.getCenterZ()));
        parameters.put(RADIUS, Integer.toString(cylinder.getRadius()));
        parameters.put(BOTTOM_Y, Integer.toString(cylinder.getBottomY()));
        parameters.put(TOP_Y, Integer.toString(cylinder.getTopY()));
        if (servicePolicy != null) {
            servicePolicy.writeTo(parameters);
        }
        return new TaskSpec(
            taskId,
            TYPE,
            "Excavate clean cylinder at " + centerX + ", " + centerZ,
            TaskLane.FALLBACK,
            parameters);
    }

    static ExcavationServicePolicy servicePolicy(TaskSpec spec) {
        parse(spec);
        return ExcavationServicePolicy.parse(spec.getParameters());
    }

    static CylinderExcavationSpec parse(TaskSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (!TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("unsupported task type: " + spec.getType());
        }
        Map<String, String> parameters = spec.getParameters();
        requireValue(parameters, SHAPE, CYLINDER);
        requireValue(parameters, MODE, ExcavationMode.CLEAN_VOLUME.name());
        return new CylinderExcavationSpec(
            parseInteger(parameters, DIMENSION),
            parseInteger(parameters, CENTER_X),
            parseInteger(parameters, CENTER_Z),
            parseInteger(parameters, RADIUS),
            parseInteger(parameters, BOTTOM_Y),
            parseInteger(parameters, TOP_Y),
            ExcavationMode.CLEAN_VOLUME);
    }

    private static int parseInteger(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("missing excavation parameter: " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid excavation parameter " + key + ": " + value, failure);
        }
    }

    private static void requireValue(Map<String, String> parameters, String key, String requiredValue) {
        String value = parameters.get(key);
        if (!requiredValue.equals(value)) {
            throw new IllegalArgumentException(
                "unsupported excavation " + key + ": " + (value == null ? "<missing>" : value));
        }
    }
}
