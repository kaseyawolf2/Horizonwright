package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Persistence-friendly specification for one bounded navigation task. */
public final class GoToTask {

    public static final String TYPE = "goto";
    public static final String DIMENSION = "dimension";
    public static final String X = "x";
    public static final String Y = "y";
    public static final String Z = "z";
    public static final String TOLERANCE = "tolerance";

    private GoToTask() {}

    public static TaskSpec create(String taskId, int dimensionId, int x, int y, int z, int tolerance) {
        validateTarget(x, y, z, tolerance);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(DIMENSION, Integer.toString(dimensionId));
        parameters.put(X, Integer.toString(x));
        parameters.put(Y, Integer.toString(y));
        parameters.put(Z, Integer.toString(z));
        parameters.put(TOLERANCE, Integer.toString(tolerance));
        return new TaskSpec(taskId, TYPE, "Go to " + x + ", " + y + ", " + z, TaskLane.MANUAL, parameters);
    }

    static Target parse(TaskSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (!TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("unsupported task type: " + spec.getType());
        }
        Map<String, String> parameters = spec.getParameters();
        int dimension = parseInteger(parameters, DIMENSION);
        int x = parseInteger(parameters, X);
        int y = parseInteger(parameters, Y);
        int z = parseInteger(parameters, Z);
        int tolerance = parseInteger(parameters, TOLERANCE);
        validateTarget(x, y, z, tolerance);
        return new Target(dimension, x, y, z, tolerance);
    }

    private static int parseInteger(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("missing GoTo parameter: " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid GoTo parameter " + key + ": " + value, failure);
        }
    }

    private static void validateTarget(int x, int y, int z, int tolerance) {
        if (x < -NavigationRequest.MAX_ABS_COORDINATE || x > NavigationRequest.MAX_ABS_COORDINATE
            || z < -NavigationRequest.MAX_ABS_COORDINATE
            || z > NavigationRequest.MAX_ABS_COORDINATE) {
            throw new IllegalArgumentException("target is outside the supported world coordinate range");
        }
        if (y < NavigationRequest.MIN_Y || y > NavigationRequest.MAX_Y) {
            throw new IllegalArgumentException(
                "target Y must be between " + NavigationRequest.MIN_Y + " and " + NavigationRequest.MAX_Y);
        }
        if (tolerance < 0 || tolerance > NavigationRequest.MAX_TOLERANCE) {
            throw new IllegalArgumentException("tolerance must be between 0 and " + NavigationRequest.MAX_TOLERANCE);
        }
    }

    static final class Target {

        final int dimensionId;
        final int x;
        final int y;
        final int z;
        final int tolerance;

        private Target(int dimensionId, int x, int y, int z, int tolerance) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tolerance = tolerance;
        }
    }
}
