package io.github.kaseyawolf2.horizonwright.core.excavation;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class InwardSpiralTest {

    @Test
    public void coversRectanglesIncludingThinEvenAndNegativeBounds() {
        for (int width = 1; width <= 7; width++) for (int depth = 1; depth <= 7; depth++) {
            verify(
                CylinderExcavationSpec
                    .rectangle(0, -18, -19 + width, -3, -4 + depth, 60, 62, ExcavationMode.CLEAN_VOLUME)
                    .withSpiral());
        }
    }

    @Test
    public void coversClippedCirclesIncludingSingleton() {
        for (int radius = 0; radius <= 9; radius++)
            verify(new CylinderExcavationSpec(0, -16, -7, radius, 40, 42, ExcavationMode.CLEAN_VOLUME).withSpiral());
    }

    @Test
    public void startsClockwiseAndKeepsLegacyGeometryDistinct() {
        CylinderExcavationSpec legacy = CylinderExcavationSpec
            .rectangle(0, 0, 2, 0, 2, 60, 60, ExcavationMode.CLEAN_VOLUME);
        CylinderExcavationSpec spiral = legacy.withSpiral();
        assertFalse(legacy.isSpiral());
        assertNotEquals(legacy.getGeometryKey(), spiral.getGeometryKey());
        ExcavationTargetBatch batch = CylinderExcavationGeometry
            .nextBatch(spiral, CylinderExcavationGeometry.initialFrontier(spiral), 9);
        int[][] expected = { { 0, 0 }, { 1, 0 }, { 2, 0 }, { 2, 1 }, { 2, 2 }, { 1, 2 }, { 0, 2 }, { 0, 1 }, { 1, 1 } };
        for (int i = 0; i < 9; i++) assertEquals(
            new BlockPosition(expected[i][0], 60, expected[i][1]),
            batch.getTargets()
                .get(i)
                .getPosition());
    }

    private static void verify(CylinderExcavationSpec spec) {
        ExcavationFrontier cursor = CylinderExcavationGeometry.initialFrontier(spec);
        Set<BlockPosition> seen = new HashSet<>();
        while (!cursor.isComplete()) {
            assertTrue(seen.size() < spec.getVolume());
            ExcavationTargetBatch batch = CylinderExcavationGeometry.nextBatch(spec, cursor, 7);
            for (ExcavationTarget target : batch.getTargets()) {
                assertTrue(spec.contains(target.getPosition()));
                assertTrue(seen.add(target.getPosition()));
                assertEquals(
                    target.getNextFrontier(),
                    ExcavationFrontier.restore(
                        spec.getGeometryKey(),
                        target.getNextFrontier()
                            .getLayerY(),
                        target.getNextFrontier()
                            .getChunkX(),
                        target.getNextFrontier()
                            .getChunkZ(),
                        target.getNextFrontier()
                            .getBand(),
                        target.getNextFrontier()
                            .getOffset(),
                        target.getNextFrontier()
                            .isComplete()));
            }
            cursor = batch.getNextFrontier();
        }
        assertEquals(spec.getVolume(), seen.size());
    }
}
