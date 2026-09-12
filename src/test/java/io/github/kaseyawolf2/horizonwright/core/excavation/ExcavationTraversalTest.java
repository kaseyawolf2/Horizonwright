package io.github.kaseyawolf2.horizonwright.core.excavation;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ExcavationTraversalTest {

    @Test
    public void wideSpiralsCoverClippedAndThinAreasAndBindCheckpointsToWidth() {
        for (ExcavationTraversal order : new ExcavationTraversal[] { ExcavationTraversal.SQUARE_SPIRAL,
            ExcavationTraversal.CIRCLE_SPIRAL }) {
            for (int band : new int[] { 2, 3, 8, 64 }) {
                for (int radius = 0; radius <= 8; radius++) verify(
                    new CylinderExcavationSpec(0, -16, -7, radius, 40, 41, ExcavationMode.CLEAN_VOLUME)
                        .withTraversal(order, band));
                for (int width = 1; width <= 7; width++) verify(
                    CylinderExcavationSpec.rectangle(0, -8, -9 + width, -5, 2, 40, 41, ExcavationMode.CLEAN_VOLUME)
                        .withTraversal(order, band));
            }
            CylinderExcavationSpec base = new CylinderExcavationSpec(0, 0, 0, 8, 40, 40, ExcavationMode.CLEAN_VOLUME);
            assertNotEquals(
                base.withTraversal(order, 1)
                    .getGeometryKey(),
                base.withTraversal(order, 8)
                    .getGeometryKey());
        }
    }

    @Test
    public void allOrdersCoverThinEvenNegativeRectanglesAndClippedCircles() {
        for (ExcavationTraversal order : ExcavationTraversal.values()) {
            for (int width = 1; width <= 6; width++) for (int depth = 1; depth <= 6; depth++) verify(
                CylinderExcavationSpec
                    .rectangle(0, -18, -19 + width, -17, -18 + depth, 60, 61, ExcavationMode.CLEAN_VOLUME)
                    .withTraversal(order));
            for (int radius = 0; radius <= 8; radius++) verify(
                new CylinderExcavationSpec(0, -16, -7, radius, 40, 41, ExcavationMode.CLEAN_VOLUME)
                    .withTraversal(order));
        }
    }

    @Test
    public void rowsSnakeAcrossTheChosenHorizontalAxis() {
        CylinderExcavationSpec rectangle = CylinderExcavationSpec
            .rectangle(0, 0, 2, 0, 1, 60, 60, ExcavationMode.CLEAN_VOLUME);
        assertOrder(
            rectangle.withTraversal(ExcavationTraversal.ROWS_X),
            new int[][] { { 0, 0 }, { 1, 0 }, { 2, 0 }, { 2, 1 }, { 1, 1 }, { 0, 1 } });
        assertOrder(
            rectangle.withTraversal(ExcavationTraversal.ROWS_Z),
            new int[][] { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 }, { 2, 0 }, { 2, 1 } });
    }

    @Test
    public void circleShellsMoveInwardAndKeepLegacyCheckpointsDistinct() {
        CylinderExcavationSpec base = new CylinderExcavationSpec(0, 0, 0, 8, 40, 40, ExcavationMode.CLEAN_VOLUME);
        CylinderExcavationSpec circle = base.withTraversal(ExcavationTraversal.CIRCLE_SPIRAL);
        double previousRing = 9;
        for (ExcavationTarget target : CylinderExcavationGeometry
            .nextBatch(circle, CylinderExcavationGeometry.initialFrontier(circle), 1000)
            .getTargets()) {
            BlockPosition p = target.getPosition();
            double ring = Math.ceil(Math.hypot(p.getX(), p.getZ()));
            assertTrue(ring <= previousRing);
            previousRing = ring;
        }
        assertEquals(0, previousRing, 0);
        Set<String> keys = new HashSet<>();
        for (ExcavationTraversal order : ExcavationTraversal.values()) assertTrue(
            keys.add(
                base.withTraversal(order)
                    .getGeometryKey()));
        assertEquals(
            base.getGeometryKey(),
            base.withTraversal(ExcavationTraversal.CHUNKS)
                .getGeometryKey());
    }

    private static void assertOrder(CylinderExcavationSpec spec, int[][] expected) {
        java.util.List<ExcavationTarget> targets = CylinderExcavationGeometry
            .nextBatch(spec, CylinderExcavationGeometry.initialFrontier(spec), expected.length)
            .getTargets();
        for (int i = 0; i < expected.length; i++) assertEquals(
            new BlockPosition(expected[i][0], 60, expected[i][1]),
            targets.get(i)
                .getPosition());
    }

    private static void verify(CylinderExcavationSpec spec) {
        ExcavationFrontier cursor = CylinderExcavationGeometry.initialFrontier(spec);
        Set<BlockPosition> seen = new HashSet<>();
        int previousY = spec.getTopY();
        while (!cursor.isComplete()) {
            assertTrue(seen.size() < spec.getVolume());
            ExcavationTargetBatch batch = CylinderExcavationGeometry.nextBatch(spec, cursor, 11);
            for (ExcavationTarget target : batch.getTargets()) {
                BlockPosition p = target.getPosition();
                assertTrue(spec.contains(p));
                assertTrue(seen.add(p));
                assertTrue(p.getY() <= previousY);
                previousY = p.getY();
                // Recreate the geometry as after reconnect, resume from coordinates, and prove the same successor.
                CylinderExcavationSpec restored = spec.withTraversal(spec.getTraversal());
                assertEquals(
                    target.getNextFrontier(),
                    CylinderExcavationGeometry
                        .nextBatch(restored, CylinderExcavationGeometry.atPosition(restored, p), 1)
                        .getNextFrontier());
            }
            cursor = batch.getNextFrontier();
        }
        assertEquals(spec.getVolume(), seen.size());
    }
}
