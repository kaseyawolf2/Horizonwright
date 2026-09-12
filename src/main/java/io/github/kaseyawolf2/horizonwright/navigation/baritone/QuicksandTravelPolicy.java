package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import net.minecraft.block.Block;
import net.minecraft.world.World;

/** Beta3 quicksand is the metadata-1 variant of BOP mud, not ordinary mud. */
public final class QuicksandTravelPolicy {

    public interface HazardLookup {

        boolean isQuicksand(int x, int y, int z);
    }

    private QuicksandTravelPolicy() {}

    public static boolean isHazard(String blockId, int metadata) {
        return isQuicksand(blockId, metadata) || "BiomesOPlenty:plants".equals(blockId) && metadata == 5;
    }

    public static boolean isHazard(Block block, int metadata) {
        return block != null && isHazard(Block.blockRegistry.getNameForObject(block), metadata);
    }

    public static boolean isHazard(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && isHazard(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
    }

    public static boolean isQuicksand(String blockId, int metadata) {
        return "BiomesOPlenty:mud".equals(blockId) && metadata == 1;
    }

    public static boolean isQuicksand(Block block, int metadata) {
        return block != null && isQuicksand(Block.blockRegistry.getNameForObject(block), metadata);
    }

    public static boolean isQuicksand(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && isQuicksand(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
    }

    /** Conservatively checks support, feet and head cells throughout a step, diagonal, jump or fall. */
    static boolean crossesQuicksand(int sx, int sy, int sz, int dx, int dy, int dz, HazardLookup blocks) {
        for (int x = Math.min(sx, dx); x <= Math.max(sx, dx); x++) {
            for (int z = Math.min(sz, dz); z <= Math.max(sz, dz); z++) {
                // A player already in quicksand must still be able to escape to dry ground.
                if (x == sx && z == sz && (sx != dx || sz != dz)) continue;
                for (int y = Math.max(0, Math.min(sy, dy) - 1); y <= Math.min(255, Math.max(sy, dy) + 1); y++) {
                    if (blocks.isQuicksand(x, y, z)) return true;
                }
            }
        }
        return false;
    }
}
