package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationObservationRequest;

/** Exact client-thread world observer; optional implementation classes are recognized only by tested class name. */
final class MinecraftExcavationObserver {

    private static final String OPENBLOCKS_GRAVE_TILE = "openblocks.common.tileentity.TileEntityGrave";

    private final Minecraft minecraft;

    MinecraftExcavationObserver(Minecraft minecraft) {
        if (minecraft == null) throw new IllegalArgumentException("minecraft must not be null");
        this.minecraft = minecraft;
    }

    ExcavationObservation observe(ExcavationObservationRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        return observePosition(request.getDimensionId(), request.getPosition());
    }

    ExcavationObservation observePosition(int dimensionId, BlockPosition position) {
        if (position == null) throw new IllegalArgumentException("position must not be null");
        requireClientWorld(dimensionId);
        World world = minecraft.theWorld;
        if (!world.blockExists(position.getX(), position.getY(), position.getZ())) {
            return ExcavationBlockClassifier.classify(
                new ExcavationBlockEvidence(position, "unloaded", false, false, false, false, false, false, false));
        }
        Block block = world.getBlock(position.getX(), position.getY(), position.getZ());
        int metadata = world.getBlockMetadata(position.getX(), position.getY(), position.getZ());
        TileEntity tile = world.getTileEntity(position.getX(), position.getY(), position.getZ());
        boolean air = block == null || block.isAir(world, position.getX(), position.getY(), position.getZ());
        Material material = block == null ? null : block.getMaterial();
        boolean fluid = material != null && material.isLiquid();
        boolean source = fluid && metadata == 0;
        boolean grave = tile != null && OPENBLOCKS_GRAVE_TILE.equals(
            tile.getClass()
                .getName());
        boolean infrastructure = tile != null && !grave;
        float hardness = block == null ? -1.0F
            : block.getBlockHardness(world, position.getX(), position.getY(), position.getZ());
        boolean breakable = !air && !fluid && !grave && !infrastructure && hardness >= 0.0F;
        return ExcavationBlockClassifier.classify(
            new ExcavationBlockEvidence(
                position,
                fingerprint(block, metadata, tile),
                true,
                air,
                fluid,
                source,
                grave,
                infrastructure,
                breakable));
    }

    private void requireClientWorld(int dimensionId) {
        if (!minecraft.func_152345_ab() || minecraft.theWorld == null
            || minecraft.thePlayer == null
            || minecraft.theWorld.provider == null) {
            throw new IllegalStateException("excavation observation requires a joined client thread");
        }
        if (minecraft.theWorld.provider.dimensionId != dimensionId) {
            throw new IllegalStateException("excavation observation dimension does not match the joined world");
        }
    }

    private static String fingerprint(Block block, int metadata, TileEntity tile) {
        if (block == null) return "minecraft:air@0";
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(block);
        String name = id == null ? Block.blockRegistry.getNameForObject(block) : id.modId + ':' + id.name;
        if (name == null || name.trim()
            .isEmpty())
            name = block.getClass()
                .getName();
        return name + '@'
            + metadata
            + (tile == null ? ""
                : "#tile=" + tile.getClass()
                    .getName());
    }
}
