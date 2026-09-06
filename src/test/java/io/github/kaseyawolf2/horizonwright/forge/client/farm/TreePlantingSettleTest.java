package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TreePlantingSettleTest {

    @Test
    public void holdsForTenTicksThenAllowsRevalidation() {
        assertEquals(10, TreePlantingRetry.settleTicksRemaining(0));
        assertEquals(1, TreePlantingRetry.settleTicksRemaining(9));
        assertEquals(0, TreePlantingRetry.settleTicksRemaining(10));
        assertEquals(0, TreePlantingRetry.settleTicksRemaining(40));
        assertEquals(10, TreePlantingRetry.settleTicksRemaining(-1));
    }
}
