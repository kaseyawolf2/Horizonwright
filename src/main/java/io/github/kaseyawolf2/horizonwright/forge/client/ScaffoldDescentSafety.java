package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.function.IntPredicate;

import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;

/** Maximum three-block descent to a verified solid floor; never dig over an unknown shaft. */
public final class ScaffoldDescentSafety {

    private ScaffoldDescentSafety() {}

    public static boolean supportsLanding(net.minecraft.util.AxisAlignedBB box, int x, int y, int z) {
        return box != null && box.minX <= x
            && box.maxX >= x + 1
            && box.minZ <= z
            && box.maxZ >= z + 1
            && box.maxY >= y + 1;
    }

    /** Only known harmless, collision-free gaps may be crossed on the way to a verified floor. */
    public static boolean passableGap(Material material, AxisAlignedBB collision, boolean hazard) {
        return !hazard && collision == null
            && (material == Material.air || material == Material.plants || material == Material.vine);
    }

    public static boolean safeLanding(int removedY, double feetY, IntPredicate passable, IntPredicate safeFloor) {
        for (int y = removedY - 1; y >= 0 && feetY - (y + 1) <= 3.0; y--) {
            if (safeFloor.test(y)) return true;
            if (!passable.test(y)) return false;
        }
        return false;
    }
}
