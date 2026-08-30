package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

/** Persistable exact excavation state; transitions return new instances. */
public final class ExcavationCheckpoint {

    private final String geometryKey;
    private final long taskRevision;
    private final long actionEpoch;
    private final ExcavationFrontier frontier;
    private final ExcavationProgress progress;
    private final ExcavationSuspensionReason suspensionReason;

    private ExcavationCheckpoint(String geometryKey, long taskRevision, long actionEpoch, ExcavationFrontier frontier,
        ExcavationProgress progress, ExcavationSuspensionReason suspensionReason) {
        if (taskRevision < 0L) {
            throw new IllegalArgumentException("taskRevision must not be negative");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        this.geometryKey = Objects.requireNonNull(geometryKey, "geometryKey");
        this.frontier = Objects.requireNonNull(frontier, "frontier");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.suspensionReason = Objects.requireNonNull(suspensionReason, "suspensionReason");
        this.taskRevision = taskRevision;
        this.actionEpoch = actionEpoch;
    }

    public static ExcavationCheckpoint start(CylinderExcavationSpec spec, long taskRevision, long actionEpoch) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return new ExcavationCheckpoint(
            spec.getGeometryKey(),
            taskRevision,
            actionEpoch,
            CylinderExcavationGeometry.initialFrontier(spec),
            ExcavationProgress.empty(spec.getVolume()),
            ExcavationSuspensionReason.NONE);
    }

    public static ExcavationCheckpoint restore(CylinderExcavationSpec spec, long taskRevision, long actionEpoch,
        ExcavationFrontier frontier, ExcavationProgress progress, ExcavationSuspensionReason suspensionReason) {
        if (spec == null || progress == null) {
            throw new IllegalArgumentException("spec and progress must not be null");
        }
        CylinderExcavationGeometry.validate(spec, frontier);
        if (progress.getTotal() != spec.getVolume()) {
            throw new IllegalArgumentException("persisted progress total does not match the cylinder volume");
        }
        if (progress.getProcessed() != CylinderExcavationGeometry.processedBefore(spec, frontier)) {
            throw new IllegalArgumentException("persisted progress does not match the exact cylinder frontier");
        }
        if (frontier.isComplete() != (progress.getRemaining() == 0L)) {
            throw new IllegalArgumentException("complete frontier and remaining progress disagree");
        }
        return new ExcavationCheckpoint(
            spec.getGeometryKey(),
            taskRevision,
            actionEpoch,
            frontier,
            progress,
            suspensionReason);
    }

    public String getGeometryKey() {
        return geometryKey;
    }

    public long getTaskRevision() {
        return taskRevision;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public ExcavationFrontier getFrontier() {
        return frontier;
    }

    public ExcavationProgress getProgress() {
        return progress;
    }

    public ExcavationSuspensionReason getSuspensionReason() {
        return suspensionReason;
    }

    public boolean isSuspended() {
        return suspensionReason != ExcavationSuspensionReason.NONE;
    }

    public boolean isComplete() {
        return frontier.isComplete();
    }

    public ExcavationCheckpoint suspend(ExcavationSuspensionReason reason, long nextTaskRevision,
        long nextActionEpoch) {
        if (reason == null || reason == ExcavationSuspensionReason.NONE) {
            throw new IllegalArgumentException("suspension requires an exact non-NONE reason");
        }
        if (isComplete()) {
            throw new IllegalStateException("a completed excavation cannot be suspended");
        }
        requireNewAuthority(nextTaskRevision, nextActionEpoch);
        return new ExcavationCheckpoint(geometryKey, nextTaskRevision, nextActionEpoch, frontier, progress, reason);
    }

    public ExcavationCheckpoint resume(long nextTaskRevision, long nextActionEpoch) {
        if (!isSuspended()) {
            throw new IllegalStateException("only a suspended checkpoint can resume");
        }
        requireNewAuthority(nextTaskRevision, nextActionEpoch);
        return new ExcavationCheckpoint(
            geometryKey,
            nextTaskRevision,
            nextActionEpoch,
            frontier,
            progress,
            ExcavationSuspensionReason.NONE);
    }

    ExcavationCheckpoint applied(ExcavationExecutionResult result) {
        ExcavationProgress nextProgress = progress.advance(result.getTargetResults());
        if (result.getNextFrontier()
            .isComplete() != (nextProgress.getRemaining() == 0L)) {
            throw new IllegalStateException("execution frontier and progress totals disagree");
        }
        return new ExcavationCheckpoint(
            geometryKey,
            Math.addExact(taskRevision, 1L),
            actionEpoch,
            result.getNextFrontier(),
            nextProgress,
            result.getSuspensionReason());
    }

    private void requireNewAuthority(long nextTaskRevision, long nextActionEpoch) {
        if (nextTaskRevision <= taskRevision) {
            throw new IllegalArgumentException("nextTaskRevision must advance");
        }
        if (nextActionEpoch <= actionEpoch) {
            throw new IllegalArgumentException("nextActionEpoch must advance");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExcavationCheckpoint)) {
            return false;
        }
        ExcavationCheckpoint that = (ExcavationCheckpoint) other;
        return taskRevision == that.taskRevision && actionEpoch == that.actionEpoch
            && geometryKey.equals(that.geometryKey)
            && frontier.equals(that.frontier)
            && progress.equals(that.progress)
            && suspensionReason == that.suspensionReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(geometryKey, taskRevision, actionEpoch, frontier, progress, suspensionReason);
    }
}
