package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

/** Versioned container adapter boundary used by a verified unload runner. */
public interface UnloadBackend {

    UnloadBackendAvailability availability();

    UnloadObservationResult observe(UnloadObservationRequest request);

    UnloadActionHandle execute(UnloadActionRequest request, ActionLease lease);
}
