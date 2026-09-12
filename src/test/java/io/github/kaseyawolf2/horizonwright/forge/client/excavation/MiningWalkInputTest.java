package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.*;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

public class MiningWalkInputTest {

    @Test
    public void walkingHandoffExtendsBeyondBreakingReachWithoutExtendingBreakingItself() {
        BlockPosition block = new BlockPosition(0, 64, 0);
        assertEquals(5.25, MiningWalkInput.approachDistance(4.5), 0.001);
        assertFalse(MiningWalkInput.withinReach(5.5, 64.5, 0.5, block, 4.5));
        assertTrue(MiningWalkInput.withinReach(5.5, 64.5, 0.5, block, MiningWalkInput.approachDistance(4.5)));
        assertFalse(MiningWalkInput.withinReach(6, 64.5, 0.5, block, MiningWalkInput.approachDistance(4.5)));
    }

    @Test
    public void stopsBeforeEnteringOneBlockRadiusIncludingMomentumAndCrossingSteps() {
        BlockPosition target = new BlockPosition(0, 60, 0);
        assertTrue(MiningWalkInput.tooClose(0.5, 1.4, 0, 0, target));
        assertTrue(MiningWalkInput.tooClose(0.5, 1.5, 0, 0, target));
        assertFalse(MiningWalkInput.tooClose(0.5, 2.5, 0, -0.35, target));
        assertTrue(MiningWalkInput.tooClose(0.5, 1.9, 0, -0.5, target));
        assertTrue(MiningWalkInput.tooClose(-2, 0.5, 5, 0, target));
        assertFalse(MiningWalkInput.tooClose(0.5, 2, 0.35, 0, target));
    }

    @Test
    public void yawUsesNearestEquivalentAngleAndStaysStableDirectlyAboveTarget() {
        assertEquals(179F, MiningWalkInput.stableYaw(179F, 0, 0), 0.001F);
        assertEquals(179F, MiningWalkInput.stableYaw(179F, 0.001, -0.001), 0.001F);
        float yaw = MiningWalkInput.stableYaw(179F, 0.01, -1);
        assertTrue(Math.abs(yaw - 179F) < 3);
        assertTrue(Math.abs(MiningWalkInput.stableYaw(899F, 0.01, -1) - 899F) < 3);
    }

    @Test
    public void projectedWalkingMustKeepTheBlockInsideTheReachMargin() {
        BlockPosition target = new BlockPosition(-16, 60, -16);
        assertTrue(MiningWalkInput.withinReach(-15.5, 62, -15.5, target, 4));
        assertFalse(MiningWalkInput.withinReach(-11, 62, -15.5, target, 4));
        assertFalse(MiningWalkInput.withinReach(-15.5, 62, -15.5, target, 0));
    }
}
