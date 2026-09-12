package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

public class BagQuickMoveTest {

    private final List<ItemFingerprint> slots = new ArrayList<>(Collections.nCopies(40, null));
    private final int[] limits = new int[40];
    private final boolean[] accepts = new boolean[40];

    public BagQuickMoveTest() {
        Arrays.fill(limits, 64);
        Arrays.fill(accepts, true);
    }

    @Test
    public void loadsWholeStackWithOneShiftAndNoCursorMutation() {
        slots.set(0, item("dirt", 64));
        VerifiedContainerClick click = predict(0, 64);
        assertEquals(1, click.getClickMode());
        assertNull(
            click.getExpectedAfter()
                .getSlots()
                .get(0));
        assertEquals(
            item("dirt", 64),
            click.getExpectedAfter()
                .getSlots()
                .get(36));
        assertNull(
            click.getExpectedAfter()
                .getCursor());
        assertEquals(item("dirt", 64), slots.get(0));
        assertNull(slots.get(36));
    }

    @Test
    public void loadingMatchesForestryFirstMergeThenFirstAvailableSlot() {
        slots.set(0, item("dirt", 64));
        slots.set(37, item("dirt", 60));
        slots.set(38, item("dirt", 50));
        VerifiedContainerClick click = predict(0, 64);
        assertEquals(
            item("dirt", 64),
            click.getExpectedAfter()
                .getSlots()
                .get(37));
        assertEquals(
            item("dirt", 60),
            click.getExpectedAfter()
                .getSlots()
                .get(36));
        assertEquals(
            item("dirt", 50),
            click.getExpectedAfter()
                .getSlots()
                .get(38));
    }

    @Test
    public void unloadingMergesHotbarThenMainBeforeUsingEmptyHotbar() {
        slots.set(36, item("sand", 64));
        slots.set(27, item("sand", 60));
        slots.set(0, item("sand", 50));
        VerifiedContainerClick click = predict(36, 64);
        assertEquals(
            item("sand", 64),
            click.getExpectedAfter()
                .getSlots()
                .get(27));
        assertEquals(
            item("sand", 64),
            click.getExpectedAfter()
                .getSlots()
                .get(0));
        assertEquals(
            item("sand", 46),
            click.getExpectedAfter()
                .getSlots()
                .get(28));
        assertNull(
            click.getExpectedAfter()
                .getSlots()
                .get(36));
    }

    @Test
    public void reservationOrInsufficientCapacityFallsBackBeforeAnyClick() {
        slots.set(0, item("dirt", 64));
        assertNull(predict(0, 48));
        for (int i = 36; i < 40; i++) slots.set(i, item("dirt", 63));
        assertNull(predict(0, 64));
        assertEquals(item("dirt", 64), slots.get(0));
    }

    @Test
    public void respectsFiltersSlotLimitsAndItemIdentity() {
        slots.set(0, item("dirt", 64));
        accepts[36] = false;
        slots.set(37, item("sand", 9));
        limits[38] = 16;
        VerifiedContainerClick click = predict(0, 64);
        assertNull(
            click.getExpectedAfter()
                .getSlots()
                .get(36));
        assertEquals(
            item("sand", 9),
            click.getExpectedAfter()
                .getSlots()
                .get(37));
        assertEquals(
            item("dirt", 16),
            click.getExpectedAfter()
                .getSlots()
                .get(38));
        assertEquals(
            item("dirt", 48),
            click.getExpectedAfter()
                .getSlots()
                .get(39));
    }

    @Test
    public void metadataAndNbtDoNotMerge() {
        slots.set(0, item("dirt", 64));
        slots.set(36, new ItemFingerprint("dirt", 1, "data", 9));
        slots.set(37, new ItemFingerprint("dirt", 0, "different", 9));
        VerifiedContainerClick click = predict(0, 64);
        assertEquals(
            slots.get(36),
            click.getExpectedAfter()
                .getSlots()
                .get(36));
        assertEquals(
            slots.get(37),
            click.getExpectedAfter()
                .getSlots()
                .get(37));
        assertEquals(
            item("dirt", 64),
            click.getExpectedAfter()
                .getSlots()
                .get(38));
    }

    private VerifiedContainerClick predict(int source, int approved) {
        return BagQuickMove.forestry(
            "test",
            new ContainerSnapshot(1, "forestry", "layout", 0, slots, null),
            source,
            approved,
            limits,
            accepts);
    }

    private static ItemFingerprint item(String id, int count) {
        return new ItemFingerprint(id, 0, "data", count);
    }
}
