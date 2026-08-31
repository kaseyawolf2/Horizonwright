package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class GuiProfileAssetsTest {

    @Test
    public void friendlyNamesAreNormalizedWithoutAcceptingJsonLikeText() {
        assertEquals("mining-tools_1.0", ProfileAssetInput.stableId(" mining-tools_1.0 ", "loadout"));
        assertThrows(IllegalArgumentException.class, () -> ProfileAssetInput.stableId("mining tools", "loadout"));
        assertThrows(IllegalArgumentException.class, () -> ProfileAssetInput.stableId("{\"id\":1}", "loadout"));
    }

    @Test
    public void inventorySlotsAreBoundedToThePlayerInventory() {
        assertEquals(0, ProfileAssetInput.inventorySlot("0", "tool"));
        assertEquals(35, ProfileAssetInput.inventorySlot("35", "tool"));
        assertThrows(IllegalArgumentException.class, () -> ProfileAssetInput.inventorySlot("36", "tool"));
        assertThrows(IllegalArgumentException.class, () -> ProfileAssetInput.inventorySlot("-1", "tool"));
    }

    @Test
    public void reservationMinimumMustBeAPositiveWholeNumber() {
        assertEquals(16, ProfileAssetInput.positiveInteger("16", "minimum"));
        assertThrows(IllegalArgumentException.class, () -> ProfileAssetInput.positiveInteger("0", "minimum"));
        assertThrows(IllegalArgumentException.class, () -> ProfileAssetInput.positiveInteger("many", "minimum"));
    }
}
