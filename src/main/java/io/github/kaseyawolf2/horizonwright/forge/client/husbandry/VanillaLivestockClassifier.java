package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import io.github.kaseyawolf2.horizonwright.core.base.LivestockSpecies;

/** Exact-class vanilla 1.7.10 livestock and breeding-feed mapping. */
public final class VanillaLivestockClassifier {

    public Descriptor classify(String exactEntityClassName) {
        if ("net.minecraft.entity.passive.EntityCow".equals(exactEntityClassName)) {
            return new Descriptor(LivestockSpecies.COW, "minecraft:wheat");
        }
        if ("net.minecraft.entity.passive.EntitySheep".equals(exactEntityClassName)) {
            return new Descriptor(LivestockSpecies.SHEEP, "minecraft:wheat");
        }
        if ("net.minecraft.entity.passive.EntityPig".equals(exactEntityClassName)) {
            return new Descriptor(LivestockSpecies.PIG, "minecraft:carrot");
        }
        if ("net.minecraft.entity.passive.EntityChicken".equals(exactEntityClassName)) {
            return new Descriptor(LivestockSpecies.CHICKEN, "minecraft:wheat_seeds");
        }
        return null;
    }

    public static final class Descriptor {

        private final LivestockSpecies species;
        private final String breedingItemId;

        private Descriptor(LivestockSpecies species, String breedingItemId) {
            this.species = species;
            this.breedingItemId = breedingItemId;
        }

        public LivestockSpecies getSpecies() {
            return species;
        }

        public String getBreedingItemId() {
            return breedingItemId;
        }
    }
}
