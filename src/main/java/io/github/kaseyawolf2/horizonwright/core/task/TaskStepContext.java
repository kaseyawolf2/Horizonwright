package io.github.kaseyawolf2.horizonwright.core.task;

/** Immutable input for one deterministic runner transition. */
public final class TaskStepContext {

    private final TaskSpec spec;
    private final long actionEpoch;
    private final long nowMillis;
    private final TaskCheckpoint checkpoint;
    private final TaskSuspensionReason suspensionRequest;
    private final TaskActionGateway actions;

    TaskStepContext(TaskSpec spec, long actionEpoch, long nowMillis, TaskCheckpoint checkpoint,
        TaskSuspensionReason suspensionRequest, TaskActionGateway actions) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("nowMillis must not be negative");
        }
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        if (suspensionRequest == null) {
            throw new IllegalArgumentException("suspensionRequest must not be null");
        }
        if (actions == null) {
            throw new IllegalArgumentException("actions must not be null");
        }
        if (actions.getActionEpoch() != actionEpoch) {
            throw new IllegalArgumentException("action gateway epoch must match the step epoch");
        }
        this.spec = spec;
        this.actionEpoch = actionEpoch;
        this.nowMillis = nowMillis;
        this.checkpoint = checkpoint;
        this.suspensionRequest = suspensionRequest;
        this.actions = actions;
    }

    public TaskSpec getSpec() {
        return spec;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public long getNowMillis() {
        return nowMillis;
    }

    public TaskCheckpoint getCheckpoint() {
        return checkpoint;
    }

    public TaskSuspensionReason getSuspensionRequest() {
        return suspensionRequest;
    }

    public TaskActionGateway getActions() {
        return actions;
    }

    public boolean isSuspensionRequested() {
        return suspensionRequest != TaskSuspensionReason.NONE;
    }
}
