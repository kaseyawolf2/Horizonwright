package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable off-thread calculation result, bounded by the source target batch. */
public final class ExcavationPlan {

    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final ExcavationFrontier nextFrontier;
    private final List<ExcavationIntent> intents;
    private final List<ManagedQuarryIntent> managedIntents;

    ExcavationPlan(long taskRevision, long actionEpoch, String geometryKey, ExcavationFrontier startFrontier,
        ExcavationFrontier nextFrontier, List<ExcavationIntent> intents, List<ManagedQuarryIntent> managedIntents) {
        this.taskRevision = taskRevision;
        this.actionEpoch = actionEpoch;
        this.geometryKey = Objects.requireNonNull(geometryKey, "geometryKey");
        this.startFrontier = Objects.requireNonNull(startFrontier, "startFrontier");
        this.nextFrontier = Objects.requireNonNull(nextFrontier, "nextFrontier");
        this.intents = immutableCopy(intents, "intent");
        this.managedIntents = immutableCopy(managedIntents, "managedIntent");
        if (this.intents.size() > CylinderExcavationGeometry.MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("plan exceeds the bounded target limit");
        }
    }

    public long getTaskRevision() {
        return taskRevision;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public String getGeometryKey() {
        return geometryKey;
    }

    public ExcavationFrontier getStartFrontier() {
        return startFrontier;
    }

    public ExcavationFrontier getNextFrontier() {
        return nextFrontier;
    }

    public List<ExcavationIntent> getIntents() {
        return intents;
    }

    public List<ManagedQuarryIntent> getManagedIntents() {
        return managedIntents;
    }

    private static <T> List<T> immutableCopy(List<T> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + "s must not be null");
        }
        List<T> copy = new ArrayList<>(values.size());
        for (T value : values) {
            copy.add(Objects.requireNonNull(value, name));
        }
        return Collections.unmodifiableList(copy);
    }
}
