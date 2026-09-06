package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TreeInteractionGeometryTest {

    @Test
    public void plantingRayEntersSupportingSoilCellAtEveryValidPlantingHeight() {
        for (int y = 1; y <= 255; y++) {
            double endpoint = TreeInteractionGeometry.supportProbeY(y);
            assertEquals(y - 1, (int) Math.floor(endpoint));
            assertTrue(endpoint > y - 0.01D);
            assertTrue(endpoint < y);
        }
    }
}
