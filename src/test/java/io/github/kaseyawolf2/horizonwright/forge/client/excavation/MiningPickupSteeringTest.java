package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.MiningPickupSteering.Destination;

public class MiningPickupSteeringTest {

    private final BlockPosition block = new BlockPosition(0, 64, 0);

    @Test
    public void selectsNearestSafePickupWithoutChangingDiggingTarget() {
        Destination obstructed = new Destination(1, 64, 3);
        Destination safe = new Destination(-1, 64, 3);
        Destination farther = new Destination(-2, 64, 3);
        assertSame(
            safe,
            MiningPickupSteering.choose(
                0.5,
                65.62,
                64,
                3,
                block,
                4.35,
                Arrays.asList(farther, obstructed, safe),
                drop -> drop != obstructed));
    }

    @Test
    public void cannotChaseDropsOutsideBreakingReachAtAnotherHeightOrOnDiggingBlock() {
        Destination far = new Destination(8, 64, 3);
        Destination high = new Destination(1, 67, 3);
        Destination onTarget = new Destination(0.5, 64, 0.5);
        assertNull(
            MiningPickupSteering
                .choose(0.5, 65.62, 64, 3, block, 4.35, Arrays.asList(far, high, onTarget), drop -> true));
        assertNull(
            MiningPickupSteering.choose(
                0.5,
                65.62,
                64,
                3,
                block,
                4.35,
                Collections.singletonList(new Destination(-1, 64, 3)),
                drop -> false));
    }

    @Test
    public void holdsAtDropForPickupSynchronizationWhileDiggingCanContinue() {
        Destination arrived = new Destination(0.5, 64, 3);
        assertSame(
            arrived,
            MiningPickupSteering
                .choose(0.5, 65.62, 64, 3, block, 4.35, Collections.singletonList(arrived), drop -> false));
        assertArrayEquals(new float[] { 0, 0 }, MiningPickupSteering.relativeInput(83, 0, 0), 0.0001F);
    }

    @Test
    public void strafeAndBackwardInputsKeepWorldTravelIndependentOfLookYaw() {
        // Facing south while the pickup is east: strafe left without rotating away from the block.
        assertArrayEquals(new float[] { 0, 1 }, MiningPickupSteering.relativeInput(0, 1, 0), 0.0001F);
        assertArrayEquals(new float[] { -1, 0 }, MiningPickupSteering.relativeInput(0, 0, -1), 0.0001F);
        for (int yaw = -180; yaw <= 180; yaw += 30) {
            float[] input = MiningPickupSteering.relativeInput(yaw, 3, -4);
            double radians = Math.toRadians(yaw);
            assertEquals(0.6, -Math.sin(radians) * input[0] + Math.cos(radians) * input[1], 0.0001);
            assertEquals(-0.8, Math.cos(radians) * input[0] + Math.sin(radians) * input[1], 0.0001);
        }
    }
}
