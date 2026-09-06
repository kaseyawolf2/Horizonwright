package io.github.kaseyawolf2.horizonwright.forge.client.farm;

/** Pure final-reach decision for a positively identified crop target. */
final class FarmReachability {

    static final int DROP_COLLECTION_TOLERANCE = 1;
    private static final double FACE_INSET = 0.001D;

    private FarmReachability() {}

    /** Stand back from a stacked trunk rather than hugging its collision box. */
    static int[][] fruitLogApproachPoints(int x, int feetY, int z, double playerX, double playerZ) {
        int[][] points = { { x - 2, feetY, z }, { x + 2, feetY, z }, { x, feetY, z - 2 }, { x, feetY, z + 2 } };
        java.util.Arrays.sort(
            points,
            java.util.Comparator.comparingDouble(
                point -> Math.pow(point[0] + 0.5D - playerX, 2) + Math.pow(point[2] + 0.5D - playerZ, 2)));
        return points;
    }

    static boolean canInteract(double distanceSquared, double reachSquared, boolean rayHitPresent,
        boolean rayHitTarget) {
        if (distanceSquared < 0.0D || reachSquared < 0.0D || distanceSquared > reachSquared) return false;
        // Some vanilla plant geometries return no world-ray hit. Once the exact crop is independently
        // identified and within reach, a null hit means no collidable block obstructed the segment.
        return !rayHitPresent || rayHitTarget;
    }

    static int collectionFeetY(double playerY) {
        int feetY = (int) Math.floor(playerY);
        return Math.max(0, Math.min(255, feetY));
    }

    /** Center first, followed by a point just inside each of the six block faces. */
    static double[][] interactionProbes(int x, int y, int z) {
        double minX = x + FACE_INSET;
        double minY = y + FACE_INSET;
        double minZ = z + FACE_INSET;
        double maxX = x + 1.0D - FACE_INSET;
        double maxY = y + 1.0D - FACE_INSET;
        double maxZ = z + 1.0D - FACE_INSET;
        double centerX = x + 0.5D;
        double centerY = y + 0.5D;
        double centerZ = z + 0.5D;
        return new double[][] { { centerX, centerY, centerZ }, { minX, centerY, centerZ }, { maxX, centerY, centerZ },
            { centerX, minY, centerZ }, { centerX, maxY, centerZ }, { centerX, centerY, minZ },
            { centerX, centerY, maxZ } };
    }
}
