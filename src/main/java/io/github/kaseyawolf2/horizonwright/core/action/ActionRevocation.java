package io.github.kaseyawolf2.horizonwright.core.action;

public final class ActionRevocation {

    private final long revokedEpoch;
    private final long newEpoch;
    private final ActionRevocationReason reason;

    ActionRevocation(long revokedEpoch, long newEpoch, ActionRevocationReason reason) {
        if (revokedEpoch < 1L || newEpoch <= revokedEpoch) {
            throw new IllegalArgumentException("action epochs must advance monotonically");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        this.revokedEpoch = revokedEpoch;
        this.newEpoch = newEpoch;
        this.reason = reason;
    }

    public long getRevokedEpoch() {
        return revokedEpoch;
    }

    public long getNewEpoch() {
        return newEpoch;
    }

    public ActionRevocationReason getReason() {
        return reason;
    }
}
