package io.github.kaseyawolf2.horizonwright.core.navigation;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

public interface NavigationBackend {

    BackendAvailability availability();

    NavigationHandle submit(NavigationRequest request, ActionLease movementLease);

    /** Starts an exact, reachable support dig, or returns null when a walking approach is needed. */
    default NavigationHandle tryBreakScaffold(BlockPosition target, ActionLease lease, String requestId,
        int dimension) {
        return null;
    }

    default void configureScaffoldJournal(java.nio.file.Path profileDirectory) {}

    default java.util.List<BlockPosition> remainingScaffolds() {
        return java.util.Collections.emptyList();
    }

    default boolean readyForScaffoldCleanup() {
        return true;
    }

    /** Performs client-thread-only teardown deferred out of pathing callbacks. */
    default void clientTick() {}
}
