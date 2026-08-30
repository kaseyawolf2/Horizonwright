package io.github.kaseyawolf2.horizonwright.core.task;

/** Epoch-tagged result of advancing a resumable task runner by one step. */
public abstract class StepResult {

    public enum Kind {
        PROGRESS,
        WAIT,
        SAFE_SUSPENSION,
        COMPLETED,
        FAILED,
        BLOCKED
    }

    private final Kind kind;
    private final long actionEpoch;
    private final TaskCheckpoint checkpoint;
    private final String detail;

    private StepResult(Kind kind, long actionEpoch, TaskCheckpoint checkpoint, String detail) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        this.kind = kind;
        this.actionEpoch = actionEpoch;
        this.checkpoint = checkpoint;
        this.detail = detail == null ? "" : detail;
    }

    public static Progress progress(long actionEpoch, TaskCheckpoint checkpoint, String detail) {
        return new Progress(actionEpoch, checkpoint, detail);
    }

    public static Wait waitFor(long actionEpoch, TaskCheckpoint checkpoint, long delayMillis, String detail) {
        return new Wait(actionEpoch, checkpoint, delayMillis, detail);
    }

    public static SafeSuspension safeSuspension(long actionEpoch, TaskCheckpoint checkpoint, String detail) {
        return new SafeSuspension(actionEpoch, checkpoint, detail);
    }

    public static Completed completed(long actionEpoch, TaskCheckpoint checkpoint, String detail) {
        return new Completed(actionEpoch, checkpoint, detail);
    }

    public static Failed failed(long actionEpoch, TaskCheckpoint checkpoint, String detail, boolean retryable) {
        return new Failed(actionEpoch, checkpoint, detail, retryable);
    }

    public static Blocked blocked(long actionEpoch, TaskCheckpoint checkpoint, BlockedReason reason) {
        return new Blocked(actionEpoch, checkpoint, reason);
    }

    public Kind getKind() {
        return kind;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public TaskCheckpoint getCheckpoint() {
        return checkpoint;
    }

    public String getDetail() {
        return detail;
    }

    public static final class Progress extends StepResult {

        private Progress(long actionEpoch, TaskCheckpoint checkpoint, String detail) {
            super(Kind.PROGRESS, actionEpoch, checkpoint, detail);
        }
    }

    public static final class Wait extends StepResult {

        private final long delayMillis;

        private Wait(long actionEpoch, TaskCheckpoint checkpoint, long delayMillis, String detail) {
            super(Kind.WAIT, actionEpoch, checkpoint, detail);
            if (delayMillis < 0L) {
                throw new IllegalArgumentException("delayMillis must not be negative");
            }
            this.delayMillis = delayMillis;
        }

        public long getDelayMillis() {
            return delayMillis;
        }
    }

    public static final class SafeSuspension extends StepResult {

        private SafeSuspension(long actionEpoch, TaskCheckpoint checkpoint, String detail) {
            super(Kind.SAFE_SUSPENSION, actionEpoch, checkpoint, detail);
        }
    }

    public static final class Completed extends StepResult {

        private Completed(long actionEpoch, TaskCheckpoint checkpoint, String detail) {
            super(Kind.COMPLETED, actionEpoch, checkpoint, detail);
        }
    }

    public static final class Failed extends StepResult {

        private final boolean retryable;

        private Failed(long actionEpoch, TaskCheckpoint checkpoint, String detail, boolean retryable) {
            super(Kind.FAILED, actionEpoch, checkpoint, detail);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    public static final class Blocked extends StepResult {

        private final BlockedReason reason;

        private Blocked(long actionEpoch, TaskCheckpoint checkpoint, BlockedReason reason) {
            super(Kind.BLOCKED, actionEpoch, checkpoint, requireReason(reason).getDetail());
            this.reason = reason;
        }

        public BlockedReason getReason() {
            return reason;
        }

        private static BlockedReason requireReason(BlockedReason reason) {
            if (reason == null) {
                throw new IllegalArgumentException("reason must not be null");
            }
            return reason;
        }
    }
}
