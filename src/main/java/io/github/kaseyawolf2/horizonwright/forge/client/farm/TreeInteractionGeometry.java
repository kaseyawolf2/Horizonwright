package io.github.kaseyawolf2.horizonwright.forge.client.farm;

/** Endpoint must enter the supporting block, not stop in the air cell above it. */
final class TreeInteractionGeometry {

    private TreeInteractionGeometry() {}

    static double supportProbeY(int saplingY) {
        return saplingY - 0.001D;
    }

    static int[][] standBackPositions(int x, int y, int z, double playerX, double playerZ) {
        int[][] points = { { x - 2, y, z }, { x + 2, y, z }, { x, y, z - 2 }, { x, y, z + 2 } };
        java.util.Arrays.sort(
            points,
            java.util.Comparator
                .comparingDouble(p -> Math.pow(p[0] + 0.5D - playerX, 2) + Math.pow(p[2] + 0.5D - playerZ, 2)));
        return points;
    }
}
