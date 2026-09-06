package io.github.kaseyawolf2.horizonwright.forge.client.farm;

/** Endpoint must enter the supporting block, not stop in the air cell above it. */
final class TreeInteractionGeometry {

    private TreeInteractionGeometry() {}

    static double supportProbeY(int saplingY) {
        return saplingY - 0.001D;
    }
}
