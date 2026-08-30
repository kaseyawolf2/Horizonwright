package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Connection-scoped ordering token attached to every client or network observation. */
public final class SafetyEventStamp {

    private final long connectionEpoch;
    private final long eventSequence;
    private final long clientTick;

    public SafetyEventStamp(long connectionEpoch, long eventSequence, long clientTick) {
        if (connectionEpoch <= 0L) {
            throw new IllegalArgumentException("connectionEpoch must be positive");
        }
        if (eventSequence <= 0L) {
            throw new IllegalArgumentException("eventSequence must be positive");
        }
        if (clientTick < 0L) {
            throw new IllegalArgumentException("clientTick must not be negative");
        }
        this.connectionEpoch = connectionEpoch;
        this.eventSequence = eventSequence;
        this.clientTick = clientTick;
    }

    public long getConnectionEpoch() {
        return connectionEpoch;
    }

    public long getEventSequence() {
        return eventSequence;
    }

    public long getClientTick() {
        return clientTick;
    }
}
