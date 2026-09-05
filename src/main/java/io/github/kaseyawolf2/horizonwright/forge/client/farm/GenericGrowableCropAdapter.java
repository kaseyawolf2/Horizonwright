package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.Optional;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockMushroom;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockStem;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.IGrowable;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.util.ForgeDirection;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;

/** Conservative public-API adapter for crop-like {@link IGrowable} implementations. */
final class GenericGrowableCropAdapter {

    private static final long SEED_PROBE_RANDOM = 0x48575f534545444cL;

    Optional<Descriptor> read(Block block, String blockId, int metadata, TileEntity tile, World world,
        BasePosition position, boolean hungerOverhaulRightClick) {
        if (!(block instanceof IGrowable)) return Optional.empty();
        if (tile != null || world == null || position == null || isPersistentOrIntegratedGrowable(block)) {
            traceRejected(block, blockId, position, "tile-backed, persistent, integrated, or incomplete context");
            return Optional.empty();
        }
        String canonicalId = canonicalId(blockId);
        if (canonicalId == null || metadata < 0 || metadata > 15) {
            traceRejected(block, blockId, position, "invalid registered identity or metadata");
            return Optional.empty();
        }

        if (block instanceof BlockCrops && hungerOverhaulRightClick) {
            Optional<Descriptor> result = classify(canonicalId, metadata, false, null, -1, true, true, true);
            traceAccepted(block, canonicalId, position, result.get());
            return result;
        }

        try {
            IGrowable growable = (IGrowable) block;
            boolean canGrow = growable
                .func_149851_a(world, position.getX(), position.getY(), position.getZ(), world.isRemote);
            Item seed = block.getItemDropped(0, new Random(SEED_PROBE_RANDOM), 0);
            if (!(seed instanceof IPlantable)) {
                traceRejected(block, canonicalId, position, "baseline drop is not an IPlantable seed");
                return Optional.empty();
            }
            Object seedName = Item.itemRegistry.getNameForObject(seed);
            if (seedName == null) {
                traceRejected(block, canonicalId, position, "seed has no registered identity");
                return Optional.empty();
            }
            IPlantable plantable = (IPlantable) seed;
            boolean replacementMatches = plantable
                .getPlant(world, position.getX(), position.getY() - 1, position.getZ()) == block;
            boolean supportAcceptsSeed = world.getBlock(position.getX(), position.getY() - 1, position.getZ())
                .canSustainPlant(
                    world,
                    position.getX(),
                    position.getY() - 1,
                    position.getZ(),
                    ForgeDirection.UP,
                    plantable);
            int seedMetadata = block.damageDropped(0);
            Optional<Descriptor> result = classify(
                canonicalId,
                metadata,
                canGrow,
                seedName.toString(),
                seedMetadata,
                replacementMatches,
                supportAcceptsSeed,
                false);
            if (result.isPresent()) traceAccepted(block, canonicalId, position, result.get());
            else traceRejected(block, canonicalId, position, "replacement identity or supporting block was not proven");
            return result;
        } catch (RuntimeException | LinkageError failure) {
            traceRejected(
                block,
                canonicalId,
                position,
                "public crop evidence failed: " + failure.getClass()
                    .getSimpleName());
            return Optional.empty();
        }
    }

    Optional<Descriptor> classify(String blockId, int metadata, boolean canGrow, String seedItemId, int seedMetadata,
        boolean replacementMatches, boolean supportAcceptsSeed, boolean rightClickHarvest) {
        String canonicalId = canonicalId(blockId);
        if (canonicalId == null || metadata < 0 || metadata > 15) return Optional.empty();
        if (rightClickHarvest) {
            return Optional.of(
                new Descriptor(
                    CropFamily.GENERIC_RIGHT_CLICK,
                    canonicalId,
                    metadata,
                    metadata >= 7,
                    "right-click:block=" + canonicalId));
        }
        String canonicalSeed = canonicalId(seedItemId);
        if (canonicalSeed == null || seedMetadata < 0 || !replacementMatches || !supportAcceptsSeed) {
            return Optional.empty();
        }
        return Optional.of(
            new Descriptor(
                CropFamily.GENERIC_IGROWABLE,
                canonicalId,
                metadata,
                !canGrow,
                VanillaCropClassifier.materialIdentity(canonicalSeed, seedMetadata, "none")));
    }

    static boolean isPersistentOrIntegratedGrowable(Block block) {
        if (block == null) return true;
        Class<?> type = block.getClass();
        if (isPersistentGrowableType(type)) return true;
        return isIntegratedGrowableClassName(type.getName());
    }

    static boolean isPersistentGrowableType(Class<?> type) {
        return type == null || BlockStem.class.isAssignableFrom(type)
            || BlockSapling.class.isAssignableFrom(type)
            || BlockGrass.class.isAssignableFrom(type)
            || BlockMushroom.class.isAssignableFrom(type)
            || BlockTallGrass.class.isAssignableFrom(type)
            || BlockDoublePlant.class.isAssignableFrom(type);
    }

    static boolean isIntegratedGrowableClassName(String name) {
        return name == null || name.startsWith("com.pam.harvestcraft.")
            || name.startsWith("com.gtnewhorizon.cropsnh.")
            || "mods.natura.blocks.crops.CropBlock".equals(name);
    }

    private static String canonicalId(String value) {
        return value == null || value.trim()
            .isEmpty() ? null : value.trim();
    }

    private static void traceAccepted(Block block, String blockId, BasePosition position, Descriptor descriptor) {
        DevelopmentTrace.event(
            "farm-growable",
            "accepted",
            "block",
            blockId,
            "class",
            block.getClass()
                .getName(),
            "position",
            position,
            "family",
            descriptor.getFamily(),
            "mature",
            descriptor.isMature(),
            "material",
            descriptor.getReplantIdentity());
    }

    private static void traceRejected(Block block, String blockId, BasePosition position, String reason) {
        DevelopmentTrace.event(
            "farm-growable",
            "rejected",
            "block",
            blockId,
            "class",
            block == null ? "null"
                : block.getClass()
                    .getName(),
            "position",
            position,
            "reason",
            reason);
    }

    static final class Descriptor {

        private final CropFamily family;
        private final String blockId;
        private final int metadata;
        private final boolean mature;
        private final String replantIdentity;

        private Descriptor(CropFamily family, String blockId, int metadata, boolean mature, String replantIdentity) {
            this.family = family;
            this.blockId = blockId;
            this.metadata = metadata;
            this.mature = mature;
            this.replantIdentity = replantIdentity;
        }

        CropFamily getFamily() {
            return family;
        }

        boolean isMature() {
            return mature;
        }

        String getObservationFingerprint() {
            return blockId + "|meta="
                + metadata
                + "|adapter=igrowable|harvest="
                + (family == CropFamily.GENERIC_RIGHT_CLICK ? "right-click" : "break-replant");
        }

        String getReplantIdentity() {
            return replantIdentity;
        }
    }
}
