package io.github.kaseyawolf2.horizonwright.core.navigation;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

public class ScaffoldNavigationRequestTest {

    @Test
    public void scaffoldingPreservesGoalDeadlineAndLeafRestrictions() {
        NavigationRequest original = NavigationRequest
            .nearAllowingPlacementAndBreaking("tree", 2, 7, 1, 80, 3, 2, Arrays.asList("minecraft:leaves"), 100, 500);
        List<BlockPosition> excluded = new ArrayList<>(Arrays.asList(new BlockPosition(1, 64, 3)));
        NavigationRequest scoped = original.withScaffolding(excluded);
        excluded.clear();
        assertFalse(original.isScaffoldingAllowed());
        assertTrue(scoped.isScaffoldingAllowed());
        assertEquals(
            1,
            scoped.getScaffoldExclusions()
                .size());
        assertEquals(original.getDeadlineNanos(), scoped.getDeadlineNanos());
        assertEquals(original.getGoalKind(), scoped.getGoalKind());
        assertEquals(7, scoped.getDimensionId());
        assertEquals(original.getAllowedBreakBlockIds(), scoped.getAllowedBreakBlockIds());
        assertTrue(scoped.isPlacementAllowed());
    }

    @Test
    public void removalOnlyDoesNotPermitNewSupportsOrGeneralBreaking() {
        NavigationRequest walking = new NavigationRequest("cleanup", 1, 0, 1, 64, 2, 0);
        NavigationRequest cleanup = walking.withScaffolding(Collections.emptyList());
        assertFalse(walking.isBreakingAllowed());
        assertFalse(walking.isScaffoldingAllowed());
        assertFalse(cleanup.isPlacementAllowed());
        assertTrue(cleanup.isBreakingAllowed());
        assertTrue(
            cleanup.getAllowedBreakBlockIds()
                .isEmpty());
    }
}
