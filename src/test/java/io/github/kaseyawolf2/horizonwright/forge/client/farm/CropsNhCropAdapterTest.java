package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import net.minecraft.tileentity.TileEntity;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;

public class CropsNhCropAdapterTest {

    @Test
    public void readsExactCropIdentityMaturityAndGrowthState() {
        FakeCropTile tile = new FakeCropTile(true, false, false, true, 120, "wheat");
        CropsNhCropAdapter adapter = new CropsNhCropAdapter(FakeCropTile.class.getName());

        CropsNhCropAdapter.Descriptor crop = adapter.read(CropsNhCropAdapter.BLOCK_ID, tile)
            .get();

        assertEquals(CropFamily.CROPS_NH, crop.getFamily());
        assertEquals("cropsnh:crop-card|id=wheat", crop.getCropIdentity());
        assertTrue(crop.isMature());
        assertFalse(crop.isProtected());
        assertTrue(
            crop.getObservationFingerprint()
                .contains("|growth=120|"));
    }

    @Test
    public void ignoresEmptySticksAndNonCropsNhBlocks() {
        CropsNhCropAdapter adapter = new CropsNhCropAdapter(FakeCropTile.class.getName());
        assertFalse(
            adapter.read(CropsNhCropAdapter.BLOCK_ID, new FakeCropTile(false, false, false, false, 0, "none"))
                .isPresent());
        assertFalse(
            adapter.read("minecraft:wheat", new FakeCropTile(true, false, false, true, 120, "wheat"))
                .isPresent());
    }

    @Test
    public void protectsWeedsAndCrossCropsFromAutomation() {
        CropsNhCropAdapter adapter = new CropsNhCropAdapter(FakeCropTile.class.getName());
        assertTrue(
            adapter.read(CropsNhCropAdapter.BLOCK_ID, new FakeCropTile(true, true, false, true, 120, "weed"))
                .get()
                .isProtected());
        assertTrue(
            adapter.read(CropsNhCropAdapter.BLOCK_ID, new FakeCropTile(true, false, true, true, 120, "wheat"))
                .get()
                .isProtected());
    }

    public static final class FakeCropTile extends TileEntity {

        private final boolean crop;
        private final boolean weed;
        private final boolean cross;
        private final boolean mature;
        private final int growth;
        private final FakeSeed seed;

        FakeCropTile(boolean crop, boolean weed, boolean cross, boolean mature, int growth, String cropId) {
            this.crop = crop;
            this.weed = weed;
            this.cross = cross;
            this.mature = mature;
            this.growth = growth;
            this.seed = new FakeSeed(cropId);
        }

        public boolean hasCrop() {
            return crop;
        }

        public boolean hasWeed() {
            return weed;
        }

        public boolean isCrossCrop() {
            return cross;
        }

        public boolean isMature() {
            return mature;
        }

        public int getGrowthProgress() {
            return growth;
        }

        public FakeSeed getSeed() {
            return seed;
        }
    }

    public static final class FakeSeed {

        private final FakeCrop crop;

        FakeSeed(String cropId) {
            crop = new FakeCrop(cropId);
        }

        public FakeCrop getCrop() {
            return crop;
        }
    }

    public static final class FakeCrop {

        private final String id;

        FakeCrop(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public java.util.Collection<Object> getGrowthRequirements() {
            return Collections.emptyList();
        }
    }
}
