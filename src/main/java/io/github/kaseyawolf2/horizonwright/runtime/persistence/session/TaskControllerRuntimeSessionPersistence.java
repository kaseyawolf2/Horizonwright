package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceException;

/** Production-neutral adapter from a runtime session to the atomic task-controller persistence coordinator. */
public final class TaskControllerRuntimeSessionPersistence implements RuntimeSessionPersistence {

    private final WorldProfileIdentity expectedIdentity;
    private final TaskControllerPersistenceCoordinator coordinator;

    public TaskControllerRuntimeSessionPersistence(HorizonwrightPersistenceStore store,
        WorldProfileIdentity expectedIdentity) {
        if (store == null || expectedIdentity == null) {
            throw new IllegalArgumentException("store and expectedIdentity must not be null");
        }
        this.expectedIdentity = expectedIdentity;
        this.coordinator = new TaskControllerPersistenceCoordinator(store, expectedIdentity);
    }

    @Override
    public WorldProfileIdentity getExpectedIdentity() {
        return expectedIdentity;
    }

    @Override
    public RuntimeEnvelope load() throws TaskControllerPersistenceException {
        return coordinator.load();
    }

    @Override
    public RuntimeEnvelope save(long writtenAtEpochMillis, RuntimeSessionConnection connection,
        RuntimeSessionRuntime runtime) throws TaskControllerPersistenceException {
        if (connection == null || runtime == null) {
            throw new IllegalArgumentException("connection and runtime must not be null");
        }
        if (!ProfileRuntimeSession.hasSameDurableBinding(expectedIdentity, connection.getIdentity())) {
            throw new IllegalArgumentException("connection does not belong to this persistence partition");
        }
        return coordinator.save(
            writtenAtEpochMillis,
            connection.getConnectionEpoch(),
            runtime.snapshotUnresolvedDeathState(),
            runtime.getController());
    }
}
