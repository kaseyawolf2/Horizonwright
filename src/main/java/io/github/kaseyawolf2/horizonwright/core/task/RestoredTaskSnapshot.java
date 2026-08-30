package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Optional;

/** Immutable persistence-neutral task record used by controller export and restore. */
public final class RestoredTaskSnapshot {

    private final TaskSpec spec;
    private final TaskState state;
    private final TaskCheckpoint checkpoint;
    private final int retryCount;
    private final long remainingDelayMillis;
    private final TaskSuspensionReason suspensionReason;
    private final BlockedReason blockedReason;
    private final int queuePosition;
    private final long rejectedStaleResults;
    private final String detail;
    private final String sourceScheduleId;

    public RestoredTaskSnapshot(TaskSpec spec, TaskState state, TaskCheckpoint checkpoint, int retryCount,
        long remainingDelayMillis, TaskSuspensionReason suspensionReason, BlockedReason blockedReason,
        int queuePosition, long rejectedStaleResults, String detail, String sourceScheduleId) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        if (retryCount < 0 || remainingDelayMillis < 0L || rejectedStaleResults < 0L) {
            throw new IllegalArgumentException("task counters and delay must not be negative");
        }
        if (suspensionReason == null) {
            throw new IllegalArgumentException("suspensionReason must not be null");
        }
        if (state.isTerminal() ? queuePosition != -1 : queuePosition < 0) {
            throw new IllegalArgumentException("queuePosition is inconsistent with task state");
        }
        if ((state == TaskState.BLOCKED) != (blockedReason != null)) {
            throw new IllegalArgumentException("blocked state and blockedReason must agree");
        }
        if (state == TaskState.SUSPENDED && suspensionReason == TaskSuspensionReason.NONE) {
            throw new IllegalArgumentException("suspended tasks require a suspension reason");
        }
        if (state == TaskState.SUSPENDED && suspensionReason == TaskSuspensionReason.CANCELLATION) {
            throw new IllegalArgumentException("a safely suspended task cannot have pending cancellation");
        }
        if ((state == TaskState.QUEUED || state == TaskState.RUNNING
            || state == TaskState.BLOCKED
            || state == TaskState.COMPLETED
            || state == TaskState.FAILED) && suspensionReason != TaskSuspensionReason.NONE) {
            throw new IllegalArgumentException("task state cannot carry the supplied suspension reason");
        }
        if (state == TaskState.CANCELLED && suspensionReason != TaskSuspensionReason.CANCELLATION) {
            throw new IllegalArgumentException("cancelled tasks require the cancellation reason");
        }
        if (state == TaskState.SUSPENDING && suspensionReason == TaskSuspensionReason.NONE) {
            throw new IllegalArgumentException("suspending tasks require a suspension reason");
        }
        if (sourceScheduleId != null && sourceScheduleId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("sourceScheduleId must not be blank");
        }
        this.spec = spec;
        this.state = state;
        this.checkpoint = checkpoint;
        this.retryCount = retryCount;
        this.remainingDelayMillis = remainingDelayMillis;
        this.suspensionReason = suspensionReason;
        this.blockedReason = blockedReason;
        this.queuePosition = queuePosition;
        this.rejectedStaleResults = rejectedStaleResults;
        this.detail = detail == null ? "" : detail;
        this.sourceScheduleId = sourceScheduleId == null ? null : sourceScheduleId.trim();
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

    public int getRetryCount() {
        return retryCount;
    }

    public long getRemainingDelayMillis() {
        return remainingDelayMillis;
    }

    public TaskSuspensionReason getSuspensionReason() {
        return suspensionReason;
    }

    public Optional<BlockedReason> getBlockedReason() {
        return Optional.ofNullable(blockedReason);
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public long getRejectedStaleResults() {
        return rejectedStaleResults;
    }

    public String getDetail() {
        return detail;
    }

    public Optional<String> getSourceScheduleId() {
        return Optional.ofNullable(sourceScheduleId);
    }
}
