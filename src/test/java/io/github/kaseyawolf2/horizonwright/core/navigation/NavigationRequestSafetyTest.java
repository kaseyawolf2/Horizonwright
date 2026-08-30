package io.github.kaseyawolf2.horizonwright.core.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
