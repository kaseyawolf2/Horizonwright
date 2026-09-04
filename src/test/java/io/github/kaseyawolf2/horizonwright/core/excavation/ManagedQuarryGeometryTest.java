package io.github.kaseyawolf2.horizonwright.core.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagedQuarryGeometryTest {

    @Test
    public void consecutiveLayersFormARetainedDescendingStaircaseInsideTheVolume() {
        CylinderExcavationSpec spec = new CylinderExcavationSpec(
            0,
            100,
            -100,
            2,
            40,
            70,
            ExcavationMode.MANAGED_QUARRY);

        BlockPosition previous = ManagedQuarryGeometry.rampStep(spec, spec.getTopY());
        assertTrue(spec.contains(previous));
        for (int layer = spec.getTopY() - 1; layer >= spec.getBottomY(); layer--) {
            BlockPosition current = ManagedQuarryGeometry.rampStep(spec, layer);
            int horizontalDistance = Math.abs(previous.getX() - current.getX())
                + Math.abs(previous.getZ() - current.getZ());
            assertEquals(1, horizontalDistance);
            assertEquals(previous.getY() - 1, current.getY());
            assertTrue(spec.contains(current));
            previous = current;
        }
    }

    @Test
    public void lightIsAboveItsSupportingStepAndReservedWhenInsideVolume() {
        CylinderExcavationSpec spec = new CylinderExcavationSpec(0, 0, 0, 8, 20, 64, ExcavationMode.MANAGED_QUARRY);

        BlockPosition ramp = ManagedQuarryGeometry.rampStep(spec, 60);
        BlockPosition light = ManagedQuarryGeometry.lightPosition(spec, 60);

        assertEquals(ramp.getX(), light.getX());
        assertEquals(ramp.getY() + 1, light.getY());
        assertEquals(ramp.getZ(), light.getZ());
        assertTrue(spec.contains(ramp));
        assertTrue(spec.contains(light));
        assertTrue(ManagedQuarryGeometry.isRampStep(spec, ramp));
        assertTrue(ManagedQuarryGeometry.isScheduledLightPosition(spec, ManagedQuarryConfiguration.defaults(), light));
    }

    @Test
    public void invalidModeAndLayerAreRejected() {
        CylinderExcavationSpec clean = new CylinderExcavationSpec(0, 0, 0, 1, 10, 12, ExcavationMode.CLEAN_VOLUME);
        CylinderExcavationSpec managed = new CylinderExcavationSpec(0, 0, 0, 2, 10, 12, ExcavationMode.MANAGED_QUARRY);

        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryGeometry.rampStep(clean, 12));
        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryGeometry.rampStep(managed, 9));
        assertTrue(
            ManagedQuarryGeometry.rampStep(managed, 12)
                .getY() == 12);

        CylinderExcavationSpec tooNarrow = new CylinderExcavationSpec(
            0,
            0,
            0,
            1,
            10,
            12,
            ExcavationMode.MANAGED_QUARRY);
        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryGeometry.rampStep(tooNarrow, 12));
    }
}
