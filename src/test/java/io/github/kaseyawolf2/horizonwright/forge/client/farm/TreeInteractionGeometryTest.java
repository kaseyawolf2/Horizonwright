package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TreeInteractionGeometryTest {

    @Test
    public void standBackViewsAreDistinctAndNearestFirst() {
        int[][] points = TreeInteractionGeometry.standBackPositions(10, 79, -20, 5, -19.5D);
        assertEquals(8, points[0][0]);
        java.util.Set<String> unique = new java.util.HashSet<>();
        for (int[] p : points) {
            assertEquals(79, p[1]);
            assertEquals(2, Math.abs(p[0] - 10) + Math.abs(p[2] + 20));
            unique.add(p[0] + "," + p[2]);
        }
        assertEquals(4, unique.size());
    }

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
