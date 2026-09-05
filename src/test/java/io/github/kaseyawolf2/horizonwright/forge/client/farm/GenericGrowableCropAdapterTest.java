package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockMushroom;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockStem;
import net.minecraft.block.BlockTallGrass;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;

public class GenericGrowableCropAdapterTest {

    private final GenericGrowableCropAdapter adapter = new GenericGrowableCropAdapter();

    @Test
    public void publicGrowthAndExactReplantEvidenceProduceDestructiveCropObservations() {
        GenericGrowableCropAdapter.Descriptor growing = classify(true, true, true);
        assertEquals(CropFamily.GENERIC_IGROWABLE, growing.getFamily());
        assertFalse(growing.isMature());
        assertEquals("example:seed|meta=4|nbt=none", growing.getReplantIdentity());

        GenericGrowableCropAdapter.Descriptor mature = classify(false, true, true);
        assertTrue(mature.isMature());
        assertEquals("example:crop|meta=6|adapter=igrowable|harvest=break-replant", mature.getObservationFingerprint());
    }

    @Test
    public void replacementAndCurrentSupportMustBothBeProven() {
        assertFalse(classifyOptional(false, false, true).isPresent());
        assertFalse(classifyOptional(false, true, false).isPresent());
        assertFalse(
            adapter.classify("example:crop", 6, false, null, -1, true, true, false)
                .isPresent());
    }

    @Test
    public void exactHungerOverhaulBlockCropEvidenceUsesNonDestructiveHarvest() {
        GenericGrowableCropAdapter.Descriptor young = adapter
            .classify("example:block_crop", 6, false, null, -1, true, true, true)
            .get();
        GenericGrowableCropAdapter.Descriptor mature = adapter
            .classify("example:block_crop", 7, false, null, -1, true, true, true)
            .get();
        assertEquals(CropFamily.GENERIC_RIGHT_CLICK, mature.getFamily());
        assertFalse(young.isMature());
        assertTrue(mature.isMature());
        assertEquals("right-click:block=example:block_crop", mature.getReplantIdentity());
    }

    @Test
    public void persistentVanillaGrowablesAreNeverTreatedAsCrops() {
        assertTrue(GenericGrowableCropAdapter.isPersistentGrowableType(BlockSapling.class));
        assertTrue(GenericGrowableCropAdapter.isPersistentGrowableType(BlockStem.class));
        assertTrue(GenericGrowableCropAdapter.isPersistentGrowableType(BlockGrass.class));
        assertTrue(GenericGrowableCropAdapter.isPersistentGrowableType(BlockMushroom.class));
        assertTrue(GenericGrowableCropAdapter.isPersistentGrowableType(BlockTallGrass.class));
        assertTrue(GenericGrowableCropAdapter.isPersistentGrowableType(BlockDoublePlant.class));
    }

    @Test
    public void optionalIntegrationsCannotFallThroughToGenericDestructiveHarvest() {
        assertTrue(GenericGrowableCropAdapter.isIntegratedGrowableClassName("com.pam.harvestcraft.BlockPamCrop"));
        assertTrue(
            GenericGrowableCropAdapter.isIntegratedGrowableClassName("com.gtnewhorizon.cropsnh.blocks.BlockCrop"));
        assertTrue(GenericGrowableCropAdapter.isIntegratedGrowableClassName("mods.natura.blocks.crops.CropBlock"));
        assertFalse(GenericGrowableCropAdapter.isIntegratedGrowableClassName("example.SafeCrop"));
    }

    private GenericGrowableCropAdapter.Descriptor classify(boolean canGrow, boolean replacementMatches,
        boolean supportAcceptsSeed) {
        return classifyOptional(canGrow, replacementMatches, supportAcceptsSeed).get();
    }

    private Optional<GenericGrowableCropAdapter.Descriptor> classifyOptional(boolean canGrow,
        boolean replacementMatches, boolean supportAcceptsSeed) {
        return adapter
            .classify("example:crop", 6, canGrow, "example:seed", 4, replacementMatches, supportAcceptsSeed, false);
    }
}
