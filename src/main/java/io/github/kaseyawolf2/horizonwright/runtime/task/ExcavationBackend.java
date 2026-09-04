package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

/**
 * Typed boundary for a version-tested excavation integration.
 *
 * <p>
 * An implementation must observe on the client thread, verify the action request's fingerprint before sending any
 * gameplay action, and expose CONFIRMED only after a post-action observation proves the exact outcome.
 */
public interface ExcavationBackend {

    ExcavationBackendAvailability availability();

    ExcavationObservationResult observe(ExcavationObservationRequest request);

    ExcavationActionHandle execute(ExcavationActionRequest request, ActionLease actionLease);

    /** Reports whether this backend can observe and place managed-quarry infrastructure. */
    default ExcavationBackendAvailability managedQuarryAvailability() {
        return ExcavationBackendAvailability.unavailable("Managed-quarry infrastructure is not supported");
    }

    default ManagedQuarryObservationResult observeManagedQuarry(ManagedQuarryObservationRequest request) {
        throw new UnsupportedOperationException("Managed-quarry infrastructure observation is not supported");
    }

    default ManagedQuarryActionHandle executeManagedQuarry(ManagedQuarryActionRequest request,
        ActionLease actionLease) {
        throw new UnsupportedOperationException("Managed-quarry infrastructure execution is not supported");
    }
}
