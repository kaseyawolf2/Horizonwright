package io.github.kaseyawolf2.horizonwright.core.excavation;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class RectangleExcavationTest {

    @Test
    public void visitsExactAsymmetricRectangleAcrossNegativeChunks() {
        CylinderExcavationSpec spec = CylinderExcavationSpec
            .rectangle(0, -18, -13, -2, 2, 60, 62, ExcavationMode.CLEAN_VOLUME);
        assertEquals(90, spec.getVolume());
        ExcavationTargetBatch batch = CylinderExcavationGeometry
            .nextBatch(spec, CylinderExcavationGeometry.initialFrontier(spec), 200);
        Set<BlockPosition> positions = new HashSet<>();
        for (ExcavationTarget target : batch.getTargets()) {
            assertTrue(spec.contains(target.getPosition()));
            assertTrue(positions.add(target.getPosition()));
        }
        assertEquals(90, positions.size());
        assertTrue(
            batch.getNextFrontier()
                .isComplete());
        assertTrue(positions.contains(new BlockPosition(-18, 60, -2)));
        assertTrue(positions.contains(new BlockPosition(-13, 62, 2)));
        assertFalse(spec.contains(new BlockPosition(-12, 61, 0)));
    }

    @Test
    public void rectangularRampStaysInsideAndDescendsContinuously() {
        CylinderExcavationSpec spec = CylinderExcavationSpec
            .rectangle(0, -7, -3, 10, 16, 0, 80, ExcavationMode.MANAGED_QUARRY);
        BlockPosition previous = null;
        for (int y = 80; y >= 0; y--) {
            BlockPosition step = ManagedQuarryGeometry.rampStep(spec, y);
            assertTrue(spec.contains(step));
            if (previous != null)
                assertEquals(1, Math.abs(step.getX() - previous.getX()) + Math.abs(step.getZ() - previous.getZ()));
            previous = step;
        }
    }
}
