package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationMode;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationProgress;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;

/** Strict, self-authenticating TaskCheckpoint bridge for clean-volume cylinder state. */
final class ExcavationTaskCheckpointCodec {

    private static final String PHASE = "phase";
    private static final String ACTIVE = "active";
    private static final String SUSPENDED = "suspended";
    private static final String COMPLETED = "completed";
    private static final String GEOMETRY_KEY = "geometryKey";
    private static final String TASK_REVISION = "taskRevision";
    private static final String ACTION_EPOCH = "actionEpoch";
    private static final String DIMENSION = "dimension";
    private static final String CENTER_X = "centerX";
    private static final String CENTER_Z = "centerZ";
    private static final String RADIUS = "radius";
    private static final String BOTTOM_Y = "bottomY";
    private static final String TOP_Y = "topY";
    private static final String MODE = "mode";
    private static final String FRONTIER_LAYER_Y = "frontier.layerY";
    private static final String FRONTIER_CHUNK_X = "frontier.chunkX";
    private static final String FRONTIER_CHUNK_Z = "frontier.chunkZ";
    private static final String FRONTIER_BAND = "frontier.band";
    private static final String FRONTIER_OFFSET = "frontier.offset";
    private static final String FRONTIER_COMPLETE = "frontier.complete";
    private static final String PROGRESS_TOTAL = "progress.total";
    private static final String PROGRESS_COMPLETED = "progress.completed";
    private static final String PROGRESS_PROTECTED = "progress.protected";
    private static final String PROGRESS_UNREACHABLE = "progress.unreachable";
    private static final String PROGRESS_FLUID_CONTAINED = "progress.fluidContained";
    private static final String PROGRESS_FAILED = "progress.failed";
    private static final String SUSPENSION_REASON = "suspensionReason";

    private ExcavationTaskCheckpointCodec() {}

    static TaskCheckpoint encode(CylinderExcavationSpec spec, ExcavationCheckpoint checkpoint) {
        requireMatchingGeometry(spec, checkpoint);
        if (checkpoint.getTaskRevision() < 1L) {
            throw new IllegalArgumentException("runtime excavation checkpoint revision must be positive");
        }
        ExcavationFrontier frontier = checkpoint.getFrontier();
        ExcavationProgress progress = checkpoint.getProgress();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(PHASE, phase(checkpoint));
        values.put(GEOMETRY_KEY, checkpoint.getGeometryKey());
        values.put(TASK_REVISION, Long.toString(checkpoint.getTaskRevision()));
        values.put(ACTION_EPOCH, Long.toString(checkpoint.getActionEpoch()));
        values.put(DIMENSION, Integer.toString(spec.getDimensionId()));
        values.put(CENTER_X, Integer.toString(spec.getCenterX()));
        values.put(CENTER_Z, Integer.toString(spec.getCenterZ()));
        values.put(RADIUS, Integer.toString(spec.getRadius()));
        values.put(BOTTOM_Y, Integer.toString(spec.getBottomY()));
        values.put(TOP_Y, Integer.toString(spec.getTopY()));
        values.put(
            MODE,
            spec.getMode()
                .name());
        values.put(FRONTIER_LAYER_Y, Integer.toString(frontier.getLayerY()));
        values.put(FRONTIER_CHUNK_X, Integer.toString(frontier.getChunkX()));
        values.put(FRONTIER_CHUNK_Z, Integer.toString(frontier.getChunkZ()));
        values.put(FRONTIER_BAND, Integer.toString(frontier.getBand()));
        values.put(FRONTIER_OFFSET, Integer.toString(frontier.getOffset()));
        values.put(FRONTIER_COMPLETE, Boolean.toString(frontier.isComplete()));
        values.put(PROGRESS_TOTAL, Long.toString(progress.getTotal()));
        values.put(PROGRESS_COMPLETED, Long.toString(progress.getCompleted()));
        values.put(PROGRESS_PROTECTED, Long.toString(progress.getProtectedBlocks()));
        values.put(PROGRESS_UNREACHABLE, Long.toString(progress.getUnreachable()));
        values.put(PROGRESS_FLUID_CONTAINED, Long.toString(progress.getFluidContained()));
        values.put(PROGRESS_FAILED, Long.toString(progress.getFailed()));
        values.put(
            SUSPENSION_REASON,
            checkpoint.getSuspensionReason()
                .name());
        return new TaskCheckpoint(checkpoint.getTaskRevision(), values);
    }

    static ExcavationCheckpoint decode(CylinderExcavationSpec spec, TaskCheckpoint checkpoint) {
        if (spec == null || checkpoint == null) {
            throw new IllegalArgumentException("spec and checkpoint must not be null");
        }
        if (checkpoint.getRevision() == 0L && checkpoint.getValues()
            .isEmpty()) {
            return null;
        }
        Map<String, String> values = checkpoint.getValues();
        requireSpec(values, spec);
        long taskRevision = parseLong(values, TASK_REVISION);
        if (taskRevision != checkpoint.getRevision()) {
            throw new IllegalArgumentException("excavation checkpoint revision does not match its bound revision");
        }
        long actionEpoch = parseLong(values, ACTION_EPOCH);
        ExcavationFrontier frontier = ExcavationFrontier.restore(
            requireText(values, GEOMETRY_KEY),
            parseInteger(values, FRONTIER_LAYER_Y),
            parseInteger(values, FRONTIER_CHUNK_X),
            parseInteger(values, FRONTIER_CHUNK_Z),
            parseInteger(values, FRONTIER_BAND),
            parseInteger(values, FRONTIER_OFFSET),
            parseBoolean(values, FRONTIER_COMPLETE));
        ExcavationProgress progress = new ExcavationProgress(
            parseLong(values, PROGRESS_TOTAL),
            parseLong(values, PROGRESS_COMPLETED),
            parseLong(values, PROGRESS_PROTECTED),
            parseLong(values, PROGRESS_UNREACHABLE),
            parseLong(values, PROGRESS_FLUID_CONTAINED),
            parseLong(values, PROGRESS_FAILED));
        ExcavationSuspensionReason suspension = parseEnum(
            ExcavationSuspensionReason.class,
            requireText(values, SUSPENSION_REASON),
            SUSPENSION_REASON);
        ExcavationCheckpoint restored = ExcavationCheckpoint
            .restore(spec, taskRevision, actionEpoch, frontier, progress, suspension);
        requirePhase(values, restored);
        return restored;
    }

    private static String phase(ExcavationCheckpoint checkpoint) {
        if (checkpoint.isComplete()) {
            return COMPLETED;
        }
        return checkpoint.isSuspended() ? SUSPENDED : ACTIVE;
    }

    private static void requirePhase(Map<String, String> values, ExcavationCheckpoint checkpoint) {
        String persisted = requireText(values, PHASE);
        String expected = phase(checkpoint);
        if (!expected.equals(persisted)) {
            throw new IllegalArgumentException(
                "excavation checkpoint phase " + persisted + " disagrees with its frontier and suspension state");
        }
    }

    private static void requireSpec(Map<String, String> values, CylinderExcavationSpec spec) {
        if (parseInteger(values, DIMENSION) != spec.getDimensionId()
            || parseInteger(values, CENTER_X) != spec.getCenterX()
            || parseInteger(values, CENTER_Z) != spec.getCenterZ()
            || parseInteger(values, RADIUS) != spec.getRadius()
            || parseInteger(values, BOTTOM_Y) != spec.getBottomY()
            || parseInteger(values, TOP_Y) != spec.getTopY()
            || parseEnum(ExcavationMode.class, requireText(values, MODE), MODE) != spec.getMode()
            || !requireText(values, GEOMETRY_KEY).equals(spec.getGeometryKey())) {
            throw new IllegalArgumentException("persisted excavation specification does not match the task");
        }
    }

    private static void requireMatchingGeometry(CylinderExcavationSpec spec, ExcavationCheckpoint checkpoint) {
        if (spec == null || checkpoint == null) {
            throw new IllegalArgumentException("spec and checkpoint must not be null");
        }
        if (!spec.getGeometryKey()
            .equals(checkpoint.getGeometryKey())) {
            throw new IllegalArgumentException("checkpoint belongs to another excavation specification");
        }
    }

    private static String requireText(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("missing excavation checkpoint field: " + key);
        }
        return value.trim();
    }

    private static int parseInteger(Map<String, String> values, String key) {
        String value = requireText(values, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid excavation checkpoint integer " + key + ": " + value, failure);
        }
    }

    private static long parseLong(Map<String, String> values, String key) {
        String value = requireText(values, key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid excavation checkpoint long " + key + ": " + value, failure);
        }
    }

    private static boolean parseBoolean(Map<String, String> values, String key) {
        String value = requireText(values, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("invalid excavation checkpoint boolean " + key + ": " + value);
        }
        return Boolean.parseBoolean(value);
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid excavation checkpoint " + field + ": " + value, failure);
        }
    }
}
