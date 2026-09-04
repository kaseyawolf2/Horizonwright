package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationMode;

public class TreeLogRecoveryPlannerTest {

    private static final CylinderExcavationSpec AREA = new CylinderExcavationSpec(
        0,
        0,
        0,
        10,
        0,
        20,
        ExcavationMode.CLEAN_VOLUME);

    @Test
    public void findsTheRootAndOrdersEveryConnectedLogFromTopToBottom() {
        BlockPosition leaf = position(0, 8, 0);
        Map<BlockPosition, TreeLogRecoveryPlanner.Kind> blocks = new HashMap<>();
        blocks.put(leaf, TreeLogRecoveryPlanner.Kind.LEAF);
        blocks.put(position(0, 7, 0), TreeLogRecoveryPlanner.Kind.LEAF);
        blocks.put(position(0, 6, 0), TreeLogRecoveryPlanner.Kind.WOOD);
        blocks.put(position(0, 5, 0), TreeLogRecoveryPlanner.Kind.WOOD);
        blocks.put(position(0, 4, 0), TreeLogRecoveryPlanner.Kind.WOOD);
        blocks.put(position(1, 6, 0), TreeLogRecoveryPlanner.Kind.WOOD);

        Optional<TreeLogRecoveryPlan> planned = TreeLogRecoveryPlanner
            .plan(AREA, leaf, position -> blocks.getOrDefault(position, TreeLogRecoveryPlanner.Kind.OTHER));

        assertTrue(planned.isPresent());
        assertEquals(
            position(0, 4, 0),
            planned.get()
                .getRootLog());
        assertEquals(
            position(0, 6, 0),
            planned.get()
                .getLogsTopDown()
                .get(0));
        assertEquals(
            position(1, 6, 0),
            planned.get()
                .getLogsTopDown()
                .get(1));
        assertEquals(
            position(0, 5, 0),
            planned.get()
                .getLogsTopDown()
                .get(2));
        assertEquals(
            position(0, 4, 0),
            planned.get()
                .getLogsTopDown()
                .get(3));
    }

    @Test
    public void returnsNoPlanWhenLeavesHaveNoReachableWood() {
        BlockPosition leaf = position(0, 8, 0);

        Optional<TreeLogRecoveryPlan> planned = TreeLogRecoveryPlanner.plan(
            AREA,
            leaf,
            position -> position.equals(leaf) ? TreeLogRecoveryPlanner.Kind.LEAF : TreeLogRecoveryPlanner.Kind.OTHER);

        assertFalse(planned.isPresent());
    }

    @Test
    public void neverIncludesAConnectedLogOutsideTheExcavationBoundary() {
        BlockPosition leaf = position(10, 8, 0);
        BlockPosition inside = position(9, 8, 0);
        BlockPosition outside = position(11, 8, 0);
        Map<BlockPosition, TreeLogRecoveryPlanner.Kind> blocks = new HashMap<>();
        blocks.put(leaf, TreeLogRecoveryPlanner.Kind.LEAF);
        blocks.put(inside, TreeLogRecoveryPlanner.Kind.WOOD);
        blocks.put(outside, TreeLogRecoveryPlanner.Kind.WOOD);

        TreeLogRecoveryPlan planned = TreeLogRecoveryPlanner
            .plan(AREA, leaf, position -> blocks.getOrDefault(position, TreeLogRecoveryPlanner.Kind.OTHER))
            .get();

        assertEquals(
            1,
            planned.getLogsTopDown()
                .size());
        assertEquals(
            inside,
            planned.getLogsTopDown()
                .get(0));
        assertFalse(
            planned.getLogsTopDown()
                .contains(outside));
    }

    private static BlockPosition position(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }
}
