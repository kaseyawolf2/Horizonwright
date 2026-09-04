package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;

public class PamHarvestCraftCropAdapterTest {

    private final PamHarvestCraftCropAdapter adapter = new PamHarvestCraftCropAdapter();

    @Test
    public void classifiesOnlyExactPinnedCropClassesAndMaturityStates() {
        assertCrop(PamHarvestCraftCropAdapter.CROP_CLASS, 6, CropFamily.PAM_CROP, false);
        assertCrop(PamHarvestCraftCropAdapter.CROP_CLASS, 7, CropFamily.PAM_CROP, true);
        assertCrop(PamHarvestCraftCropAdapter.CROP_CLASS, 8, CropFamily.PAM_CROP, false);
        assertCrop(PamHarvestCraftCropAdapter.HANGING_FRUIT_CLASS, 1, CropFamily.PAM_HANGING_FRUIT, false);
        assertCrop(PamHarvestCraftCropAdapter.HANGING_FRUIT_CLASS, 2, CropFamily.PAM_HANGING_FRUIT, true);
        assertCrop(PamHarvestCraftCropAdapter.HANGING_FRUIT_CLASS, 3, CropFamily.PAM_HANGING_FRUIT, false);
        assertCrop(PamHarvestCraftCropAdapter.FRUITING_LOG_CLASS, 14, CropFamily.PAM_FRUITING_LOG, false);
        assertCrop(PamHarvestCraftCropAdapter.FRUITING_LOG_CLASS, 15, CropFamily.PAM_FRUITING_LOG, true);

        assertFalse(
            adapter.classify("other.BlockPamCrop", "harvestcraft:pamcrop", 7, true, true)
                .isPresent());
        assertFalse(
            adapter.classify(PamHarvestCraftCropAdapter.CROP_CLASS, "", 7, true, true)
                .isPresent());
    }

    @Test
    public void refusesConfiguredNonRightClickCropAndFruitMutations() {
        assertFalse(
            adapter.classify(PamHarvestCraftCropAdapter.CROP_CLASS, "harvestcraft:pamcrop", 7, false, true)
                .isPresent());
        assertFalse(
            adapter.classify(PamHarvestCraftCropAdapter.HANGING_FRUIT_CLASS, "harvestcraft:pamfruit", 2, true, false)
                .isPresent());

        assertTrue(
            adapter.classify(PamHarvestCraftCropAdapter.FRUITING_LOG_CLASS, "harvestcraft:pamfruitlog", 3, false, false)
                .isPresent());
    }

    private void assertCrop(String blockClass, int metadata, CropFamily family, boolean mature) {
        PamHarvestCraftCropAdapter.Descriptor descriptor = adapter
            .classify(blockClass, "harvestcraft:test", metadata, true, true)
            .get();
        assertEquals(family, descriptor.getFamily());
        assertEquals(mature, descriptor.isMature());
        assertTrue(
            descriptor.getObservationFingerprint()
                .contains("|meta=" + metadata + "|"));
        assertEquals("harvestcraft:right-click|block=harvestcraft:test", descriptor.getHarvestIdentity());
    }
}
