package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

/**
 * Typed boundary for a version-tested excavation integration.
 *
 * <p>
 * An implementation must observe on the client thread, verify the action request's fingerprint before sending any
 * gameplay action, and expose CONFIRMED only after a post-action observation proves the exact outcome.
 * An explicitly asynchronous request may expose PENDING_CONFIRMATION after local removal and
 * packet drainage; subsequent progress calls must be observation-only after its lease is released.
 */
public interface ExcavationBackend {

    interface DropCollection extends AutoCloseable {

        boolean poll();

        String detail();

        @Override
        void close();
    }

    default boolean supportsDropCollection() {
        return false;
    }

    default boolean readyForDropCollection() {
        return true;
    }

    default DropCollection collectDrops(String taskId,
        io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec area, ActionLease lease) {
        throw new UnsupportedOperationException("Drop collection is unavailable");
    }

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
