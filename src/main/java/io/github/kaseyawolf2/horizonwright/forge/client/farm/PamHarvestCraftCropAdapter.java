package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;

import net.minecraft.block.Block;

import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;

/** Reflection-isolated adapter for the pinned Pam's HarvestCraft 1.3.11-GTNH blocks. */
final class PamHarvestCraftCropAdapter {

    static final String CROP_CLASS = "com.pam.harvestcraft.BlockPamCrop";
    static final String HANGING_FRUIT_CLASS = "com.pam.harvestcraft.BlockPamFruit";
    static final String FRUITING_LOG_CLASS = "com.pam.harvestcraft.BlockPamFruitingLog";
    private static final String BLOCK_REGISTRY_CLASS = "com.pam.harvestcraft.BlockRegistry";
    private static final String CROP_RIGHT_CLICK_FLAG = "rightclickharvestCrop";
    private static final String FRUIT_RIGHT_CLICK_FLAG = "rightclickharvestFruit";
    private final PamHarvestCraftCompatibilityStatus compatibility;

    PamHarvestCraftCropAdapter() {
        this(PamHarvestCraftCompatibilityProbe.inspect());
    }

    PamHarvestCraftCropAdapter(PamHarvestCraftCompatibilityStatus compatibility) {
        if (compatibility == null) throw new IllegalArgumentException("HarvestCraft compatibility status is required");
        this.compatibility = compatibility;
    }

    Optional<Descriptor> read(Block block, String blockId, int metadata) {
        if (block == null) return Optional.empty();
        String blockClass = block.getClass()
            .getName();
        if (!isSupportedClass(blockClass) || !compatibility.isAvailable()) return Optional.empty();
        boolean cropRightClick = !CROP_CLASS.equals(blockClass) || readBooleanFlag(block, CROP_RIGHT_CLICK_FLAG);
        boolean fruitRightClick = !HANGING_FRUIT_CLASS.equals(blockClass)
            || readBooleanFlag(block, FRUIT_RIGHT_CLICK_FLAG);
        return classify(blockClass, blockId, metadata, cropRightClick, fruitRightClick);
    }

    PamHarvestCraftCompatibilityStatus compatibility() {
        return compatibility;
    }

    Optional<Descriptor> classify(String blockClass, String blockId, int metadata, boolean cropRightClick,
        boolean fruitRightClick) {
        if (blockClass == null || blockId == null
            || blockId.trim()
                .isEmpty()
            || metadata < 0
            || metadata > 15) return Optional.empty();
        String canonicalId = blockId.trim();
        if (CROP_CLASS.equals(blockClass)) {
            if (!cropRightClick) return Optional.empty();
            return Optional.of(new Descriptor(CropFamily.PAM_CROP, canonicalId, metadata, metadata == 7));
        }
        if (HANGING_FRUIT_CLASS.equals(blockClass)) {
            if (!fruitRightClick) return Optional.empty();
            return Optional.of(new Descriptor(CropFamily.PAM_HANGING_FRUIT, canonicalId, metadata, metadata == 2));
        }
        if (FRUITING_LOG_CLASS.equals(blockClass)) {
            return Optional.of(new Descriptor(CropFamily.PAM_FRUITING_LOG, canonicalId, metadata, (metadata & 3) == 3));
        }
        return Optional.empty();
    }

    private static boolean isSupportedClass(String blockClass) {
        return CROP_CLASS.equals(blockClass) || HANGING_FRUIT_CLASS.equals(blockClass)
            || FRUITING_LOG_CLASS.equals(blockClass);
    }

    private static boolean readBooleanFlag(Block block, String fieldName) {
        try {
            ClassLoader loader = block.getClass()
                .getClassLoader();
            Class<?> registry = Class.forName(BLOCK_REGISTRY_CLASS, false, loader);
            Field field = registry.getField(fieldName);
            if (field.getType() != Boolean.TYPE || !Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException("Pam's HarvestCraft flag has an unsupported shape: " + fieldName);
            }
            return field.getBoolean(null);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException failure) {
            throw new IllegalStateException(
                "Pam's HarvestCraft 1.3.11-GTNH crop API does not match the tested adapter",
                failure);
        }
    }

    static final class Descriptor {

        private final CropFamily family;
        private final String blockId;
        private final int metadata;
        private final boolean mature;

        private Descriptor(CropFamily family, String blockId, int metadata, boolean mature) {
            this.family = family;
            this.blockId = blockId;
            this.metadata = metadata;
            this.mature = mature;
        }

        CropFamily getFamily() {
            return family;
        }

        boolean isMature() {
            return mature;
        }

        String getObservationFingerprint() {
            return blockId + "|meta=" + metadata + "|adapter=pam-1.3.11-gtnh";
        }

        String getHarvestIdentity() {
            return "harvestcraft:right-click|block=" + blockId;
        }
    }
}
