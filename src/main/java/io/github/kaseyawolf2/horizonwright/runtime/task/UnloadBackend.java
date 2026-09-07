package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

/** Versioned container adapter boundary used by a verified unload runner. */
public interface UnloadBackend {

    UnloadBackendAvailability availability();

    /** Optional approach/open operation. Null means manual access is required. No inventory transfers here. */
    default UnloadActionHandle accessStorage(String requestId, String storageId, long epoch, ActionLease lease) {
        return null;
    }

    /** Optional synchronized close after exact observation proves no eligible stacks remain. */
    default UnloadActionHandle closeStorage(String requestId, String storageId, long epoch, ActionLease lease) {
        return null;
    }

    UnloadObservationResult observe(UnloadObservationRequest request);

    UnloadActionHandle execute(UnloadActionRequest request, ActionLease lease);
}
