package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TreePlantingRetryTest {

    @Test
    public void missingPlacementWaitsForServerBeforeRetryingAndStopsAfterThreeRetries() {
        assertFalse(TreePlantingRetry.ready(0));
        assertFalse(TreePlantingRetry.ready(39));
        assertTrue(TreePlantingRetry.ready(40));
        for (int retries = 0; retries < 3; retries++) assertFalse(TreePlantingRetry.exhausted(retries));
        assertTrue(TreePlantingRetry.exhausted(3));
    }
}
