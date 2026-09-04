package io.github.kaseyawolf2.horizonwright.forge.client.farm;

/** Pure final-reach decision for a positively identified crop target. */
final class FarmReachability {

    private FarmReachability() {}

    static boolean canInteract(double distanceSquared, double reachSquared, boolean rayHitPresent,
        boolean rayHitTarget) {
        if (distanceSquared < 0.0D || reachSquared < 0.0D || distanceSquared > reachSquared) return false;
        // Some vanilla plant geometries return no world-ray hit. Once the exact crop is independently
        // identified and within reach, a null hit means no collidable block obstructed the segment.
        return !rayHitPresent || rayHitTarget;
    }
}
