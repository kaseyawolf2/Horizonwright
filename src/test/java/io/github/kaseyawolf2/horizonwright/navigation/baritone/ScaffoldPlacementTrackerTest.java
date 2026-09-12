package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

public class ScaffoldPlacementTrackerTest {

    private static class Log extends BlockLog {
    }

    private final Block log = new Log();
    private final ScaffoldPlacementTracker tracker = new ScaffoldPlacementTracker();

    private C08PacketPlayerBlockPlacement packet(int y, int face) {
        return new C08PacketPlayerBlockPlacement(
            196,
            y,
            335,
            face,
            new ItemStack(new ItemBlock(log), 16),
            0.5F,
            1F,
            0.5F);
    }

    @Test
    public void actualPacketFaceTracksTowerWithoutAnyScreenCrosshair() {
        BlockPosition pos = tracker.sent(packet(70, 1), 10, p -> true);
        assertEquals(new BlockPosition(196, 71, 335), pos);
        List<BlockPosition> confirmed = new ArrayList<>();
        tracker.observe(11, p -> log, (p, b) -> confirmed.add(p));
        assertEquals(java.util.Collections.singletonList(pos), confirmed);
        assertTrue(tracker.isEmpty());
    }

    @Test
    public void allSixPacketFacesUseTheirActualAdjacentCell() {
        int[][] offsets = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
        for (int face = 0; face < 6; face++) assertEquals(
            new BlockPosition(196 + offsets[face][0], 70 + offsets[face][1], 335 + offsets[face][2]),
            tracker.sent(packet(70, face), 10, p -> true));
    }

    @Test
    public void overlappingPlacementsAndDelayedUpdatesAreNotLost() {
        BlockPosition low = tracker.sent(packet(70, 1), 10, p -> true);
        BlockPosition high = tracker.sent(packet(71, 1), 11, p -> true);
        Map<BlockPosition, Block> world = new HashMap<>();
        List<BlockPosition> confirmed = new ArrayList<>();
        tracker.observe(12, world::get, (p, b) -> confirmed.add(p));
        assertEquals(
            2,
            tracker.positions()
                .size());
        assertTrue(confirmed.isEmpty());
        world.put(low, log);
        tracker.observe(13, world::get, (p, b) -> confirmed.add(p));
        assertEquals(java.util.Collections.singletonList(high), tracker.positions());
        world.put(high, log);
        tracker.observe(14, world::get, (p, b) -> confirmed.add(p));
        assertEquals(2, confirmed.size());
        assertTrue(tracker.isEmpty());
    }

    @Test
    public void rejectedPlacementExpiresWithoutClaimingExistingBlocks() {
        assertNull(tracker.sent(packet(70, 1), 10, p -> false));
        assertNull(tracker.sent(packet(70, 255), 10, p -> true));
        tracker.sent(packet(70, 1), 10, p -> true);
        tracker.observe(49, p -> null, (p, b) -> fail("No block was placed"));
        assertFalse(tracker.isEmpty());
        tracker.observe(50, p -> null, (p, b) -> fail("No block was placed"));
        assertTrue(tracker.isEmpty());
    }

    @Test
    public void worldChangeClearsPendingPlacementIdentity() {
        tracker.sent(packet(70, 1), 10, p -> true);
        tracker.clear();
        tracker.observe(11, p -> log, (p, b) -> fail("A different world must not inherit pending placements"));
        assertTrue(tracker.isEmpty());
    }
}
