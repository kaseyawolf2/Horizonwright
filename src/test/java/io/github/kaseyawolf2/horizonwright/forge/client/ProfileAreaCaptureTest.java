package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;

public class ProfileAreaCaptureTest {

    @Test
    public void twoSameDimensionCornersBuildOneNormalizedArea() {
        ProfileAreaCapture capture = new ProfileAreaCapture();
        assertFalse(capture.isComplete());
        capture.recordFirst(new BasePosition(0, 10, 70, -2));
        capture.recordSecond(new BasePosition(0, 2, 60, 8));

        NamedArea area = capture.build("north-field");

        assertTrue(capture.isComplete());
        assertEquals("north-field", area.getId());
        assertEquals(new BasePosition(0, 2, 60, -2), area.getMinimum());
        assertEquals(new BasePosition(0, 10, 70, 8), area.getMaximum());
    }

    @Test
    public void incompleteOrCrossDimensionCaptureCannotCreateAnArea() {
        ProfileAreaCapture incomplete = new ProfileAreaCapture();
        incomplete.recordFirst(new BasePosition(0, 0, 64, 0));
        assertThrows(IllegalStateException.class, () -> incomplete.build("farm"));

        ProfileAreaCapture crossDimension = new ProfileAreaCapture();
        crossDimension.recordFirst(new BasePosition(0, 0, 64, 0));
        crossDimension.recordSecond(new BasePosition(-1, 1, 64, 1));
        assertThrows(IllegalStateException.class, () -> crossDimension.build("farm"));
    }
}
