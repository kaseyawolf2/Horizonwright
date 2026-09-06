package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TreeToolCostTest {

    @Test
    public void wholeTreeAndClimbingBeatFasterSingleBlockTool() {
        assertTrue(
            TreeToolCost.estimate(.05F, 20, 2, 10, true, true) < TreeToolCost.estimate(.2F, 20, 2, 10, false, false));
    }

    @Test
    public void loneLogUsesActualSingleBlockSpeed() {
        assertTrue(
            TreeToolCost.estimate(.2F, 1, 1, 0, false, false) < TreeToolCost.estimate(.05F, 1, 1, 0, true, true));
    }

    @Test
    public void cubeCoverageBenefitsFallbackButEmptyCubeDoesNot() {
        assertTrue(
            TreeToolCost.estimate(.1F, 8, 8, 0, true, false) < TreeToolCost.estimate(.2F, 8, 8, 0, false, false));
        assertEquals(Double.POSITIVE_INFINITY, TreeToolCost.estimate(0F, 8, 8, 0, true, true), 0D);
    }
}
