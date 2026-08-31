package io.github.kaseyawolf2.horizonwright.forge.client.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Collections;

import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadTransactionPlanner;

public class VanillaChestQuickMovePredictorTest {

    private static final Item ORE = new Item();
    private static final Item DIRT = new Item();
    private static final MinecraftContainerSnapshotter SNAPSHOTS = new MinecraftContainerSnapshotter(item -> {
        if (item == ORE) return "horizonwright:test_ore";
        if (item == DIRT) return "horizonwright:test_dirt";
        return null;
    });
    private static final NamedLoadout EMPTY = new NamedLoadout("empty", "Empty", Collections.emptyList());

    @Test
    public void predictsExactVanillaMergeOrderAndPlayerSlotMapping() {
        InventoryBasic player = new InventoryBasic("player", false, 36);
        InventoryBasic storage = new InventoryBasic("storage", false, 27);
        storage.setInventorySlotContents(0, new ItemStack(ORE, 60));
        player.setInventorySlotContents(0, new ItemStack(ORE, 10));
        player.setInventorySlotContents(9, new ItemStack(DIRT, 3));
        ContainerChest chest = new ContainerChest(player, storage);
        chest.windowId = 12;

        VanillaChestQuickMovePredictor.Prediction prediction = new VanillaChestQuickMovePredictor(SNAPSHOTS)
            .predict(chest, null, EMPTY, StorageItemFilter.acceptAll(), "click");

        assertEquals(
            2,
            prediction.getPredictions()
                .size());
        assertEquals(
            0,
            prediction.getPredictions()
                .get(0)
                .getPlayerSlot());
        assertEquals(
            54,
            prediction.getPredictions()
                .get(0)
                .getClick()
                .getSlot());
        assertEquals(
            9,
            prediction.getPredictions()
                .get(1)
                .getPlayerSlot());
        assertEquals(
            27,
            prediction.getPredictions()
                .get(1)
                .getClick()
                .getSlot());
        assertEquals(
            prediction.getPredictions()
                .get(0)
                .getClick()
                .getExpectedAfter(),
            prediction.getPredictions()
                .get(1)
                .getClick()
                .getExpectedBefore());

        assertEquals(
            64,
            prediction.getPredictions()
                .get(0)
                .getClick()
                .getExpectedAfter()
                .getSlots()
                .get(0)
                .getCount());
        assertEquals(
            6,
            prediction.getPredictions()
                .get(0)
                .getClick()
                .getExpectedAfter()
                .getSlots()
                .get(1)
                .getCount());
        assertNull(
            prediction.getPredictions()
                .get(0)
                .getClick()
                .getExpectedAfter()
                .getSlots()
                .get(54));

        ContainerTransaction transaction = UnloadTransactionPlanner.create(
            "unload",
            41L,
            io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlanner
                .plan(EMPTY, prediction.getPlayerSlots(), StorageItemFilter.acceptAll()),
            prediction.getPlayerSlots(),
            prediction.getPredictions());
        assertEquals(
            2,
            transaction.getClicks()
                .size());
    }

    @Test
    public void refusesToPredictWhenExactChestCannotAcceptAWholeStackMove() {
        InventoryBasic player = new InventoryBasic("player", false, 36);
        InventoryBasic storage = new InventoryBasic("storage", false, 9);
        for (int slot = 0; slot < storage.getSizeInventory(); slot++) {
            storage.setInventorySlotContents(slot, new ItemStack(DIRT, 64));
        }
        player.setInventorySlotContents(0, new ItemStack(ORE, 1));
        ContainerChest chest = new ContainerChest(player, storage);

        try {
            new VanillaChestQuickMovePredictor(SNAPSHOTS)
                .predict(chest, null, EMPTY, StorageItemFilter.acceptAll(), "full");
            fail("a full chest cannot provide an exact reducing prediction");
        } catch (IllegalStateException expected) {
            assertEquals("vanilla chest has no capacity for approved player slot 0", expected.getMessage());
        }
        assertSame(
            ORE,
            player.getStackInSlot(0)
                .getItem());
        assertEquals(1, player.getStackInSlot(0).stackSize);
    }

    @Test
    public void rejectsNonEmptyCursorBeforeAnyPrediction() {
        InventoryBasic player = new InventoryBasic("player", false, 36);
        InventoryBasic storage = new InventoryBasic("storage", false, 27);
        ContainerChest chest = new ContainerChest(player, storage);
        try {
            new VanillaChestQuickMovePredictor(SNAPSHOTS)
                .predict(chest, new ItemStack(ORE), EMPTY, StorageItemFilter.acceptAll(), "cursor");
            fail("cursor-held items make shift-click prediction unsafe");
        } catch (IllegalStateException expected) {
            assertEquals("unloading requires an empty cursor", expected.getMessage());
        }
    }
}
