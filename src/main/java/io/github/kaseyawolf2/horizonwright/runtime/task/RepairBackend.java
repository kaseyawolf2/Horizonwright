package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

/** Pinned-version repair container adapter boundary. */
public interface RepairBackend {

    RepairBackendAvailability availability();

    RepairObservationResult observe(RepairObservationRequest request);

    RepairActionHandle execute(RepairActionRequest request, ActionLease lease);
}
