package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationMode;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryConfiguration;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Persistence-friendly specification for clean-volume and managed-quarry cylinder excavation. */
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
    public static final String RAMP_MATERIAL = "managed.rampMaterial";
    public static final String LIGHT_MATERIAL = "managed.lightMaterial";
    public static final String FLUID_FILLER_MATERIAL = "managed.fluidFillerMaterial";
    public static final String LIGHT_LAYER_INTERVAL = "managed.lightLayerInterval";
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

    public static TaskSpec managedQuarryCylinder(String taskId, int dimensionId, int centerX, int centerZ, int radius,
        int bottomY, int topY, ManagedQuarryConfiguration configuration) {
        return managedQuarryCylinder(taskId, dimensionId, centerX, centerZ, radius, bottomY, topY, configuration, null);
    }

    /** Creates a managed quarry carrying only explicitly approved infrastructure materials. */
    public static TaskSpec managedQuarryCylinder(String taskId, int dimensionId, int centerX, int centerZ, int radius,
        int bottomY, int topY, ManagedQuarryConfiguration configuration, ExcavationServicePolicy servicePolicy) {
        if (configuration == null) throw new IllegalArgumentException("managed quarry configuration is required");
        CylinderExcavationSpec cylinder = new CylinderExcavationSpec(
            dimensionId,
            centerX,
            centerZ,
            radius,
            bottomY,
            topY,
            ExcavationMode.MANAGED_QUARRY);
        io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryGeometry
            .rampStep(cylinder, cylinder.getTopY());
        Map<String, String> parameters = baseParameters(cylinder);
        parameters.put(RAMP_MATERIAL, configuration.getRampMaterial());
        parameters.put(LIGHT_MATERIAL, configuration.getLightMaterial());
        parameters.put(FLUID_FILLER_MATERIAL, configuration.getFluidFillerMaterial());
        parameters.put(LIGHT_LAYER_INTERVAL, Integer.toString(configuration.getLightLayerInterval()));
        if (servicePolicy != null) servicePolicy.writeTo(parameters);
        return new TaskSpec(
            taskId,
            TYPE,
            "Excavate managed quarry at " + centerX + ", " + centerZ,
            TaskLane.FALLBACK,
            parameters);
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
        Map<String, String> parameters = baseParameters(cylinder);
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

    static ManagedQuarryConfiguration managedConfiguration(TaskSpec spec) {
        CylinderExcavationSpec cylinder = parse(spec);
        if (cylinder.getMode() != ExcavationMode.MANAGED_QUARRY) return null;
        Map<String, String> parameters = spec.getParameters();
        return new ManagedQuarryConfiguration(
            requireText(parameters, RAMP_MATERIAL),
            requireText(parameters, LIGHT_MATERIAL),
            requireText(parameters, FLUID_FILLER_MATERIAL),
            parseInteger(parameters, LIGHT_LAYER_INTERVAL));
    }

    static CylinderExcavationSpec parse(TaskSpec spec) {
        CylinderExcavationSpec geometry = parseGeometry(spec);
        String order = spec.getParameters()
            .get("traversal");
        return geometry.withTraversal(
            io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTraversal.parse(order),
            Integer.parseInt(
                spec.getParameters()
                    .getOrDefault("spiralWidth", "1")));
    }

    private static CylinderExcavationSpec parseGeometry(TaskSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (!TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("unsupported task type: " + spec.getType());
        }
        Map<String, String> parameters = spec.getParameters();
        ExcavationMode mode = parseMode(parameters);
        if ("rectangle".equals(parameters.get(SHAPE))) return CylinderExcavationSpec.rectangle(
            parseInteger(parameters, DIMENSION),
            parseInteger(parameters, "minX"),
            parseInteger(parameters, "maxX"),
            parseInteger(parameters, "minZ"),
            parseInteger(parameters, "maxZ"),
            parseInteger(parameters, BOTTOM_Y),
            parseInteger(parameters, TOP_Y),
            mode);
        requireValue(parameters, SHAPE, CYLINDER);
        return new CylinderExcavationSpec(
            parseInteger(parameters, DIMENSION),
            parseInteger(parameters, CENTER_X),
            parseInteger(parameters, CENTER_Z),
            parseInteger(parameters, RADIUS),
            parseInteger(parameters, BOTTOM_Y),
            parseInteger(parameters, TOP_Y),
            mode);
    }

    private static Map<String, String> baseParameters(CylinderExcavationSpec cylinder) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(SHAPE, CYLINDER);
        parameters.put(
            MODE,
            cylinder.getMode()
                .name());
        parameters.put(DIMENSION, Integer.toString(cylinder.getDimensionId()));
        parameters.put(CENTER_X, Integer.toString(cylinder.getCenterX()));
        parameters.put(CENTER_Z, Integer.toString(cylinder.getCenterZ()));
        parameters.put(RADIUS, Integer.toString(cylinder.getRadius()));
        parameters.put(BOTTOM_Y, Integer.toString(cylinder.getBottomY()));
        parameters.put(TOP_Y, Integer.toString(cylinder.getTopY()));
        return parameters;
    }

    public static TaskSpec withTraversal(TaskSpec template,
        io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTraversal order) {
        Map<String, String> parameters = new LinkedHashMap<>(template.getParameters());
        parameters.put("traversal", order.id());
        TaskSpec result = new TaskSpec(
            template.getId(),
            template.getType(),
            template.getDisplayName(),
            template.getLane(),
            parameters);
        parse(result);
        return result;
    }

    public static TaskSpec forArea(TaskSpec template, io.github.kaseyawolf2.horizonwright.core.base.NamedArea area) {
        Map<String, String> ordered = new LinkedHashMap<>(template.getParameters());
        ordered.put("traversal", "spiral-v1");
        template = new TaskSpec(
            template.getId(),
            template.getType(),
            template.getDisplayName(),
            template.getLane(),
            ordered);
        if (area.isCircular()) return template;
        Map<String, String> parameters = new LinkedHashMap<>(template.getParameters());
        parameters.put(SHAPE, "rectangle");
        parameters.put(
            "minX",
            Integer.toString(
                area.getMinimum()
                    .getX()));
        parameters.put(
            "maxX",
            Integer.toString(
                area.getMaximum()
                    .getX()));
        parameters.put(
            "minZ",
            Integer.toString(
                area.getMinimum()
                    .getZ()));
        parameters.put(
            "maxZ",
            Integer.toString(
                area.getMaximum()
                    .getZ()));
        TaskSpec result = new TaskSpec(
            template.getId(),
            TYPE,
            "Mine rectangle: " + area.getDisplayName(),
            template.getLane(),
            parameters);
        CylinderExcavationSpec geometry = parse(result);
        if (geometry.getMode() == ExcavationMode.MANAGED_QUARRY)
            io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryGeometry
                .rampStep(geometry, geometry.getTopY());
        return result;
    }

    private static ExcavationMode parseMode(Map<String, String> parameters) {
        String value = requireText(parameters, MODE);
        try {
            return ExcavationMode.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unsupported excavation mode: " + value, failure);
        }
    }

    private static int parseInteger(Map<String, String> parameters, String key) {
        String value = requireText(parameters, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid excavation parameter " + key + ": " + value, failure);
        }
    }

    private static String requireText(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException("missing excavation parameter: " + key);
        return value.trim();
    }

    private static void requireValue(Map<String, String> parameters, String key, String requiredValue) {
        String value = parameters.get(key);
        if (!requiredValue.equals(value)) {
            throw new IllegalArgumentException(
                "unsupported excavation " + key + ": " + (value == null ? "<missing>" : value));
        }
    }
}
