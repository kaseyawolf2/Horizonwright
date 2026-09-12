package io.github.kaseyawolf2.horizonwright.core.navigation;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

public class ScaffoldCleanupTest {

    private final InMemoryActionBroker broker = new InMemoryActionBroker();
    private final ActionLease lease = broker
        .tryAcquire("tree", EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.DIG))
        .get();
    private final Backend backend = new Backend();
    private final ScaffoldCleanup cleanup = new ScaffoldCleanup(backend, lease, "tree", 0);

    @Test
    public void removesEverySupportAndWaitsForPacketDrain() {
        backend.directAvailable = true;
        backend.blocks.add(new BlockPosition(3, 70, 4));
        backend.blocks.add(new BlockPosition(3, 69, 4));
        assertFalse(cleanup.poll());
        assertEquals(70, backend.directTarget.getY());
        assertEquals(0, backend.submissions);
        backend.state = NavigationState.COMPLETED;
        assertFalse(cleanup.poll());
        backend.blocks.remove(0);
        assertFalse(cleanup.poll());
        backend.ready = false;
        assertFalse(cleanup.poll());
        assertEquals(1, backend.directStarts);
        backend.ready = true;
        assertFalse(cleanup.poll());
        assertEquals(69, backend.directTarget.getY());
        backend.blocks.clear();
        assertFalse(cleanup.poll());
        backend.ready = false;
        assertFalse(cleanup.poll());
        backend.ready = true;
        assertTrue(cleanup.poll());
    }

    @Test(expected = IllegalStateException.class)
    public void arrivalWithoutRemovingBlockCannotFinish() {
        backend.blocks.add(new BlockPosition(0, 64, 0));
        cleanup.poll();
        backend.state = NavigationState.COMPLETED;
        for (int i = 0; i < 20; i++) assertFalse(cleanup.poll());
    }

    @Test(expected = IllegalStateException.class)
    public void unreachableCleanupCannotFinish() {
        backend.blocks.add(new BlockPosition(0, 64, 0));
        cleanup.poll();
        backend.state = NavigationState.FAILED;
        cleanup.poll();
    }

    @Test
    public void cancellationKeepsSupportsPendingForResume() {
        backend.blocks.add(new BlockPosition(0, 64, 0));
        cleanup.poll();
        cleanup.close();
        assertEquals(1, backend.blocks.size());
        assertTrue(backend.cancelled);
    }

    @Test(expected = IllegalStateException.class)
    public void revokedAuthorityCannotDig() {
        broker.revokeAll();
        cleanup.poll();
    }

    @Test
    public void reachablePillarIsDugWithoutTryingToPathInsideIt() {
        backend.directAvailable = true;
        backend.blocks.add(new BlockPosition(182, 69, 362));
        assertFalse(cleanup.poll());
        assertEquals(1, backend.directStarts);
        assertEquals(0, backend.submissions);
        assertFalse(cleanup.poll());
        backend.blocks.clear();
        assertFalse(cleanup.poll());
        assertTrue(cleanup.poll());
    }

    @Test
    public void distantPillarApproachesAdjacentThenHandsOffToExactDig() {
        backend.blocks.add(new BlockPosition(182, 69, 362));
        assertFalse(cleanup.poll());
        assertEquals(NavigationGoalKind.ADJACENT, backend.request.getGoalKind());
        assertFalse(backend.request.isPlacementAllowed());
        assertTrue(
            backend.request.getAllowedBreakBlockIds()
                .isEmpty());
        backend.state = NavigationState.COMPLETED;
        assertFalse(cleanup.poll());
        backend.ready = false;
        backend.directAvailable = true;
        assertFalse(cleanup.poll());
        assertEquals(0, backend.directStarts);
        backend.ready = true;
        assertFalse(cleanup.poll());
        assertEquals(1, backend.directStarts);
        assertEquals(1, backend.submissions);
    }

    @Test
    public void cancellingDirectDigKeepsSupportPending() {
        backend.directAvailable = true;
        backend.blocks.add(new BlockPosition(182, 69, 362));
        cleanup.poll();
        cleanup.close();
        assertTrue(backend.cancelled);
        assertEquals(1, backend.blocks.size());
    }

    private static final class Backend implements NavigationBackend {

        final List<BlockPosition> blocks = new ArrayList<>();
        NavigationRequest request;
        NavigationState state = NavigationState.MOVING;
        boolean ready = true, cancelled, directAvailable;
        int submissions, directStarts;
        BlockPosition directTarget;

        @Override
        public NavigationHandle tryBreakScaffold(BlockPosition target, ActionLease lease, String id, int dimension) {
            if (!directAvailable) return null;
            directTarget = target;
            directStarts++;
            state = NavigationState.MOVING;
            return new NavigationHandle() {

                public String getRequestId() {
                    return id;
                }

                public NavigationProgress progress() {
                    return new NavigationProgress(id, lease.getEpoch(), state, "dig");
                }

                public void cancel() {
                    cancelled = true;
                }
            };
        }

        public BackendAvailability availability() {
            return BackendAvailability.available("test");
        }

        public List<BlockPosition> remainingScaffolds() {
            return new ArrayList<>(blocks);
        }

        public boolean readyForScaffoldCleanup() {
            return ready;
        }

        public NavigationHandle submit(NavigationRequest request, ActionLease lease) {
            this.request = request;
            submissions++;
            state = NavigationState.MOVING;
            cancelled = false;
            return new NavigationHandle() {

                public String getRequestId() {
                    return request.getRequestId();
                }

                public NavigationProgress progress() {
                    return new NavigationProgress(request.getRequestId(), lease.getEpoch(), state, "test");
                }

                public void cancel() {
                    cancelled = true;
                }
            };
        }
    }
}
