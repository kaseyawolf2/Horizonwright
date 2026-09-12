package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.List;
import java.util.function.Predicate;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

/** Chooses a nearby pickup while preserving the current digging target and its look direction. */
final class MiningPickupSteering {

    static final double PICKUP_STOP_DISTANCE = 0.35;

    private MiningPickupSteering() {}

    static final class Destination {

        final double x, y, z;

        Destination(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static Destination choose(double playerX, double eyeY, double feetY, double playerZ, BlockPosition block,
        double reach, List<Destination> drops, Predicate<Destination> safeStep) {
        Destination nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Destination drop : drops) {
            double distance = Math.hypot(drop.x - playerX, drop.z - playerZ);
            if (distance >= best || Math.abs(drop.y - feetY) > 1.25
                || !MiningWalkInput.withinReach(drop.x, eyeY, drop.z, block, reach)
                || MiningWalkInput.tooClose(drop.x, drop.z, 0, 0, block)
                || distance > PICKUP_STOP_DISTANCE && !safeStep.test(drop)) continue;
            nearest = drop;
            best = distance;
        }
        return nearest;
    }

    static float[] relativeInput(float yawDegrees, double dx, double dz) {
        double distance = Math.hypot(dx, dz);
        if (distance == 0) return new float[] { 0, 0 };
        double yaw = Math.toRadians(yawDegrees);
        return new float[] { (float) ((-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / distance),
            (float) ((Math.cos(yaw) * dx + Math.sin(yaw) * dz) / distance) };
    }
}
