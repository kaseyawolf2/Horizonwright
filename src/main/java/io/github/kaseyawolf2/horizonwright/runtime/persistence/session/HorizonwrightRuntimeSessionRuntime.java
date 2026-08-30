package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;

/** Pure-Java session adapter over one fresh {@link HorizonwrightRuntime}. */
public final class HorizonwrightRuntimeSessionRuntime implements RuntimeSessionRuntime {

    private final HorizonwrightRuntime runtime;
    private final RuntimeSessionConnection connection;
    private final RuntimeSessionEnvironmentSource environmentSource;
    private final RuntimeSessionDeathStateBoundary deathState;

    private boolean restoreAttempted;
    private boolean restored;
    private boolean closed;

    HorizonwrightRuntimeSessionRuntime(HorizonwrightRuntime runtime, RuntimeSessionConnection connection,
        RuntimeSessionEnvironmentSource environmentSource, RuntimeSessionDeathStateBoundary deathState) {
        if (runtime == null || connection == null || environmentSource == null || deathState == null) {
            throw new IllegalArgumentException("runtime, connection, environmentSource, and deathState are required");
        }
        this.runtime = runtime;
        this.connection = connection;
        this.environmentSource = environmentSource;
        this.deathState = deathState;
    }

    @Override
    public synchronized void restore(RuntimeEnvelope envelope) {
        ensureOpen();
        if (restoreAttempted) {
            throw new IllegalStateException("runtime session restore has already been attempted");
        }
        restoreAttempted = true;
        if (envelope == null) {
            throw new IllegalArgumentException("envelope must not be null");
        }
        if (!ProfileRuntimeSession.hasSameDurableBinding(connection.getIdentity(), envelope)) {
            throw new IllegalArgumentException("runtime envelope does not belong to this connection profile/world");
        }
        runtime.restoreControllerState(envelope.getTaskControllerState());
        deathState.restore(envelope.getUnresolvedDeathState());
        restored = true;
    }

    @Override
    public synchronized void clientTick() {
        ensureRestored();
        ScheduleEnvironment environment = environmentSource.snapshot(connection);
        if (environment == null) {
            throw new IllegalStateException("runtime session environment source returned null");
        }
        if (!environment.isConnected()) {
            throw new IllegalStateException("an active runtime session requires a connected schedule environment");
        }
        runtime.clientTick(environment);
    }

    @Override
    public synchronized IHorizonwrightController getController() {
        ensureRestored();
        return runtime.getController();
    }

    @Override
    public synchronized UnresolvedDeathState snapshotUnresolvedDeathState() {
        ensureRestored();
        return deathState.snapshot();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        runtime.close();
    }

    private void ensureRestored() {
        ensureOpen();
        if (!restored) {
            throw new IllegalStateException("runtime session must restore before use");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("runtime session is closed");
        }
    }
}
