package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;

/** Monotonic sequence source owned by exactly one live client connection. */
public final class ConnectionSafetyEventStampSource {

    private final long connectionEpoch;
    private long sequence;
    private long lastClientTick = -1L;
    private boolean open = true;

    public ConnectionSafetyEventStampSource(long connectionEpoch) {
        if (connectionEpoch <= 0L) {
            throw new IllegalArgumentException("connectionEpoch must be positive");
        }
        this.connectionEpoch = connectionEpoch;
    }

    public synchronized SafetyEventStamp next(long clientTick) {
        if (!open) {
            throw new IllegalStateException("the connection stamp source is retired");
        }
        if (clientTick < 0L || clientTick < lastClientTick) {
            throw new IllegalArgumentException("client ticks must be non-negative and monotonically nondecreasing");
        }
        if (sequence == Long.MAX_VALUE) {
            throw new IllegalStateException("connection event sequence exhausted");
        }
        lastClientTick = clientTick;
        return new SafetyEventStamp(connectionEpoch, ++sequence, clientTick);
    }

    public synchronized void retire() {
        open = false;
    }

    /** Linearizes a final packet write with connection retirement. */
    public synchronized boolean runIfOpen(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (!open) {
            return false;
        }
        action.run();
        return true;
    }

    public synchronized boolean isOpen() {
        return open;
    }

    public long getConnectionEpoch() {
        return connectionEpoch;
    }
}
