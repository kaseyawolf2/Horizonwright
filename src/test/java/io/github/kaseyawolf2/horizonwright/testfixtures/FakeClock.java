package io.github.kaseyawolf2.horizonwright.testfixtures;

public final class FakeClock {

    private long wallTicks;
    private long connectedTicks;
    private boolean connected;

    public FakeClock() {
        this(0L, 0L, false);
    }

    public FakeClock(long wallTicks, long connectedTicks, boolean connected) {
        if (wallTicks < 0L || connectedTicks < 0L || connectedTicks > wallTicks) {
            throw new IllegalArgumentException("invalid initial clock state");
        }
        this.wallTicks = wallTicks;
        this.connectedTicks = connectedTicks;
        this.connected = connected;
    }

    public synchronized long wallTicks() {
        return wallTicks;
    }

    public synchronized long connectedTicks() {
        return connectedTicks;
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    public synchronized void connect() {
        connected = true;
    }

    public synchronized void disconnect() {
        connected = false;
    }

    public synchronized void advanceTicks(long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("ticks must not be negative");
        }
        wallTicks = Math.addExact(wallTicks, ticks);
        if (connected) {
            connectedTicks = Math.addExact(connectedTicks, ticks);
        }
    }
}
