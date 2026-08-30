package io.github.kaseyawolf2.horizonwright.testfixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.testfixtures.FakeActionExecutor.ActionResult;
import io.github.kaseyawolf2.horizonwright.testfixtures.FakeActionExecutor.ActionState;
import io.github.kaseyawolf2.horizonwright.testfixtures.FakeInventory.ItemStack;
import io.github.kaseyawolf2.horizonwright.testfixtures.FakeWorldSnapshot.BlockPos;
import io.github.kaseyawolf2.horizonwright.testfixtures.FakeWorldSnapshot.BlockState;

public class DeterministicFakesTest {

    @Test
    public void clockSeparatesWallTimeFromConnectedTime() {
        FakeClock clock = new FakeClock();
        clock.advanceTicks(20L);
        assertEquals(20L, clock.wallTicks());
        assertEquals(0L, clock.connectedTicks());

        clock.connect();
        clock.advanceTicks(40L);
        clock.disconnect();
        clock.advanceTicks(10L);

        assertEquals(70L, clock.wallTicks());
        assertEquals(40L, clock.connectedTicks());
        assertFalse(clock.isConnected());
    }

    @Test
    public void worldSnapshotDefensivelyCopiesItsBlockMap() {
        BlockPos position = new BlockPos(-17, 64, 33);
        Map<BlockPos, BlockState> source = new LinkedHashMap<>();
        source.put(position, new BlockState("minecraft:stone", 0));
        FakeWorldSnapshot snapshot = new FakeWorldSnapshot(-1, 9L, source);

        source.put(position, new BlockState("minecraft:air", 0));

        assertEquals(-1, snapshot.getDimensionId());
        assertEquals(9L, snapshot.getRevision());
        assertEquals(
            "minecraft:stone",
            snapshot.blockAt(position)
                .get()
                .getRegistryName());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void worldSnapshotExposesNoMutableBlockMap() {
        FakeWorldSnapshot snapshot = new FakeWorldSnapshot(0, 1L, new LinkedHashMap<BlockPos, BlockState>());
        snapshot.getBlocks()
            .put(new BlockPos(0, 0, 0), new BlockState("minecraft:stone", 0));
    }

    @Test
    public void inventorySnapshotsRemainStableAcrossMovesAndRestore() {
        FakeInventory inventory = new FakeInventory(3);
        inventory.setSlot(0, new ItemStack("minecraft:cobblestone", 0, 6, "plain-cobble"));
        FakeInventory.Snapshot before = inventory.snapshot();

        inventory.move(0, 1, 2);
        FakeInventory.Snapshot after = inventory.snapshot();

        assertEquals(
            6,
            before.getSlot(0)
                .get()
                .getCount());
        assertFalse(
            before.getSlot(1)
                .isPresent());
        assertEquals(
            4,
            after.getSlot(0)
                .get()
                .getCount());
        assertEquals(
            2,
            after.getSlot(1)
                .get()
                .getCount());

        inventory.restore(before);
        assertEquals(before, inventory.snapshot());
    }

    @Test
    public void packetGateIsEvaluatedAtFlushRatherThanQueueTime() {
        FakePacketBoundary boundary = new FakePacketBoundary();
        boundary.queueOutbound("START_DIGGING", 3L, "10,64,10");
        final boolean[] deathLatched = { true };

        boundary.flush(packet -> !deathLatched[0]);

        assertTrue(
            boundary.queuedPackets()
                .isEmpty());
        assertTrue(
            boundary.writtenPackets()
                .isEmpty());
        assertEquals(
            1,
            boundary.droppedPackets()
                .size());
    }

    @Test
    public void actionExecutorCancelsWorkWhoseLeaseWasRevokedWhileQueued() {
        FakeClock clock = new FakeClock();
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("fixture", EnumSet.of(ActionCapability.DIG))
            .get();
        FakeActionExecutor executor = new FakeActionExecutor(clock);
        executor.submit("break-one-block", ActionCapability.DIG, lease);

        broker.revokeAll();
        clock.advanceTicks(1L);
        List<ActionResult> results = executor.drain();

        assertEquals(1, results.size());
        assertEquals(
            ActionState.CANCELLED_STALE_LEASE,
            results.get(0)
                .getState());
        assertEquals(
            lease.getEpoch(),
            results.get(0)
                .getActionEpoch());
        assertEquals(
            0L,
            results.get(0)
                .getSubmittedAtTick());
        assertEquals(
            1L,
            results.get(0)
                .getObservedAtTick());
    }

    @Test
    public void actionExecutorRunsWorkWithAStillValidCapabilityLease() {
        FakeClock clock = new FakeClock();
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("fixture", EnumSet.of(ActionCapability.USE))
            .get();
        FakeActionExecutor executor = new FakeActionExecutor(clock);
        executor.submit("use-bed", ActionCapability.USE, lease);

        clock.advanceTicks(2L);
        ActionResult result = executor.drain()
            .get(0);

        assertEquals(ActionState.EXECUTED, result.getState());
        assertEquals(ActionCapability.USE, result.getCapability());
        assertEquals("use-bed", result.getActionId());
        assertEquals(
            1,
            executor.history()
                .size());
    }
}
