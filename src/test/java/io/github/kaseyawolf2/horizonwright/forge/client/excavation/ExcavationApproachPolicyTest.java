package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.*;

import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationGoalKind;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;

public class ExcavationApproachPolicyTest {

    @Test
    public void remainingCanopyRetainsSupportAndExactLeafBreakingAcrossRetries() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            NavigationRequest request = ExcavationApproachPolicy.create(
                "canopy-" + attempt,
                1L,
                0,
                new BlockPosition(232, 93, 408),
                attempt,
                true,
                Collections.singletonList("minecraft:leaves"),
                0L,
                1000L);
            assertTrue(request.isPlacementAllowed());
            assertEquals(Collections.singletonList("minecraft:leaves"), request.getAllowedBreakBlockIds());
            assertEquals(attempt == 1 ? NavigationGoalKind.ADJACENT : NavigationGoalKind.RANGE, request.getGoalKind());
        }
    }

    @Test
    public void ordinaryTargetsDoNotGainPermissionToBreakNearbyBlocks() {
        NavigationRequest normal = ExcavationApproachPolicy
            .create("normal", 1L, 0, new BlockPosition(1, 64, 1), 1, false, Collections.emptyList(), 0L, 1000L);
        assertFalse(normal.isPlacementAllowed());
        assertFalse(normal.isBreakingAllowed());
        NavigationRequest obstruction = ExcavationApproachPolicy
            .create("obstruction", 1L, 0, new BlockPosition(1, 64, 1), 2, true, Collections.emptyList(), 0L, 1000L);
        assertTrue(obstruction.isPlacementAllowed());
        assertFalse(obstruction.isBreakingAllowed());
    }
}
