package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VerticalMiningStabilityTest {

    @Test
    public void jumpPlaceSequenceCannotHandOffToMiningUntilSettledOnSupport() {
        // Reach can become true anywhere in this sequence; only the settled support permits takeover.
        assertFalse(VerticalMiningStability.isStable(true, 70, 70, 0.42));
        assertFalse(VerticalMiningStability.isStable(false, 70.42, 70, 0.33));
        assertFalse(VerticalMiningStability.isStable(false, 71.2, 71.2, 0));
        assertFalse(VerticalMiningStability.isStable(false, 71.1, 71.2, -0.1));
        assertFalse(VerticalMiningStability.isStable(true, 71, 71.1, -0.0784));
        assertTrue(VerticalMiningStability.isStable(true, 71, 71, -0.0784000015));
    }

    @Test
    public void supportedHorizontalWalkingDoesNotBlockMining() {
        assertTrue(VerticalMiningStability.isStable(true, 64, 64, 0));
        assertTrue(VerticalMiningStability.isStable(true, 64, 64, -0.0784));
    }

    @Test
    public void fallingThroughReachAndSteppingUpCannotStartOrContinueDigging() {
        assertFalse(VerticalMiningStability.isStable(false, 65, 65.2, -0.2));
        assertFalse(VerticalMiningStability.isStable(true, 64.5, 64, -0.0784));
        assertFalse(VerticalMiningStability.isStable(true, 64, 64, -0.3));
    }

    @Test
    public void airborneApexIsNotStationaryFooting() {
        assertFalse(VerticalMiningStability.isStable(false, 65.25, 65.25, 0));
    }

    @Test
    public void invalidMotionDataCannotAuthorizeBreaking() {
        assertFalse(VerticalMiningStability.isStable(true, Double.NaN, 64, 0));
        assertFalse(VerticalMiningStability.isStable(true, 64, 64, Double.POSITIVE_INFINITY));
    }
}
