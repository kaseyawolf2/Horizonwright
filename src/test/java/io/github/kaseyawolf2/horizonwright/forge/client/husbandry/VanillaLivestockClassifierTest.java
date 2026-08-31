package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.LivestockSpecies;

public class VanillaLivestockClassifierTest {

    private final VanillaLivestockClassifier classifier = new VanillaLivestockClassifier();

    @Test
    public void pinsExactVanillaClassesAndFeeds() {
        assertDescriptor("net.minecraft.entity.passive.EntityCow", LivestockSpecies.COW, "minecraft:wheat");
        assertDescriptor("net.minecraft.entity.passive.EntitySheep", LivestockSpecies.SHEEP, "minecraft:wheat");
        assertDescriptor("net.minecraft.entity.passive.EntityPig", LivestockSpecies.PIG, "minecraft:carrot");
        assertDescriptor(
            "net.minecraft.entity.passive.EntityChicken",
            LivestockSpecies.CHICKEN,
            "minecraft:wheat_seeds");
    }

    @Test
    public void refusesSubclassesModsAndUnsupportedAnimals() {
        assertNull(classifier.classify("net.minecraft.entity.passive.EntityMooshroom"));
        assertNull(classifier.classify("com.example.EntitySpecialCow"));
        assertNull(classifier.classify("net.minecraft.entity.passive.EntityWolf"));
        assertNull(classifier.classify(null));
    }

    private void assertDescriptor(String className, LivestockSpecies species, String feed) {
        VanillaLivestockClassifier.Descriptor descriptor = classifier.classify(className);
        assertEquals(species, descriptor.getSpecies());
        assertEquals(feed, descriptor.getBreedingItemId());
    }
}
