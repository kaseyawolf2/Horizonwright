package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;

/** Exact vanilla 1.7.10 crop maturity and replant-material table. */
final class VanillaCropClassifier {

    Descriptor classify(Block block, String registeredBlockId, int metadata) {
        String canonicalId = canonicalVanillaId(block);
        return classify(canonicalId == null ? registeredBlockId : canonicalId, metadata);
    }

    Descriptor classify(String blockId, int metadata) {
        if (blockId == null || metadata < 0 || metadata > 15) return null;
        if ("minecraft:wheat".equals(blockId)) {
            return new Descriptor(blockId, metadata, 7, "minecraft:wheat_seeds", 0);
        }
        if ("minecraft:carrots".equals(blockId)) {
            return new Descriptor(blockId, metadata, 7, "minecraft:carrot", 0);
        }
        if ("minecraft:potatoes".equals(blockId)) {
            return new Descriptor(blockId, metadata, 7, "minecraft:potato", 0);
        }
        if ("minecraft:nether_wart".equals(blockId)) {
            return new Descriptor(blockId, metadata, 3, "minecraft:nether_wart", 0);
        }
        return null;
    }

    private static String canonicalVanillaId(Block block) {
        if (block == Blocks.wheat) return "minecraft:wheat";
        if (block == Blocks.carrots) return "minecraft:carrots";
        if (block == Blocks.potatoes) return "minecraft:potatoes";
        if (block == Blocks.nether_wart) return "minecraft:nether_wart";
        return null;
    }

    static final class Descriptor {

        private final String blockId;
        private final int metadata;
        private final int matureMetadata;
        private final String seedItemId;
        private final int seedMetadata;

        private Descriptor(String blockId, int metadata, int matureMetadata, String seedItemId, int seedMetadata) {
            this.blockId = blockId;
            this.metadata = metadata;
            this.matureMetadata = matureMetadata;
            this.seedItemId = seedItemId;
            this.seedMetadata = seedMetadata;
        }

        CropFamily getFamily() {
            return CropFamily.VANILLA;
        }

        boolean isMature() {
            return metadata >= matureMetadata;
        }

        String getObservationFingerprint() {
            return blockId + "|meta=" + metadata;
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
