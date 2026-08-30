package io.github.kaseyawolf2.horizonwright.core.task;

/** Synchronous notification that a runner's current action epoch has been revoked. */
public final class TaskInterruption {

    private final TaskInterruptionReason reason;
    private final long revokedEpoch;
    private final long replacementEpoch;

    public TaskInterruption(TaskInterruptionReason reason, long revokedEpoch, long replacementEpoch) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        if (revokedEpoch < 1L) {
            throw new IllegalArgumentException("revokedEpoch must be positive");
        }
        if (replacementEpoch <= revokedEpoch) {
            throw new IllegalArgumentException("replacementEpoch must be greater than revokedEpoch");
        }
        this.reason = reason;
        this.revokedEpoch = revokedEpoch;
        this.replacementEpoch = replacementEpoch;
    }

    public TaskInterruptionReason getReason() {
        return reason;
    }

    public long getRevokedEpoch() {
        return revokedEpoch;
    }

    public long getReplacementEpoch() {
        return replacementEpoch;
    }
}
