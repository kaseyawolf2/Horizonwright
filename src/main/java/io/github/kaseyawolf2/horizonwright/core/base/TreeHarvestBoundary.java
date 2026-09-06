package io.github.kaseyawolf2.horizonwright.core.base;

/** Planting bounds select roots; captured connected logs may extend beyond them. */
public final class TreeHarvestBoundary {

    public static final int OUTSIDE_REACH = 32;

    private TreeHarvestBoundary() {}

    public static boolean rootSelected(NamedArea area, BasePosition root, String treeId) {
        int side = treeId.endsWith("|2x2") ? 2 : 1;
        for (int x = 0; x < side; x++) for (int z = 0; z < side; z++)
            if (area.contains(new BasePosition(root.getDimensionId(), root.getX() + x, root.getY(), root.getZ() + z)))
                return true;
        return false;
    }

    public static boolean logWithinReach(BasePosition root, BasePosition block) {
        return block.getDimensionId() == root.getDimensionId() && block.getY() >= 0
            && block.getY() <= 255
            && Math.abs((long) block.getX() - root.getX()) <= OUTSIDE_REACH
            && Math.abs((long) block.getZ() - root.getZ()) <= OUTSIDE_REACH;
    }
}
