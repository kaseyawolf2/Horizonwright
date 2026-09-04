package io.github.kaseyawolf2.horizonwright.core.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagedQuarryGeometryTest {

    @Test
    public void consecutiveLayersFormAnOutsideDescendingStaircase() {
        CylinderExcavationSpec spec = new CylinderExcavationSpec(
            0,
            100,
            -100,
            2,
            40,
            70,
            ExcavationMode.MANAGED_QUARRY);

        BlockPosition previous = ManagedQuarryGeometry.rampStep(spec, spec.getTopY());
        assertFalse(spec.contains(previous));
        for (int layer = spec.getTopY() - 1; layer >= spec.getBottomY(); layer--) {
            BlockPosition current = ManagedQuarryGeometry.rampStep(spec, layer);
            int horizontalDistance = Math.abs(previous.getX() - current.getX())
                + Math.abs(previous.getZ() - current.getZ());
            assertEquals(1, horizontalDistance);
            assertEquals(previous.getY() - 1, current.getY());
            assertFalse(spec.contains(current));
            previous = current;
        }
    }

    @Test
    public void lightIsAboveItsSupportingStepAndNeverInsideVolume() {
        CylinderExcavationSpec spec = new CylinderExcavationSpec(0, 0, 0, 8, 20, 64, ExcavationMode.MANAGED_QUARRY);

        BlockPosition ramp = ManagedQuarryGeometry.rampStep(spec, 60);
        BlockPosition light = ManagedQuarryGeometry.lightPosition(spec, 60);

        assertEquals(ramp.getX(), light.getX());
        assertEquals(ramp.getY() + 1, light.getY());
        assertEquals(ramp.getZ(), light.getZ());
        assertFalse(spec.contains(ramp));
        assertFalse(spec.contains(light));
    }

    @Test
    public void invalidModeAndLayerAreRejected() {
        CylinderExcavationSpec clean = new CylinderExcavationSpec(0, 0, 0, 1, 10, 12, ExcavationMode.CLEAN_VOLUME);
        CylinderExcavationSpec managed = new CylinderExcavationSpec(0, 0, 0, 1, 10, 12, ExcavationMode.MANAGED_QUARRY);

        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryGeometry.rampStep(clean, 12));
        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryGeometry.rampStep(managed, 9));
        assertTrue(
            ManagedQuarryGeometry.rampStep(managed, 12)
                .getY() == 12);

        CylinderExcavationSpec edge = new CylinderExcavationSpec(
            0,
            CylinderExcavationSpec.MAX_ABS_COORDINATE,
            0,
            0,
            10,
            12,
            ExcavationMode.MANAGED_QUARRY);
        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryGeometry.rampStep(edge, 12));
    }
}
