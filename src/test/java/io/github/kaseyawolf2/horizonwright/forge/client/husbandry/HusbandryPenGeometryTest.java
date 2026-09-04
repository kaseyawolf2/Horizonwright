package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;

public class HusbandryPenGeometryTest {

    @Test
    public void mapsEntityFeetOntoTheCapturedSupportBlockArea() {
        NamedArea pen = new NamedArea("pen", "Pen", new BasePosition(0, -3, 64, -4), new BasePosition(0, 6, 64, 5));

        assertEquals(new BasePosition(0, -1, 64, -2), HusbandryPenGeometry.policyPosition(pen, 0, -0.2D, 65.0D, -1.1D));
        assertEquals(new BasePosition(0, 3, 64, 4), HusbandryPenGeometry.policyPosition(pen, 0, 3.9D, 66.8D, 4.1D));
    }

    @Test
    public void observationVolumeIncludesTheAnimalHeightAboveSupportBlocks() {
        NamedArea flatTenByTen = new NamedArea(
            "pen",
            "Pen",
            new BasePosition(0, 0, 64, 0),
            new BasePosition(0, 9, 64, 9));

        assertEquals(300L, HusbandryPenGeometry.observationVolume(flatTenByTen));
    }
}
