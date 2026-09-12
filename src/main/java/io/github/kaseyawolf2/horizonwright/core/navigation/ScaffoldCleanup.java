package io.github.kaseyawolf2.horizonwright.core.navigation;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

/** Completion gate: all recorded supports must be removed, never merely abandoned after navigation. */
public final class ScaffoldCleanup implements AutoCloseable {

    private final NavigationBackend navigation;
    private final ActionLease lease;
    private final String id;
    private final int dimension;
    private NavigationHandle moving;
    private BlockPosition target;
    private BlockPosition approachedTarget;
    private boolean directDig;
    private int sequence, settled;

    public ScaffoldCleanup(NavigationBackend navigation, ActionLease lease, String id, int dimension) {
        this.navigation = navigation;
        this.lease = lease;
        this.id = id;
        this.dimension = dimension;
    }

    public boolean poll() {
        if (!lease.isValid()) throw new IllegalStateException("Scaffold cleanup authority was revoked");
        List<BlockPosition> remaining = navigation.remainingScaffolds();
        if (moving != null) {
            NavigationProgress progress = moving.progress();
            if (progress.getState() == NavigationState.FAILED || progress.getState() == NavigationState.CANCELLED)
                throw new IllegalStateException("Pillar removal is incomplete: " + progress.getDetail());
            if (!remaining.contains(target)) {
                moving.cancel();
                moving = null;
                settled = 0;
                approachedTarget = null;
            } else if (progress.getState() == NavigationState.COMPLETED) {
                if (!directDig) {
                    moving.cancel();
                    moving = null;
                    approachedTarget = target;
                } else if (++settled >= 20) throw new IllegalStateException(
                    "Pillar block remains at " + target + "; task completion is withheld");
            }
            return false;
        }
        if (!navigation.readyForScaffoldCleanup()) return false;
        if (remaining.isEmpty()) return true;
        // Highest first makes vertical pillars removable by digging down from their top.
        target = remaining.get(0);
        String requestId = id + "-remove-pillar-" + (++sequence);
        moving = navigation.tryBreakScaffold(target, lease, requestId, dimension);
        directDig = moving != null;
        if (!directDig) {
            if (target.equals(approachedTarget))
                throw new IllegalStateException("Pillar block is not safely reachable after approach at " + target);
            moving = navigation.submit(
                NavigationRequest
                    .adjacentTo(
                        requestId,
                        lease.getEpoch(),
                        dimension,
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        System.nanoTime(),
                        TimeUnit.SECONDS.toNanos(45))
                    .withScaffolding(Collections.emptyList()),
                lease);
        }
        return false;
    }

    @Override
    public void close() {
        if (moving != null) moving.cancel();
        moving = null;
    }
}
