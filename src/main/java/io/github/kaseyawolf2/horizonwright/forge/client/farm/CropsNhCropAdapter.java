package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

import net.minecraft.tileentity.TileEntity;

import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;

/** Reflection-isolated adapter for the CropsNH 2.0.91 crop-stick API. */
final class CropsNhCropAdapter {

    static final String BLOCK_ID = "cropsnh:cropSticks";
    static final String TILE_CLASS = "com.gtnewhorizon.cropsnh.tileentity.TileEntityCropSticks";
    static final String MACHINE_ONLY_REQUIREMENT = "com.gtnewhorizon.cropsnh.farming.requirements.growth.MachineOnlyGrowthRequirement";
    private final String supportedTileClass;

    CropsNhCropAdapter() {
        this(TILE_CLASS);
    }

    CropsNhCropAdapter(String supportedTileClass) {
        if (supportedTileClass == null || supportedTileClass.trim()
            .isEmpty()) throw new IllegalArgumentException("supported CropsNH tile class is required");
        this.supportedTileClass = supportedTileClass.trim();
    }

    Optional<Descriptor> read(String blockId, TileEntity tile) {
        if (!BLOCK_ID.equals(blockId) || tile == null
            || !supportedTileClass.equals(
                tile.getClass()
                    .getName()))
            return Optional.empty();
        try {
            if (!booleanValue(invoke(tile, "hasCrop"), "hasCrop")) return Optional.empty();
            boolean weed = booleanValue(invoke(tile, "hasWeed"), "hasWeed");
            boolean crossCrop = booleanValue(invoke(tile, "isCrossCrop"), "isCrossCrop");
            boolean mature = booleanValue(invoke(tile, "isMature"), "isMature");
            int growthProgress = intValue(invoke(tile, "getGrowthProgress"), "getGrowthProgress");
            Object seed = requireValue(invoke(tile, "getSeed"), "getSeed");
            Object crop = requireValue(invoke(seed, "getCrop"), "getCrop");
            String cropId = stringValue(invoke(crop, "getId"), "getId");
            Object rawRequirements = invoke(crop, "getGrowthRequirements");
            if (!(rawRequirements instanceof Collection)) {
                throw new IllegalStateException("CropsNH getGrowthRequirements returned an unsupported value");
            }
            boolean machineOnly = false;
            for (Object requirement : (Collection<?>) rawRequirements) {
                if (requirement != null && MACHINE_ONLY_REQUIREMENT.equals(
                    requirement.getClass()
                        .getName())) {
                    machineOnly = true;
                    break;
                }
            }
            return Optional.of(new Descriptor(cropId, growthProgress, mature, weed, crossCrop, machineOnly));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("CropsNH 2.0.91 crop-stick API does not match the tested adapter", failure);
        }
    }

    private static Object invoke(Object target, String method)
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method accessor = target.getClass()
            .getMethod(method);
        return accessor.invoke(target);
    }

    private static Object requireValue(Object value, String accessor) {
        if (value == null) throw new IllegalStateException("CropsNH " + accessor + " returned no value");
        return value;
    }

    private static boolean booleanValue(Object value, String accessor) {
        if (!(value instanceof Boolean))
            throw new IllegalStateException("CropsNH " + accessor + " returned an unsupported value");
        return (Boolean) value;
    }

    private static int intValue(Object value, String accessor) {
        if (!(value instanceof Number))
            throw new IllegalStateException("CropsNH " + accessor + " returned an unsupported value");
        return ((Number) value).intValue();
    }

    private static String stringValue(Object value, String accessor) {
        if (!(value instanceof String) || ((String) value).trim()
            .isEmpty()) throw new IllegalStateException("CropsNH " + accessor + " returned no identity");
        return ((String) value).trim();
    }

    static final class Descriptor {

        private final String cropId;
        private final int growthProgress;
        private final boolean mature;
        private final boolean weed;
        private final boolean crossCrop;
        private final boolean machineOnly;

        Descriptor(String cropId, int growthProgress, boolean mature, boolean weed, boolean crossCrop,
            boolean machineOnly) {
            if (cropId == null || cropId.trim()
                .isEmpty() || growthProgress < 0)
                throw new IllegalArgumentException("valid CropsNH crop state is required");
            this.cropId = cropId.trim();
            this.growthProgress = growthProgress;
            this.mature = mature;
            this.weed = weed;
            this.crossCrop = crossCrop;
            this.machineOnly = machineOnly;
        }

        CropFamily getFamily() {
            return CropFamily.CROPS_NH;
        }

        boolean isMature() {
            return mature;
        }

        boolean isProtected() {
            return weed || crossCrop || machineOnly;
        }

        String getObservationFingerprint() {
            return BLOCK_ID + "|crop="
                + cropId
                + "|growth="
                + growthProgress
                + "|mature="
                + mature
                + "|weed="
                + weed
                + "|cross="
                + crossCrop
                + "|machineOnly="
                + machineOnly;
        }

        String getCropIdentity() {
            return "cropsnh:crop-card|id=" + cropId;
        }
    }
}
