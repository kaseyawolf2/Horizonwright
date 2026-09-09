package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

public class TreeToolInventorySwapTest {

    private static final ItemFingerprint AXE = new ItemFingerprint("tconstruct:lumberaxe", 0, "tool", 1);
    private static final ItemFingerprint FOOD = new ItemFingerprint("minecraft:bread", 0, "none", 8);

    @Test
    public void modeTwoUsesTheMainInventorySourceAndHotbarButtonAndRequiresConfirmation() {
        ContainerSnapshot before = inventory(AXE, FOOD);
        ContainerTransaction transaction = PlayerInventoryHotbarSwap.plan("tool", 7L, before, 22, 4);
        VerifiedContainerClick click = transaction.nextClick(before, 7L)
            .get();
        assertEquals(22, click.getSlot());
        assertEquals(4, click.getMouseButton());
        assertEquals(2, click.getClickMode());
        assertEquals(ContainerTransactionState.AWAITING_CONFIRMATION, transaction.getState());
        assertEquals(
            FOOD,
            click.getExpectedAfter()
                .getSlots()
                .get(22));
        assertEquals(
            AXE,
            click.getExpectedAfter()
                .getSlots()
                .get(40));
        assertTrue(transaction.confirm(click.getClickId(), true, click.getExpectedAfter(), 7L));
        assertEquals(ContainerTransactionState.COMPLETED, transaction.getState());
    }

    @Test
    public void rejectedOrChangedServerStateNeverConfirmsToolAvailability() {
        ContainerSnapshot before = inventory(AXE, FOOD);
        ContainerTransaction rejected = PlayerInventoryHotbarSwap.plan("rejected", 7L, before, 22, 4);
        VerifiedContainerClick click = rejected.nextClick(before, 7L)
            .get();
        assertFalse(rejected.confirm(click.getClickId(), false, click.getExpectedAfter(), 7L));
        assertEquals(ContainerTransactionState.ABORTED, rejected.getState());
        ContainerTransaction changed = PlayerInventoryHotbarSwap.plan("changed", 7L, before, 22, 4);
        assertFalse(
            changed.nextClick(inventory(AXE, null), 7L)
                .isPresent());
        assertEquals(ContainerTransactionState.ABORTED, changed.getState());
    }

    @Test
    public void refusesAnOpenBagCursorStackAndOutOfBoundsSlotMapping() {
        ContainerSnapshot before = inventory(AXE, FOOD);
        assertThrows(
            IllegalArgumentException.class,
            () -> PlayerInventoryHotbarSwap.plan(
                "bag",
                7L,
                new ContainerSnapshot(2, "mod.Backpack", "slots", 0L, before.getSlots(), null),
                22,
                4));
        assertThrows(
            IllegalArgumentException.class,
            () -> PlayerInventoryHotbarSwap.plan(
                "cursor",
                7L,
                new ContainerSnapshot(0, before.getContainerType(), "slots", 0L, before.getSlots(), FOOD),
                22,
                4));
        assertThrows(IllegalArgumentException.class, () -> PlayerInventoryHotbarSwap.plan("source", 7L, before, 8, 4));
        assertThrows(IllegalArgumentException.class, () -> PlayerInventoryHotbarSwap.plan("hotbar", 7L, before, 22, 9));
    }

    private static ContainerSnapshot inventory(ItemFingerprint source, ItemFingerprint hotbar) {
        List<ItemFingerprint> slots = new ArrayList<>(Collections.nCopies(45, null));
        slots.set(22, source);
        slots.set(40, hotbar);
        return new ContainerSnapshot(0, "net.minecraft.inventory.ContainerPlayer", "player-slots", 0L, slots, null);
    }
}
