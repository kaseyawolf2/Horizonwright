package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.init.Blocks;

import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;

/** Exact vanilla 1.7.10 crop maturity and replant-material table. */
final class VanillaCropClassifier {

    private final HungerOverhaulCompatibilityStatus hungerOverhaul;

    VanillaCropClassifier() {
        this(HungerOverhaulCompatibilityProbe.inspect());
    }

    VanillaCropClassifier(HungerOverhaulCompatibilityStatus hungerOverhaul) {
        if (hungerOverhaul == null) throw new IllegalArgumentException("Hunger Overhaul compatibility is required");
        this.hungerOverhaul = hungerOverhaul;
    }

    Descriptor classify(Block block, String registeredBlockId, int metadata) {
        String canonicalId = canonicalVanillaId(block);
        String blockId = canonicalId == null ? registeredBlockId : canonicalId;
        boolean rightClick = canRightClickHarvest(block);
        return classify(blockId, metadata, rightClick);
    }

    boolean canRightClickHarvest(Block block) {
        return block instanceof BlockCrops && hungerOverhaul.isAvailable()
            && HungerOverhaulCompatibilityProbe.rightClickHarvestingEnabled(block);
    }

    Descriptor classify(String blockId, int metadata) {
        return classify(blockId, metadata, false);
    }

    Descriptor classify(String blockId, int metadata, boolean rightClickHarvest) {
        if (blockId == null || metadata < 0 || metadata > 15) return null;
        if ("minecraft:wheat".equals(blockId)) {
            return new Descriptor(blockId, metadata, 7, "minecraft:wheat_seeds", 0, rightClickHarvest);
        }
        if ("minecraft:carrots".equals(blockId)) {
            return new Descriptor(blockId, metadata, 7, "minecraft:carrot", 0, rightClickHarvest);
        }
        if ("minecraft:potatoes".equals(blockId)) {
            return new Descriptor(blockId, metadata, 7, "minecraft:potato", 0, rightClickHarvest);
        }
        if ("minecraft:nether_wart".equals(blockId)) {
            return new Descriptor(blockId, metadata, 3, "minecraft:nether_wart", 0, false);
        }
        if ("minecraft:cocoa".equals(blockId)) {
            int age = (metadata & 12) >> 2;
            return new Descriptor(blockId, metadata, age, "minecraft:dye", 3, false, 3);
        }
        return null;
    }

    HungerOverhaulCompatibilityStatus hungerOverhaulCompatibility() {
        return hungerOverhaul;
    }

    private static String canonicalVanillaId(Block block) {
        if (block == Blocks.wheat) return "minecraft:wheat";
        if (block == Blocks.carrots) return "minecraft:carrots";
        if (block == Blocks.potatoes) return "minecraft:potatoes";
        if (block == Blocks.nether_wart) return "minecraft:nether_wart";
        if (block == Blocks.cocoa) return "minecraft:cocoa";
        return null;
    }

    static final class Descriptor {

        private final String blockId;
        private final int metadata;
        private final int matureMetadata;
        private final String seedItemId;
        private final int seedMetadata;
        private final boolean rightClickHarvest;

        private Descriptor(String blockId, int metadata, int matureMetadata, String seedItemId, int seedMetadata,
            boolean rightClickHarvest) {
            this(blockId, metadata, metadata, seedItemId, seedMetadata, rightClickHarvest, matureMetadata);
        }

        private Descriptor(String blockId, int metadata, int maturityValue, String seedItemId, int seedMetadata,
            boolean rightClickHarvest, int matureMetadata) {
            this.blockId = blockId;
            this.metadata = metadata;
            this.maturityValue = maturityValue;
            this.matureMetadata = matureMetadata;
            this.seedItemId = seedItemId;
            this.seedMetadata = seedMetadata;
            this.rightClickHarvest = rightClickHarvest;
        }

        private final int maturityValue;

        CropFamily getFamily() {
            return rightClickHarvest ? CropFamily.VANILLA : CropFamily.VANILLA_BREAK_REPLANT;
        }

        boolean isMature() {
            return maturityValue >= matureMetadata;
        }

        String getObservationFingerprint() {
            return blockId + "|meta=" + metadata + "|harvest=" + (rightClickHarvest ? "right-click" : "break-replant");
        }

        String getSeedItemId() {
            return seedItemId;
        }

        int getSeedMetadata() {
            return seedMetadata;
        }

        String getSeedFingerprint() {
            return materialIdentity(seedItemId, seedMetadata, "none");
        }
    }

    static String materialIdentity(String itemId, int metadata, String dataHash) {
        if (itemId == null || itemId.trim()
            .isEmpty()
            || metadata < 0
            || dataHash == null
            || dataHash.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("complete item identity is required");
        }
        return itemId.trim() + "|meta=" + metadata + "|nbt=" + dataHash.trim();
    }
}
