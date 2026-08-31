package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmPassCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Strict checkpoint bridge for one immutable finite farm observation pass. */
final class FarmTaskCheckpointCodec {

    private static final String PLOT_ID = "plot.id";
    private static final String PLOT_NAME = "plot.name";
    private static final String PLOT_DIMENSION = "plot.dimension";
    private static final String PLOT_MIN_X = "plot.minX";
    private static final String PLOT_MIN_Y = "plot.minY";
    private static final String PLOT_MIN_Z = "plot.minZ";
    private static final String PLOT_MAX_X = "plot.maxX";
    private static final String PLOT_MAX_Y = "plot.maxY";
    private static final String PLOT_MAX_Z = "plot.maxZ";
    private static final String PASS_REVISION = "passRevision";
    private static final String CHECKPOINT_REVISION = "checkpointRevision";
    private static final String NEXT_INDEX = "nextIndex";
    private static final String VERIFIED_MUTATIONS = "verifiedMutations";
    private static final String OBSERVATION_COUNT = "observationCount";

    private FarmTaskCheckpointCodec() {}

    static TaskCheckpoint encode(TaskSpec spec, FarmPassCheckpoint checkpoint, List<CropObservation> observations) {
        return encode(spec, checkpoint, observations, checkpoint.getPassRevision());
    }

    static TaskCheckpoint encode(TaskSpec spec, FarmPassCheckpoint checkpoint, List<CropObservation> observations,
        long checkpointRevision) {
        if (checkpoint == null || observations == null || observations.contains(null)) {
            throw new IllegalArgumentException("checkpoint and complete observations are required");
        }
        requireSpec(spec, checkpoint.getPlot());
        requireMatchingObservations(checkpoint, observations);
        if (checkpoint.getPassRevision() < 1L || checkpointRevision < checkpoint.getPassRevision()) {
            throw new IllegalArgumentException("farm checkpoint revision must not predate its positive pass revision");
        }
        NamedArea plot = checkpoint.getPlot();
        BasePosition minimum = plot.getMinimum();
        BasePosition maximum = plot.getMaximum();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(PLOT_ID, plot.getId());
        values.put(PLOT_NAME, plot.getDisplayName());
        values.put(PLOT_DIMENSION, Integer.toString(minimum.getDimensionId()));
        values.put(PLOT_MIN_X, Integer.toString(minimum.getX()));
        values.put(PLOT_MIN_Y, Integer.toString(minimum.getY()));
        values.put(PLOT_MIN_Z, Integer.toString(minimum.getZ()));
        values.put(PLOT_MAX_X, Integer.toString(maximum.getX()));
        values.put(PLOT_MAX_Y, Integer.toString(maximum.getY()));
        values.put(PLOT_MAX_Z, Integer.toString(maximum.getZ()));
        values.put(PASS_REVISION, Long.toString(checkpoint.getPassRevision()));
        values.put(CHECKPOINT_REVISION, Long.toString(checkpointRevision));
        values.put(NEXT_INDEX, Integer.toString(checkpoint.getNextObservationIndex()));
        values.put(VERIFIED_MUTATIONS, Integer.toString(checkpoint.getVerifiedMutations()));
        values.put(OBSERVATION_COUNT, Integer.toString(observations.size()));
        for (int index = 0; index < observations.size(); index++)
            writeObservation(values, index, observations.get(index));
        return new TaskCheckpoint(checkpointRevision, values);
    }

    static FarmPassCheckpoint decode(TaskSpec spec, TaskCheckpoint taskCheckpoint) {
        if (taskCheckpoint == null) throw new IllegalArgumentException("task checkpoint is required");
        if (taskCheckpoint.getRevision() == 0L && taskCheckpoint.getValues()
            .isEmpty()) return null;
        Map<String, String> values = taskCheckpoint.getValues();
        int dimension = integer(values, PLOT_DIMENSION);
        NamedArea plot = new NamedArea(
            text(values, PLOT_ID),
            text(values, PLOT_NAME),
            new BasePosition(
                dimension,
                integer(values, PLOT_MIN_X),
                integer(values, PLOT_MIN_Y),
                integer(values, PLOT_MIN_Z)),
            new BasePosition(
                dimension,
                integer(values, PLOT_MAX_X),
                integer(values, PLOT_MAX_Y),
                integer(values, PLOT_MAX_Z)));
        requireSpec(spec, plot);
        long passRevision = longValue(values, PASS_REVISION);
        if (passRevision < 1L || longValue(values, CHECKPOINT_REVISION) != taskCheckpoint.getRevision()
            || taskCheckpoint.getRevision() < passRevision) {
            throw new IllegalArgumentException("farm checkpoint revision is inconsistent with its pass revision");
        }
        List<CropObservation> observations = observations(taskCheckpoint);
        return FarmPassCheckpoint.restore(
            plot,
            passRevision,
            observations,
            integer(values, NEXT_INDEX),
            integer(values, VERIFIED_MUTATIONS));
    }

    static List<CropObservation> observations(TaskCheckpoint taskCheckpoint) {
        if (taskCheckpoint == null) throw new IllegalArgumentException("task checkpoint is required");
        if (taskCheckpoint.getRevision() == 0L && taskCheckpoint.getValues()
            .isEmpty()) return Collections.emptyList();
        Map<String, String> values = taskCheckpoint.getValues();
        int count = integer(values, OBSERVATION_COUNT);
        if (count < 0 || count > 1_000_000) throw new IllegalArgumentException("invalid farm observation count");
        List<CropObservation> observations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) observations.add(readObservation(values, index));
        return Collections.unmodifiableList(observations);
    }

    private static void writeObservation(Map<String, String> values, int index, CropObservation observation) {
        String prefix = "observation." + index + ".";
        BasePosition position = observation.getPosition();
        values.put(prefix + "dimension", Integer.toString(position.getDimensionId()));
        values.put(prefix + "x", Integer.toString(position.getX()));
        values.put(prefix + "y", Integer.toString(position.getY()));
        values.put(prefix + "z", Integer.toString(position.getZ()));
        values.put(
            prefix + "family",
            observation.getFamily()
                .name());
        values.put(prefix + "fingerprint", observation.getObservationFingerprint());
        values.put(prefix + "seed", observation.getRequiredSeedFingerprint());
        values.put(prefix + "maturityKnown", Boolean.toString(observation.isMaturityKnown()));
        values.put(prefix + "mature", Boolean.toString(observation.isMature()));
        values.put(prefix + "protected", Boolean.toString(observation.isProtectedBlock()));
    }

    private static CropObservation readObservation(Map<String, String> values, int index) {
        String prefix = "observation." + index + ".";
        return new CropObservation(
            new BasePosition(
                integer(values, prefix + "dimension"),
                integer(values, prefix + "x"),
                integer(values, prefix + "y"),
                integer(values, prefix + "z")),
            enumValue(CropFamily.class, values, prefix + "family"),
            text(values, prefix + "fingerprint"),
            text(values, prefix + "seed"),
            bool(values, prefix + "maturityKnown"),
            bool(values, prefix + "mature"),
            bool(values, prefix + "protected"));
    }

    private static void requireMatchingObservations(FarmPassCheckpoint checkpoint, List<CropObservation> observations) {
        if (checkpoint.getObservationCount() != observations.size()) {
            throw new IllegalArgumentException("farm observations do not match the checkpoint size");
        }
        for (int index = 0; index < observations.size(); index++) {
            CropObservation observation = observations.get(index);
            if (!checkpoint.getObservationTargets()
                .get(index)
                .equals(observation.getPosition())
                || !checkpoint.getObservationFingerprints()
                    .get(index)
                    .equals(observation.getObservationFingerprint())
                || !checkpoint.getRequiredSeedFingerprints()
                    .get(index)
                    .equals(observation.getRequiredSeedFingerprint())) {
                throw new IllegalArgumentException("farm observation " + index + " does not match the checkpoint");
            }
        }
    }

    private static void requireSpec(TaskSpec spec, NamedArea plot) {
        if (!FarmTask.plotId(spec)
            .equals(plot.getId())) {
            throw new IllegalArgumentException("farm checkpoint belongs to another named plot");
        }
        FarmTask.minimumSeedReserve(spec);
    }

    private static String text(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException("missing farm field " + key);
        return value.trim();
    }

    private static int integer(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(text(values, key));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid farm integer " + key, failure);
        }
    }

    private static long longValue(Map<String, String> values, String key) {
        try {
            return Long.parseLong(text(values, key));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid farm long " + key, failure);
        }
    }

    private static boolean bool(Map<String, String> values, String key) {
        String value = text(values, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("invalid farm boolean " + key);
        }
        return Boolean.parseBoolean(value);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Map<String, String> values, String key) {
        try {
            return Enum.valueOf(type, text(values, key));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid farm enum " + key, failure);
        }
    }
}
