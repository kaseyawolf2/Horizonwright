package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CropsNhSpadeSelectionTest {

    @Test
    public void acceptsOnlyTheTwoTestedCropsNhSpadeImplementations() {
        assertTrue(LiveVanillaFarmBackend.isCropsNhSpadeClassName("com.gtnewhorizon.cropsnh.items.tools.ItemSpade"));
        assertTrue(
            LiveVanillaFarmBackend.isCropsNhSpadeClassName("com.gtnewhorizon.cropsnh.items.tools.ItemReinforcedSpade"));
        assertFalse(LiveVanillaFarmBackend.isCropsNhSpadeClassName("net.minecraft.item.ItemSpade"));
        assertFalse(LiveVanillaFarmBackend.isCropsNhSpadeClassName("example.UnknownCropTool"));
    }
}
