package io.github.kaseyawolf2.horizonwright.core.excavation;

public enum ExcavationResultDisposition {
    APPLIED,
    STALE_TASK_REVISION,
    STALE_ACTION_EPOCH,
    STALE_FRONTIER,
    WRONG_GEOMETRY,
    CHECKPOINT_SUSPENDED,
    CHECKPOINT_COMPLETE
}
