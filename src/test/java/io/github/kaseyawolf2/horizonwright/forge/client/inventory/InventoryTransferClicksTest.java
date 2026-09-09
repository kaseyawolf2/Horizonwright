package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

public class InventoryTransferClicksTest {

    @Test
    public void carrierStagesIntoEmptyHotbarAndReturnsUsingNormalClicks() {
        ContainerSnapshot before = snapshot(item("Forestry:minerBag", "uid-and-contents", 1), null, null);
        List<VerifiedContainerClick> outward = InventoryTransferClicks.swap("stage", before, 0, 1, 64, 1);
        assertEquals(2, outward.size());
        assertConservedChain(before, outward);
        assertNull(
            last(outward).getSlots()
                .get(0));
        assertEquals(
            before.getSlots()
                .get(0),
            last(outward).getSlots()
                .get(1));
        List<VerifiedContainerClick> home = InventoryTransferClicks.swap("restore", last(outward), 0, 1, 1, 64);
        assertEquals(2, home.size());
        assertConservedChain(last(outward), home);
        assertEquals(before.getSlots(), last(home).getSlots());
    }

    @Test
    public void carrierSwapPreservesAnOccupiedHotbarStackAndRestoresIt() {
        ContainerSnapshot before = snapshot(item("Forestry:minerBag", "uid", 1), item("ore", "cargo", 64), null);
        List<VerifiedContainerClick> outward = InventoryTransferClicks.swap("stage", before, 0, 1, 64, 1);
        assertEquals(3, outward.size());
        assertConservedChain(before, outward);
        assertEquals(
            before.getSlots()
                .get(1),
            last(outward).getSlots()
                .get(0));
        List<VerifiedContainerClick> home = InventoryTransferClicks.swap("restore", last(outward), 0, 1, 1, 64);
        assertConservedChain(last(outward), home);
        assertEquals(before.getSlots(), last(home).getSlots());
    }

    @Test
    public void carrierSwapPreservesDistinctBagIdentities() {
        ContainerSnapshot before = snapshot(item("bag", "first-uid", 1), item("bag", "second-uid", 1), null);
        List<VerifiedContainerClick> clicks = InventoryTransferClicks.swap("stage", before, 0, 1, 1, 1);
        assertConservedChain(before, clicks);
        assertEquals(
            before.getSlots()
                .get(1),
            last(clicks).getSlots()
                .get(0));
        assertEquals(
            before.getSlots()
                .get(0),
            last(clicks).getSlots()
                .get(1));
    }

    @Test
    public void identicalUnstackableCarriersLeaveAnEmptyCursor() {
        ContainerSnapshot before = snapshot(item("bag", "same", 1), item("bag", "same", 1), null);
        List<VerifiedContainerClick> clicks = InventoryTransferClicks.swap("stage", before, 0, 1, 1, 1);
        assertConservedChain(before, clicks);
        assertEquals(before.getSlots(), last(clicks).getSlots());
    }

    @Test(expected = IllegalArgumentException.class)
    public void swapRejectsPartialPlacementIntoAnEmptySlot() {
        InventoryTransferClicks.swap("invalid", snapshot(item("item", "data", 2), null, null), 0, 1, 64, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void swapRejectsAnOccupiedCursor() {
        InventoryTransferClicks
            .swap("invalid", snapshot(item("bag", "data", 1), null, item("ore", "data", 1)), 0, 1, 64, 1);
    }

    @Test
    public void wholeStackMovesToEmptySlotWithEmptyFinalCursor() {
        ContainerSnapshot before = snapshot(item("ore", "a", 32), null, null);
        List<VerifiedContainerClick> clicks = InventoryTransferClicks.move("move", before, 0, 1, 64);

        assertEquals(2, clicks.size());
        assertEquals(
            0,
            clicks.get(0)
                .getSlot());
        assertEquals(
            1,
            clicks.get(1)
                .getSlot());
        assertNull(
            last(clicks).getSlots()
                .get(0));
        assertEquals(
            item("ore", "a", 32),
            last(clicks).getSlots()
                .get(1));
        assertConservedChain(before, clicks);
    }

    @Test
    public void fullIdenticalMergeLeavesNoSourceOrCursorRemainder() {
        ContainerSnapshot before = snapshot(item("ore", "a", 24), item("ore", "a", 40), null);
        List<VerifiedContainerClick> clicks = InventoryTransferClicks.move("merge", before, 0, 1, 64);

        assertEquals(2, clicks.size());
        assertNull(
            last(clicks).getSlots()
                .get(0));
        assertEquals(
            item("ore", "a", 64),
            last(clicks).getSlots()
                .get(1));
        assertConservedChain(before, clicks);
    }

    @Test
    public void partialMergeReturnsRemainderToOriginalSlot() {
        ContainerSnapshot before = snapshot(item("ore", "a", 32), item("ore", "a", 60), null);
        List<VerifiedContainerClick> clicks = InventoryTransferClicks.move("merge", before, 0, 1, 64);

        assertEquals(3, clicks.size());
        assertEquals(
            item("ore", "a", 28),
            clicks.get(1)
                .getExpectedAfter()
                .getCursor());
        assertEquals(
            0,
            clicks.get(2)
                .getSlot());
        assertEquals(
            item("ore", "a", 28),
            last(clicks).getSlots()
                .get(0));
        assertEquals(
            item("ore", "a", 64),
            last(clicks).getSlots()
                .get(1));
        assertConservedChain(before, clicks);
    }

    @Test
    public void requestedCountMovesExactlyThatManyIntoExistingStack() {
        ContainerSnapshot before = snapshot(item("ore", "a", 32), item("ore", "a", 16), null);
        List<VerifiedContainerClick> clicks = InventoryTransferClicks.move("reserve", before, 0, 1, 64, 3);

        assertEquals(5, clicks.size());
        for (int index = 1; index <= 3; index++) {
            assertEquals(
                1,
                clicks.get(index)
                    .getMouseButton());
            assertEquals(
                1,
                clicks.get(index)
                    .getSlot());
        }
        assertEquals(
            item("ore", "a", 29),
            last(clicks).getSlots()
                .get(0));
        assertEquals(
            item("ore", "a", 19),
            last(clicks).getSlots()
                .get(1));
        assertConservedChain(before, clicks);
    }

    @Test
    public void everyBoundedCountPreservesTotalsAtEveryClick() {
        for (int sourceCount = 1; sourceCount <= 64; sourceCount++) {
            for (int requested = 1; requested <= 64; requested++) {
                for (int targetCount : new int[] { 0, 1, 32, 63 }) {
                    ContainerSnapshot before = snapshot(
                        item("ore", "a", sourceCount),
                        targetCount == 0 ? null : item("ore", "a", targetCount),
                        null);
                    List<VerifiedContainerClick> clicks = InventoryTransferClicks
                        .move("bounded", before, 0, 1, 64, requested);
                    int transferred = Math.min(Math.min(sourceCount, requested), 64 - targetCount);
                    assertEquals(
                        sourceCount - transferred,
                        count(
                            last(clicks).getSlots()
                                .get(0)));
                    assertEquals(
                        targetCount + transferred,
                        count(
                            last(clicks).getSlots()
                                .get(1)));
                    assertConservedChain(before, clicks);
                }
            }
        }
    }

    @Test
    public void restrictiveSlotCapacityLimitsTransferEvenWhenRequestIsLarger() {
        ContainerSnapshot before = snapshot(item("ore", "a", 64), null, null);
        List<VerifiedContainerClick> clicks = InventoryTransferClicks.move("limit", before, 0, 1, 16, 32);

        assertEquals(
            item("ore", "a", 48),
            last(clicks).getSlots()
                .get(0));
        assertEquals(
            item("ore", "a", 16),
            last(clicks).getSlots()
                .get(1));
        assertConservedChain(before, clicks);
    }

    @Test(expected = IllegalArgumentException.class)
    public void occupiedCursorCannotBeginTransfer() {
        InventoryTransferClicks.move("cursor", snapshot(item("ore", "a", 1), null, item("other", "b", 1)), 0, 1, 64);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sameItemWithDifferentNbtCannotMerge() {
        InventoryTransferClicks.move("nbt", snapshot(item("ore", "a", 1), item("ore", "b", 1), null), 0, 1, 64);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sameItemWithDifferentMetadataCannotMerge() {
        ContainerSnapshot before = snapshot(item("ore", "a", 1), new ItemFingerprint("ore", 1, "a", 1), null);
        InventoryTransferClicks.move("metadata", before, 0, 1, 64);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fullDestinationCannotBeginTransfer() {
        InventoryTransferClicks.move("full", snapshot(item("ore", "a", 1), item("ore", "a", 64), null), 0, 1, 64);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroCapacityCannotBeginTransfer() {
        InventoryTransferClicks.move("zero", snapshot(item("ore", "a", 1), null, null), 0, 1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroRequestedCountCannotBeginTransfer() {
        InventoryTransferClicks.move("zero", snapshot(item("ore", "a", 1), null, null), 0, 1, 64, 0);
    }

    private static ContainerSnapshot snapshot(ItemFingerprint source, ItemFingerprint destination,
        ItemFingerprint cursor) {
        return new ContainerSnapshot(
            12,
            "test:bag",
            "bag-slots",
            10,
            Arrays.asList(source, destination, item("unrelated", "protected", 7)),
            cursor);
    }

    private static ItemFingerprint item(String id, String nbt, int count) {
        return new ItemFingerprint(id, 0, nbt, count);
    }

    private static ContainerSnapshot last(List<VerifiedContainerClick> clicks) {
        return clicks.get(clicks.size() - 1)
            .getExpectedAfter();
    }

    private static int count(ItemFingerprint item) {
        return item == null ? 0 : item.getCount();
    }

    private static void assertConservedChain(ContainerSnapshot before, List<VerifiedContainerClick> clicks) {
        Map<String, Integer> totals = totals(before);
        ContainerSnapshot previous = before;
        for (VerifiedContainerClick click : clicks) {
            assertEquals(previous, click.getExpectedBefore());
            ContainerSnapshot after = click.getExpectedAfter();
            assertEquals(totals, totals(after));
            assertTrue(before.sameIdentityAndLayout(after));
            assertEquals(previous.getRevision() + 1, after.getRevision());
            assertEquals(
                before.getSlots()
                    .get(2),
                after.getSlots()
                    .get(2));
            assertEquals(0, click.getClickMode());
            previous = after;
        }
        assertNull(previous.getCursor());
    }

    private static Map<String, Integer> totals(ContainerSnapshot snapshot) {
        Map<String, Integer> totals = new HashMap<>();
        for (ItemFingerprint item : snapshot.getSlots()) add(totals, item);
        add(totals, snapshot.getCursor());
        return totals;
    }

    private static void add(Map<String, Integer> totals, ItemFingerprint item) {
        if (item == null) return;
        String identity = item.getItemId() + ":" + item.getMetadata() + "#" + item.getDataHash();
        Integer previous = totals.get(identity);
        totals.put(identity, (previous == null ? 0 : previous) + item.getCount());
    }
}
