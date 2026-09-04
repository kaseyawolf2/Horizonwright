package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FarmReachabilityTest {

    @Test
    public void clearNullPlantRayIsReachableAtNormalDistance() {
        assertTrue(FarmReachability.canInteract(2.84D, 25.0D, false, false));
    }

    @Test
    public void exactCropHitIsReachableButAnObstructingBlockIsNot() {
        assertTrue(FarmReachability.canInteract(2.84D, 25.0D, true, true));
        assertFalse(FarmReachability.canInteract(2.84D, 25.0D, true, false));
    }

    @Test
    public void clearRayNeverOverridesReachDistance() {
        assertFalse(FarmReachability.canInteract(25.01D, 25.0D, false, false));
    }
}
