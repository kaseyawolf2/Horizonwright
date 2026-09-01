package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

/** Bounded client-thread observer for exact vanilla 1.7.10 crop blocks and seed inventory. */
public final class MinecraftVanillaFarmObserver {

    private static final long MAX_SCANNED_BLOCKS = 65_536L;

    private final Minecraft minecraft;
    private final ProfileFarmConfiguration configuration;
    private final VanillaCropClassifier classifier = new VanillaCropClassifier();
    private final MinecraftContainerSnapshotter items = new MinecraftContainerSnapshotter();

    public MinecraftVanillaFarmObserver(Minecraft minecraft, ProfileFarmConfiguration configuration) {
        if (minecraft == null || configuration == null) {
            throw new IllegalArgumentException("minecraft and farm configuration are required");
        }
        this.minecraft = minecraft;
        this.configuration = configuration;
    }

    public NamedArea resolvePlot(String plotId) {
        requireClient();
        NamedArea plot = configuration.resolve(plotId);
        requireCurrentDimension(plot);
        requireBoundedLoadedArea(plot);
        return plot;
    }

    public List<CropObservation> scan(NamedArea plot) {
        requireClient();
        requireCurrentDimension(plot);
        requireBoundedLoadedArea(plot);
        List<CropObservation> result = new ArrayList<>();
        BasePosition minimum = plot.getMinimum();
        BasePosition maximum = plot.getMaximum();
        for (int y = minimum.getY(); y <= maximum.getY(); y++) {
            for (int z = minimum.getZ(); z <= maximum.getZ(); z++) {
                for (int x = minimum.getX(); x <= maximum.getX(); x++) {
                    CropObservation observation = observeIfCrop(new BasePosition(minimum.getDimensionId(), x, y, z));
                    if (observation != null) result.add(observation);
                }
            }
        }
        return result;
    }

    public CropObservation observeRequired(BasePosition position) {
        requireClient();
        if (position == null || position.getDimensionId() != minecraft.theWorld.provider.dimensionId
            || !minecraft.theWorld.getChunkProvider()
                .chunkExists(position.getX() >> 4, position.getZ() >> 4)) {
            throw new IllegalStateException("farm target is not loaded in the current dimension");
        }
        CropObservation observation = observeIfCrop(position);
        if (observation == null)
            throw new IllegalStateException("frozen farm target is no longer a supported vanilla crop");
        return observation;
    }

    CropObservation observeSupported(BasePosition position) {
        requireClient();
        if (position == null || position.getDimensionId() != minecraft.theWorld.provider.dimensionId
            || !minecraft.theWorld.getChunkProvider()
                .chunkExists(position.getX() >> 4, position.getZ() >> 4))
            return null;
        return observeIfCrop(position);
    }

    int findHotbarSeed(String requiredSeedFingerprint) {
        requireClient();
        for (int slot = 0; slot < 9; slot++) {
            ItemFingerprint fingerprint = items.fingerprint(minecraft.thePlayer.inventory.mainInventory[slot]);
            if (fingerprint != null && requiredSeedFingerprint.equals(materialIdentity(fingerprint))) return slot;
        }
        return -1;
    }

    String hotbarMaterialIdentity(int slot) {
        requireClient();
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("hotbar slot must be from 0 to 8");
        ItemFingerprint fingerprint = items.fingerprint(minecraft.thePlayer.inventory.mainInventory[slot]);
        return fingerprint == null ? null : materialIdentity(fingerprint);
    }

    public SeedReserveEvidence reserve(long inventoryRevision, String requiredSeedFingerprint, int minimumReserve) {
        requireClient();
        int count = 0;
        StringBuilder snapshot = new StringBuilder();
        for (int slot = 0; slot < minecraft.thePlayer.inventory.mainInventory.length; slot++) {
            ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
            ItemFingerprint fingerprint = items.fingerprint(stack);
            snapshot.append(slot)
                .append('=')
                .append(fingerprint == null ? "empty" : fingerprint.toString())
                .append(';');
            if (fingerprint != null && requiredSeedFingerprint.equals(materialIdentity(fingerprint))) {
                count = Math.addExact(count, fingerprint.getCount());
            }
        }
        ItemFingerprint cursor = items.fingerprint(minecraft.thePlayer.inventory.getItemStack());
        snapshot.append("cursor=")
            .append(cursor == null ? "empty" : cursor.toString());
        return new SeedReserveEvidence(
            inventoryRevision,
            "sha256:" + sha256(snapshot.toString()),
            requiredSeedFingerprint,
            count,
            minimumReserve);
    }

    static String materialIdentity(ItemFingerprint fingerprint) {
        if (fingerprint == null) throw new IllegalArgumentException("item fingerprint is required");
        return VanillaCropClassifier
            .materialIdentity(fingerprint.getItemId(), fingerprint.getMetadata(), fingerprint.getDataHash());
    }

    private CropObservation observeIfCrop(BasePosition position) {
        Block block = minecraft.theWorld.getBlock(position.getX(), position.getY(), position.getZ());
        Object registryName = Block.blockRegistry.getNameForObject(block);
        int metadata = minecraft.theWorld.getBlockMetadata(position.getX(), position.getY(), position.getZ());
        VanillaCropClassifier.Descriptor descriptor = classifier
            .classify(block, registryName == null ? null : registryName.toString(), metadata);
        if (descriptor == null) return null;
        TileEntity tile = minecraft.theWorld.getTileEntity(position.getX(), position.getY(), position.getZ());
        return new CropObservation(
            position,
            descriptor.getFamily(),
            descriptor.getObservationFingerprint() + (tile == null ? "|tile=none"
                : "|tile=" + tile.getClass()
                    .getName()),
            descriptor.getSeedFingerprint(),
            true,
            descriptor.isMature(),
            tile != null);
    }

    private void requireClient() {
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.theWorld.provider == null) {
            throw new IllegalStateException("a joined Minecraft client thread is required for farm observation");
        }
    }

    private void requireCurrentDimension(NamedArea plot) {
        if (plot == null || plot.getMinimum()
            .getDimensionId() != minecraft.theWorld.provider.dimensionId) {
            throw new IllegalStateException("named farm plot is in another dimension");
        }
    }

    private void requireBoundedLoadedArea(NamedArea plot) {
        BasePosition minimum = plot.getMinimum();
        BasePosition maximum = plot.getMaximum();
        long x = (long) maximum.getX() - minimum.getX() + 1L;
        long y = (long) maximum.getY() - minimum.getY() + 1L;
        long z = (long) maximum.getZ() - minimum.getZ() + 1L;
        long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact(x, y), z);
        } catch (ArithmeticException failure) {
            throw new IllegalStateException("named farm plot volume is too large", failure);
        }
        if (volume > MAX_SCANNED_BLOCKS) {
            throw new IllegalStateException("named farm plot exceeds the 65,536-block observation bound");
        }
        for (int chunkX = minimum.getX() >> 4; chunkX <= maximum.getX() >> 4; chunkX++) {
            for (int chunkZ = minimum.getZ() >> 4; chunkZ <= maximum.getZ() >> 4; chunkZ++) {
                if (!minecraft.theWorld.getChunkProvider()
                    .chunkExists(chunkX, chunkZ)) {
                    throw new IllegalStateException("every chunk in the named farm plot must be loaded");
                }
            }
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte current : digest) encoded.append(String.format("%02x", current & 0xff));
            return encoded.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
