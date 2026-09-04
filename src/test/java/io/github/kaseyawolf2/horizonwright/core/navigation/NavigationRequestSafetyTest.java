package io.github.kaseyawolf2.horizonwright.core.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public class NavigationRequestSafetyTest {

    @Test
    public void rejectsCoordinatesAndToleranceOutsideTheBoundedGotoEnvelope() {
        assertRejected(0, NavigationRequest.MAX_Y + 1, 0, 0);
        assertRejected(0, 64, 0, NavigationRequest.MAX_TOLERANCE + 1);
        assertRejected(NavigationRequest.MAX_ABS_COORDINATE + 1, 64, 0, 1);
        assertRejected(0, 64, -NavigationRequest.MAX_ABS_COORDINATE - 1, 1);
    }

    @Test
    public void deadlineUsesTheMonotonicClockAndHasAHardMaximum() {
        NavigationRequest request = new NavigationRequest("deadline", 1L, 0, 0, 64, 0, 1, 100L, 5L);

        assertFalse(request.isExpired(104L));
        assertTrue(request.isExpired(105L));

        try {
            new NavigationRequest("too-long", 1L, 0, 0, 64, 0, 1, 100L, NavigationRequest.MAX_RUNTIME_NANOS + 1L);
            fail("timeout above the hard maximum must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("timeout"));
        }
    }

    @Test
    public void aRequestMaySpanWellBeyondTheFormerSmokeTestRadius() {
        NavigationRequest request = new NavigationRequest("long-route", 1L, 0, 4_096, 64, -4_096, 1);

        assertEquals(4_096, request.getX());
        assertEquals(-4_096, request.getZ());
        assertFalse(request.isPlacementAllowed());
    }

    @Test
    public void placementMustBeExplicitlyEnabledForOneNavigationRequest() {
        NavigationRequest adjacent = NavigationRequest
            .adjacentToAllowingPlacement("scaffold-adjacent", 1L, 0, 4, 90, 4, 100L, 1_000L);
        NavigationRequest near = NavigationRequest
            .nearAllowingPlacement("scaffold-near", 1L, 0, 4, 90, 4, 3, 100L, 1_000L);

        assertTrue(adjacent.isPlacementAllowed());
        assertEquals(NavigationGoalKind.ADJACENT, adjacent.getGoalKind());
        assertTrue(near.isPlacementAllowed());
        assertEquals(NavigationGoalKind.RANGE, near.getGoalKind());
        assertEquals(3, near.getTolerance());
    }

    @Test
    public void scopedBreakingCarriesOnlyExplicitBlockIds() {
        NavigationRequest request = NavigationRequest.adjacentToAllowingPlacementAndBreaking(
            "tree-route",
            1L,
            0,
            4,
            90,
            4,
            Arrays.asList("minecraft:leaves", "minecraft:leaves", "Natura:floraleaves"),
            100L,
            1_000L);

        assertTrue(request.isPlacementAllowed());
        assertTrue(request.isBreakingAllowed());
        assertEquals(Arrays.asList("minecraft:leaves", "Natura:floraleaves"), request.getAllowedBreakBlockIds());
    }

    @Test
    public void scopedBreakingRejectsBlankBlockIds() {
        try {
            NavigationRequest.adjacentToAllowingPlacementAndBreaking(
                "invalid-tree-route",
                1L,
                0,
                4,
                90,
                4,
                Arrays.asList("minecraft:leaves", " "),
                100L,
                1_000L);
            fail("blank allowed-break IDs must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("allowed break block IDs"));
        }
    }

    private static void assertRejected(int x, int y, int z, int tolerance) {
        try {
            new NavigationRequest("invalid", 1L, 0, x, y, z, tolerance);
            fail("unsafe navigation request must be rejected");
        } catch (IllegalArgumentException expected) {
            assertFalse(
                expected.getMessage()
                    .isEmpty());
        }
    }
}
