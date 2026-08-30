package io.github.kaseyawolf2.horizonwright.core.excavation;

/** Applies execution results only when both revision stamps and exact frontier still match. */
public final class ExcavationReducer {

    private ExcavationReducer() {}

    public static ExcavationResultApplication apply(ExcavationCheckpoint checkpoint, ExcavationExecutionResult result) {
        if (checkpoint == null || result == null) {
            throw new IllegalArgumentException("checkpoint and result must not be null");
        }
        ExcavationResultDisposition rejection = rejection(checkpoint, result);
        if (rejection != null) {
            return new ExcavationResultApplication(rejection, checkpoint);
        }
        return new ExcavationResultApplication(ExcavationResultDisposition.APPLIED, checkpoint.applied(result));
    }

    private static ExcavationResultDisposition rejection(ExcavationCheckpoint checkpoint,
        ExcavationExecutionResult result) {
        if (!checkpoint.getGeometryKey()
            .equals(result.getGeometryKey())) {
            return ExcavationResultDisposition.WRONG_GEOMETRY;
        }
        if (checkpoint.getTaskRevision() != result.getTaskRevision()) {
            return ExcavationResultDisposition.STALE_TASK_REVISION;
        }
        if (checkpoint.getActionEpoch() != result.getActionEpoch()) {
            return ExcavationResultDisposition.STALE_ACTION_EPOCH;
        }
        if (!checkpoint.getFrontier()
            .equals(result.getStartFrontier())) {
            return ExcavationResultDisposition.STALE_FRONTIER;
        }
        if (checkpoint.isComplete()) {
            return ExcavationResultDisposition.CHECKPOINT_COMPLETE;
        }
        if (checkpoint.isSuspended()) {
            return ExcavationResultDisposition.CHECKPOINT_SUSPENDED;
        }
        return null;
    }
}
