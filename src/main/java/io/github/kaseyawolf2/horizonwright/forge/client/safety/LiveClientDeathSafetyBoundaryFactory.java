package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.IdentityHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyPolicy;
import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketBridgeFactory;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.RuntimeSessionConnection;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.RuntimeSessionDeathStateBoundary;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.RuntimeSessionDeathStateBoundaryFactory;

/** Correlates each fresh runtime with the same live death boundary used by its packet installer. */
public final class LiveClientDeathSafetyBoundaryFactory implements RuntimeSessionDeathStateBoundaryFactory {

    private final HorizonwrightPersistenceStore store;
    private final DeathSafetyPolicy policy;
    private final Map<HorizonwrightRuntime, LiveClientDeathSafetyBoundary> boundaries = new IdentityHashMap<>();

    public LiveClientDeathSafetyBoundaryFactory(HorizonwrightPersistenceStore store, DeathSafetyPolicy policy) {
        if (store == null || policy == null) {
            throw new IllegalArgumentException("persistence store and death-safety policy must not be null");
        }
        this.store = store;
        this.policy = policy;
    }

    @Override
    public synchronized RuntimeSessionDeathStateBoundary create(HorizonwrightRuntime runtime,
        RuntimeSessionConnection connection) {
        if (runtime == null || connection == null) {
            throw new IllegalArgumentException("runtime and connection must not be null");
        }
        if (boundaries.containsKey(runtime)) {
            throw new IllegalStateException("runtime already owns a live death-safety boundary");
        }
        LiveClientDeathSafetyBoundary boundary = new LiveClientDeathSafetyBoundary(
            runtime,
            connection,
            store,
            policy,
            retired -> remove(runtime, retired));
        boundaries.put(runtime, boundary);
        return boundary;
    }

    public synchronized DeathSafetyPacketBridgeFactory requirePacketBridgeFactory(HorizonwrightRuntime runtime) {
        LiveClientDeathSafetyBoundary boundary = boundaries.get(runtime);
        if (boundary == null) {
            throw new IllegalStateException("runtime has no live death-safety boundary");
        }
        return boundary;
    }

    private synchronized void remove(HorizonwrightRuntime runtime, LiveClientDeathSafetyBoundary retired) {
        if (boundaries.get(runtime) == retired) {
            boundaries.remove(runtime);
        }
    }
}
