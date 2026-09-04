package io.github.kaseyawolf2.horizonwright.core.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class CylinderExcavationGeometryTest {

    @Test
    public void validatesBoundsButAcceptsNegativeCentersAndExactVerticalRanges() {
        CylinderExcavationSpec spec = spec(-31, -17, 2, 4, 9, ExcavationMode.CLEAN_VOLUME);

        assertEquals(-31, spec.getCenterX());
        assertEquals(-17, spec.getCenterZ());
        assertEquals(13L, spec.getColumnCount());
        assertEquals(78L, spec.getVolume());
        assertTrue(spec.contains(new BlockPosition(-33, 4, -17)));
        assertFalse(spec.contains(new BlockPosition(-33, 3, -17)));
        assertFalse(spec.contains(new BlockPosition(-33, 4, -19)));

        assertThrows(IllegalArgumentException.class, () -> spec(0, 0, -1, 0, 0, ExcavationMode.CLEAN_VOLUME));
        assertThrows(
            IllegalArgumentException.class,
            () -> spec(0, 0, CylinderExcavationSpec.MAX_RADIUS + 1, 0, 0, ExcavationMode.CLEAN_VOLUME));
        assertThrows(IllegalArgumentException.class, () -> spec(0, 0, 1, 9, 8, ExcavationMode.CLEAN_VOLUME));
        assertThrows(IllegalArgumentException.class, () -> spec(0, 0, 1, -1, 8, ExcavationMode.CLEAN_VOLUME));
        assertThrows(IllegalArgumentException.class, () -> spec(0, 0, 1, 0, 256, ExcavationMode.CLEAN_VOLUME));
        assertThrows(
            IllegalArgumentException.class,
            () -> spec(CylinderExcavationSpec.MAX_ABS_COORDINATE, 0, 1, 0, 1, ExcavationMode.CLEAN_VOLUME));
    }

    @Test
    public void matchesTheLegacyRadiusTwoGoldenGeometryByMembership() throws IOException {
        CylinderExcavationSpec spec = spec(0, 0, 2, 7, 7, ExcavationMode.CLEAN_VOLUME);
        ExcavationTargetBatch batch = CylinderExcavationGeometry
            .nextBatch(spec, CylinderExcavationGeometry.initialFrontier(spec), 32);
        Set<String> actual = new HashSet<>();
        for (ExcavationTarget target : batch.getTargets()) {
            actual.add(
                target.getPosition()
                    .getX() + "\t"
                    + target.getPosition()
                        .getZ());
        }

        assertEquals(loadGoldenColumns(), actual);
        assertEquals(
            13,
            batch.getTargets()
                .size());
        assertTrue(
            batch.getNextFrontier()
                .isComplete());
    }

    @Test
    public void traversesNegativeChunkBoundariesByLayerChunkBandAndOffset() throws IOException {
        CylinderExcavationSpec spec = spec(-16, -16, 2, 5, 5, ExcavationMode.CLEAN_VOLUME);
        ExcavationTargetBatch batch = CylinderExcavationGeometry
            .nextBatch(spec, CylinderExcavationGeometry.initialFrontier(spec), 5);
        List<String> expected = loadLines("/fixtures/excavation/negative-chunk-frontier.tsv");

        for (int index = 0; index < expected.size(); index++) {
            ExcavationTarget target = batch.getTargets()
                .get(index);
            String actual = target.getPosition()
                .getX() + "\t"
                + target.getPosition()
                    .getY()
                + "\t"
                + target.getPosition()
                    .getZ()
                + "\t"
                + target.getNextFrontier()
                    .getChunkX()
                + "\t"
                + target.getNextFrontier()
                    .getChunkZ()
                + "\t"
                + target.getNextFrontier()
                    .getBand()
                + "\t"
                + target.getNextFrontier()
                    .getOffset();
            assertEquals(expected.get(index), actual);
        }
        assertEquals(
            new BlockPosition(-18, 5, -16),
            batch.getTargets()
                .get(1)
                .getPosition());
        assertEquals(
            -2,
            batch.getTargets()
                .get(1)
                .getNextFrontier()
                .getChunkX());
        assertEquals(
            -1,
            batch.getTargets()
                .get(1)
                .getNextFrontier()
                .getChunkZ());
    }

    @Test
    public void radiusTwoHundredFiftyStaysBoundedWithoutMaterializingItsVolume() {
        CylinderExcavationSpec fullHeight = spec(-8_001, -16_001, 250, 0, 255, ExcavationMode.MANAGED_QUARRY);
        assertEquals(196_321L, fullHeight.getColumnCount());
        assertEquals(50_258_176L, fullHeight.getVolume());

        ExcavationTargetBatch tinyBatch = CylinderExcavationGeometry
            .nextBatch(fullHeight, CylinderExcavationGeometry.initialFrontier(fullHeight), 37);
        assertEquals(
            37,
            tinyBatch.getTargets()
                .size());
        assertEquals(37L, CylinderExcavationGeometry.processedBefore(fullHeight, tinyBatch.getNextFrontier()));

        CylinderExcavationSpec oneLayer = spec(-8_001, -16_001, 250, 12, 12, ExcavationMode.MANAGED_QUARRY);
        ExcavationFrontier frontier = CylinderExcavationGeometry.initialFrontier(oneLayer);
        long visited = 0L;
        while (!frontier.isComplete()) {
            ExcavationTargetBatch batch = CylinderExcavationGeometry.nextBatch(oneLayer, frontier, 113);
            assertTrue(
                batch.getTargets()
                    .size() <= 113);
            visited += batch.getTargets()
                .size();
            frontier = batch.getNextFrontier();
        }
        assertEquals(oneLayer.getColumnCount(), visited);
        assertEquals(oneLayer.getVolume(), CylinderExcavationGeometry.processedBefore(oneLayer, frontier));
    }

    @Test
    public void exactFrontierRoundTripsAndWrongGeometryIsRejected() {
        CylinderExcavationSpec spec = spec(-16, -16, 2, 4, 5, ExcavationMode.CLEAN_VOLUME);
        ExcavationFrontier next = CylinderExcavationGeometry
            .nextBatch(spec, CylinderExcavationGeometry.initialFrontier(spec), 7)
            .getNextFrontier();
        ExcavationFrontier restored = ExcavationFrontier.restore(
            next.getGeometryKey(),
            next.getLayerY(),
            next.getChunkX(),
            next.getChunkZ(),
            next.getBand(),
            next.getOffset(),
            next.isComplete());

        assertEquals(next, restored);
        assertEquals(7L, CylinderExcavationGeometry.processedBefore(spec, restored));
        CylinderExcavationSpec other = spec(-16, -16, 3, 4, 5, ExcavationMode.CLEAN_VOLUME);
        assertThrows(IllegalArgumentException.class, () -> CylinderExcavationGeometry.validate(other, restored));
        assertThrows(
            IllegalArgumentException.class,
            () -> CylinderExcavationGeometry.nextBatch(spec, restored, CylinderExcavationGeometry.MAX_BATCH_SIZE + 1));
    }

    @Test
    public void arbitraryInCylinderPositionBecomesAnExactCanonicalFrontier() {
        CylinderExcavationSpec spec = spec(-16, -16, 3, 4, 9, ExcavationMode.CLEAN_VOLUME);
        BlockPosition position = new BlockPosition(-18, 7, -17);

        ExcavationFrontier frontier = CylinderExcavationGeometry.atPosition(spec, position);

        assertEquals(position, frontier.getPosition());
        CylinderExcavationGeometry.validate(spec, frontier);
        assertThrows(
            IllegalArgumentException.class,
            () -> CylinderExcavationGeometry.atPosition(spec, new BlockPosition(-30, 7, -17)));
    }

    private static CylinderExcavationSpec spec(int centerX, int centerZ, int radius, int bottomY, int topY,
        ExcavationMode mode) {
        return new CylinderExcavationSpec(0, centerX, centerZ, radius, bottomY, topY, mode);
    }

    private static Set<String> loadGoldenColumns() throws IOException {
        List<String> lines = loadLines("/fixtures/characterization/circle-cylinder-radius-2.tsv");
        return new HashSet<>(lines);
    }

    private static List<String> loadLines(String resource) throws IOException {
        InputStream stream = CylinderExcavationGeometryTest.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("missing fixture " + resource);
        }
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("dx\t") && !line.startsWith("x\t")) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }
}
