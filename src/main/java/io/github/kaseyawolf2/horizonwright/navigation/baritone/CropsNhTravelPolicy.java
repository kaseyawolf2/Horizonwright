package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Exact CropsNH crop-stick travel rules; ordinary walking and jumping away remain allowed. */
final class CropsNhTravelPolicy {

    static final String CROP_STICKS_ID = "cropsnh:cropSticks";
    static final int SPRINT_SUPPRESSION_RADIUS = 4;
    static final int SPRINT_SUPPRESSION_VERTICAL_RADIUS = 2;
    static final double CROP_LANDING_PENALTY = 1_000D;

    boolean shouldSuppressSprint(World world, EntityPlayer player) {
        if (world == null || player == null) return false;
        int centerX = floor(player.posX);
        int centerY = floor(player.posY);
        int centerZ = floor(player.posZ);
        for (int x = centerX - SPRINT_SUPPRESSION_RADIUS; x <= centerX + SPRINT_SUPPRESSION_RADIUS; x++) {
            for (int z = centerZ - SPRINT_SUPPRESSION_RADIUS; z <= centerZ + SPRINT_SUPPRESSION_RADIUS; z++) {
                if (!world.getChunkProvider()
                    .chunkExists(x >> 4, z >> 4)) continue;
                for (int y = Math.max(0, centerY - SPRINT_SUPPRESSION_VERTICAL_RADIUS); y
                    <= Math.min(255, centerY + SPRINT_SUPPRESSION_VERTICAL_RADIUS); y++) {
                    if (isCropSticks(world.getBlock(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    double landingPenalty(int sourceY, int destinationY, Block destinationFeetBlock) {
        return landingPenalty(sourceY, destinationY, isCropSticks(destinationFeetBlock));
    }

    double landingPenalty(int sourceY, int destinationY, boolean destinationIsCropSticks) {
        return sourceY != destinationY && destinationIsCropSticks ? CROP_LANDING_PENALTY : 0D;
    }

    static boolean isCropSticks(Block block) {
        if (block == null) return false;
        Object name = Block.blockRegistry.getNameForObject(block);
        return name != null && isCropSticksId(name.toString());
    }

    static boolean isCropSticksId(String blockId) {
        return CROP_STICKS_ID.equals(blockId);
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
