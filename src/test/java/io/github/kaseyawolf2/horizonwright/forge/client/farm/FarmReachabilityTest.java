package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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

    @Test
    public void collectionUsesCurrentFootLayerAndAnAdjacentTolerance() {
        assertEquals(66, FarmReachability.collectionFeetY(66.62D));
        assertEquals(0, FarmReachability.collectionFeetY(-0.1D));
        assertEquals(255, FarmReachability.collectionFeetY(300.0D));
        assertEquals(1, FarmReachability.DROP_COLLECTION_TOLERANCE);
    }

    @Test
    public void probesCenterAndAllSixInsetFaces() {
        double[][] probes = FarmReachability.interactionProbes(10, 20, -4);
        assertEquals(7, probes.length);
        assertArrayEquals(new double[] { 10.5D, 20.5D, -3.5D }, probes[0], 0.0D);
        assertEquals(10.001D, probes[1][0], 0.0000001D);
        assertEquals(10.999D, probes[2][0], 0.0000001D);
        assertEquals(20.001D, probes[3][1], 0.0000001D);
        assertEquals(20.999D, probes[4][1], 0.0000001D);
        assertEquals(-3.999D, probes[5][2], 0.0000001D);
        assertEquals(-3.001D, probes[6][2], 0.0000001D);
    }
}
