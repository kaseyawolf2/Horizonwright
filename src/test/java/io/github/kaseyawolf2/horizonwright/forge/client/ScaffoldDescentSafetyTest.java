package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScaffoldDescentSafetyTest {

    @Test
    public void fullCollisionSupportsLandingRegardlessOfRenderingOpacity() {
        assertTrue(
            ScaffoldDescentSafety.supportsLanding(
                net.minecraft.util.AxisAlignedBB.getBoundingBox(182, 67, 362, 183, 68, 363),
                182,
                67,
                362));
        assertFalse(ScaffoldDescentSafety.supportsLanding(null, 182, 67, 362));
        assertFalse(
            ScaffoldDescentSafety.supportsLanding(
                net.minecraft.util.AxisAlignedBB.getBoundingBox(182, 67, 362, 183, 67.5, 363),
                182,
                67,
                362));
    }

    @Test
    public void acceptsOneBlockDescentOntoNextPillarBlock() {
        assertTrue(ScaffoldDescentSafety.safeLanding(69, 70, y -> false, y -> y == 68));
    }

    @Test
    public void acceptsShortAirGapWhereStandingInsideRemovedBlockIsImpossible() {
        assertTrue(ScaffoldDescentSafety.safeLanding(69, 70, y -> y == 68, y -> y == 67));
    }

    @Test
    public void rejectsUnsafeDropAndUnknownFloor() {
        assertFalse(ScaffoldDescentSafety.safeLanding(69, 70, y -> true, y -> y == 65));
        assertFalse(ScaffoldDescentSafety.safeLanding(69, 70, y -> true, y -> false));
    }

    @Test
    public void rejectsHazardBeforeSafeFloor() {
        assertFalse(ScaffoldDescentSafety.safeLanding(69, 70, y -> false, y -> y == 67));
    }
}
