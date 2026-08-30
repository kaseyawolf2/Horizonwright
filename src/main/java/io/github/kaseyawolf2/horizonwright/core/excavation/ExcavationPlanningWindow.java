package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Exact bounded world snapshot passed across the asynchronous calculation boundary. */
public final class ExcavationPlanningWindow {

    private final long taskRevision;
    private final long actionEpoch;
    private final ExcavationTargetBatch targetBatch;
    private final List<ExcavationObservation> observations;

    public ExcavationPlanningWindow(long taskRevision, long actionEpoch, ExcavationTargetBatch targetBatch,
        List<ExcavationObservation> observations) {
        if (taskRevision < 0L) {
            throw new IllegalArgumentException("taskRevision must not be negative");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        this.targetBatch = Objects.requireNonNull(targetBatch, "targetBatch");
        if (observations == null || observations.size() != targetBatch.getTargets()
            .size()) {
            throw new IllegalArgumentException("observations must exactly cover the bounded target batch");
        }
        List<ExcavationObservation> copy = new ArrayList<>(observations.size());
        for (int index = 0; index < observations.size(); index++) {
            ExcavationObservation observation = Objects.requireNonNull(observations.get(index), "observation");
            if (!targetBatch.getTargets()
                .get(index)
                .getPosition()
                .equals(observation.getPosition())) {
                throw new IllegalArgumentException("observations must preserve target order and position");
            }
            copy.add(observation);
        }
        this.taskRevision = taskRevision;
        this.actionEpoch = actionEpoch;
        this.observations = Collections.unmodifiableList(copy);
    }

    public long getTaskRevision() {
        return taskRevision;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public ExcavationTargetBatch getTargetBatch() {
        return targetBatch;
    }

    public List<ExcavationObservation> getObservations() {
        return observations;
    }
}
