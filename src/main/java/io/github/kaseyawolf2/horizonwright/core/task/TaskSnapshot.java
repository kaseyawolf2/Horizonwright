package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Optional;

/** Immutable externally visible state for one task. */
public final class TaskSnapshot {

    private final TaskSpec spec;
    private final TaskState state;
    private final TaskCheckpoint checkpoint;
    private final long actionEpoch;
    private final int retryCount;
    private final long nextEligibleAtMillis;
    private final TaskSuspensionReason suspensionReason;
    private final BlockedReason blockedReason;
    private final int queuePosition;
    private final long rejectedStaleResults;
    private final String detail;

    TaskSnapshot(TaskSpec spec, TaskState state, TaskCheckpoint checkpoint, long actionEpoch, int retryCount,
        long nextEligibleAtMillis, TaskSuspensionReason suspensionReason, BlockedReason blockedReason,
        int queuePosition, long rejectedStaleResults, String detail) {
        this.spec = spec;
        this.state = state;
        this.checkpoint = checkpoint;
        this.actionEpoch = actionEpoch;
        this.retryCount = retryCount;
        this.nextEligibleAtMillis = nextEligibleAtMillis;
        this.suspensionReason = suspensionReason;
        this.blockedReason = blockedReason;
        this.queuePosition = queuePosition;
        this.rejectedStaleResults = rejectedStaleResults;
        this.detail = detail;
    }

    public TaskSpec getSpec() {
        return spec;
    }

    public TaskState getState() {
        return state;
    }

    public TaskCheckpoint getCheckpoint() {
        return checkpoint;
    }

    /** Returns the most recently assigned runner epoch, or zero if the task has never run. */
    public long getActionEpoch() {
        return actionEpoch;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getNextEligibleAtMillis() {
        return nextEligibleAtMillis;
    }

    public TaskSuspensionReason getSuspensionReason() {
        return suspensionReason;
    }

    public Optional<BlockedReason> getBlockedReason() {
        return Optional.ofNullable(blockedReason);
    }

    /** Zero-based position in the visible lane queue, or -1 for a terminal task. */
    public int getQueuePosition() {
        return queuePosition;
    }

    public long getRejectedStaleResults() {
        return rejectedStaleResults;
    }

    public String getDetail() {
        return detail;
    }
}
