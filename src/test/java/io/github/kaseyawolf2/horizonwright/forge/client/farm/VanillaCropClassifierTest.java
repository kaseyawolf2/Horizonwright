package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.init.Blocks;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;

public class VanillaCropClassifierTest {

    private final VanillaCropClassifier classifier = new VanillaCropClassifier();

    @Test
    public void exactVanillaCropMaturityAndSeedIdentitiesArePinned() {
        assertCrop("minecraft:wheat", 6, false, "minecraft:wheat_seeds");
        assertCrop("minecraft:wheat", 7, true, "minecraft:wheat_seeds");
        assertCrop("minecraft:carrots", 7, true, "minecraft:carrot");
        assertCrop("minecraft:potatoes", 7, true, "minecraft:potato");
        assertCrop("minecraft:nether_wart", 2, false, "minecraft:nether_wart");
        assertCrop("minecraft:nether_wart", 3, true, "minecraft:nether_wart");
    }

    @Test
    public void unknownModdedBlocksAndInvalidMetadataAreNotGuessed() {
        assertNull(classifier.classify("harvestcraft:pamcrop", 7));
        assertNull(classifier.classify("minecraft:wheat", -1));
        assertNull(classifier.classify("minecraft:wheat", 16));
    }

    @Test
    public void exactVanillaBlockIdentitySurvivesLegacyRegistryAliases() {
        VanillaCropClassifier.Descriptor wheat = classifier.classify(Blocks.wheat, "legacy:wheat_alias", 7);
        assertEquals("minecraft:wheat|meta=7", wheat.getObservationFingerprint());
        assertTrue(wheat.isMature());
    }

    @Test
    public void inventoryMaterialIdentityIgnoresCountButPreservesItemMetadataAndNbtHash() {
        ItemFingerprint seeds = new ItemFingerprint("minecraft:wheat_seeds", 0, "none", 32);
        assertEquals("minecraft:wheat_seeds|meta=0|nbt=none", MinecraftVanillaFarmObserver.materialIdentity(seeds));
    }

    private void assertCrop(String blockId, int metadata, boolean mature, String seedItem) {
        VanillaCropClassifier.Descriptor descriptor = classifier.classify(blockId, metadata);
        assertEquals(blockId + "|meta=" + metadata, descriptor.getObservationFingerprint());
        assertEquals(seedItem, descriptor.getSeedItemId());
        assertEquals(seedItem + "|meta=0|nbt=none", descriptor.getSeedFingerprint());
        if (mature) assertTrue(descriptor.isMature());
        else assertFalse(descriptor.isMature());
    }
}
