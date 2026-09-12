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

    @Test
    public void checkpointPersistenceDoesNotChangeLivePredictionFingerprint() {
        net.minecraft.entity.player.InventoryPlayer player = new net.minecraft.entity.player.InventoryPlayer(null);
        player.setInventorySlotContents(0, new ItemStack(ORE, 64));
        ContainerChest chest = new ContainerChest(player, new InventoryBasic("storage", false, 27));
        VanillaChestQuickMovePredictor predictor = new VanillaChestQuickMovePredictor(SNAPSHOTS);
        String fingerprint = null;
        for (long revision = 132; revision <= 133; revision++) {
            VanillaChestQuickMovePredictor.Prediction prediction = predictor.predict(
                chest,
                null,
                EMPTY,
                StorageItemFilter.acceptAll(),
                new io.github.kaseyawolf2.horizonwright.runtime.task.UnloadObservationRequest(
                    "unload",
                    revision,
                    7,
                    "empty",
                    "chest"));
            ContainerTransaction transaction = UnloadTransactionPlanner.createBatch(
                "persisted-transaction",
                7,
                io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlanner
                    .plan(EMPTY, prediction.getPlayerSlots(), StorageItemFilter.acceptAll()),
                prediction.getPlayerSlots(),
                prediction.getPredictions());
            String current = io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionFingerprint
                .fingerprint(transaction);
            if (fingerprint != null) assertEquals(fingerprint, current);
            fingerprint = current;
        }
    }

    @Test
    public void liveUnloadPlansOnlyNextTransferAndReobservesDrainedChest() {
        net.minecraft.entity.player.InventoryPlayer player = new net.minecraft.entity.player.InventoryPlayer(null);
        player.setInventorySlotContents(0, new ItemStack(ORE, 64));
        player.setInventorySlotContents(9, new ItemStack(DIRT, 32));
        InventoryBasic storage = new InventoryBasic("storage", false, 108);
        ContainerChest chest = new ContainerChest(player, storage);
        VanillaChestQuickMovePredictor predictor = new VanillaChestQuickMovePredictor(SNAPSHOTS);
        VanillaChestQuickMovePredictor.Prediction first = predictor
            .predict(chest, null, EMPTY, StorageItemFilter.acceptAll(), "first");
        assertEquals(
            2,
            first.getPredictions()
                .size());
        assertEquals(
            1,
            first.nextExtractionAwareTransfer(108)
                .size());
        assertEquals(
            108,
            first.nextExtractionAwareTransfer(108)
                .get(0)
                .getClick()
                .getExtractionStorageSlots());
        ContainerTransaction transfer = UnloadTransactionPlanner.createBatch(
            "live-transfer",
            7,
            io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlanner
                .plan(EMPTY, first.getPlayerSlots(), StorageItemFilter.acceptAll()),
            first.getPlayerSlots(),
            first.nextExtractionAwareTransfer(108));
        io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick click = transfer
            .nextClick(SNAPSHOTS.capture(chest, null, 0), 7)
            .get();
        player.setInventorySlotContents(0, null); // Server deposited ore; pipe already extracted it.
        org.junit.Assert.assertTrue(transfer.confirm(click.getClickId(), true, SNAPSHOTS.capture(chest, null, 1), 7));
        assertEquals(
            io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState.COMPLETED,
            transfer.getState());
        VanillaChestQuickMovePredictor.Prediction next = predictor
            .predict(chest, null, EMPTY, StorageItemFilter.acceptAll(), "next");
        assertEquals(
            9,
            next.nextExtractionAwareTransfer(108)
                .get(0)
                .getPlayerSlot());
        assertNull(
            next.nextExtractionAwareTransfer(108)
                .get(0)
                .getClick()
                .getExpectedBefore()
                .getSlots()
                .get(0));
        player.setInventorySlotContents(9, null);
        assertEquals(
            0,
            predictor.predict(chest, null, EMPTY, StorageItemFilter.acceptAll(), "done")
                .nextExtractionAwareTransfer(108)
                .size());
    }

    private static final Item ORE = new Item();
    private static final Item DIRT = new Item();
    private static final MinecraftContainerSnapshotter SNAPSHOTS = new MinecraftContainerSnapshotter(item -> {
        if (item == ORE) return "horizonwright:test_ore";
        if (item == DIRT) return "horizonwright:test_dirt";
        return null;
    });
    private static final NamedLoadout EMPTY = new NamedLoadout("empty", "Empty", Collections.emptyList());

    @Test
    public void serverStyleClientInventoryIsNotTheWorldChestObject() {
        net.minecraft.tileentity.TileEntityChest worldTile = new net.minecraft.tileentity.TileEntityChest();
        InventoryBasic clientInventory = new InventoryBasic("container.chest", false, 27);
        ContainerChest window = new ContainerChest(
            new net.minecraft.entity.player.InventoryPlayer(null),
            clientInventory);
        assertSame(clientInventory, SupportedChestLayout.inventory(window));
        org.junit.Assert.assertNotSame(worldTile, SupportedChestLayout.inventory(window));
        assertEquals(
            worldTile.getSizeInventory(),
            SupportedChestLayout.inventory(window)
                .getSizeInventory());
    }

    @Test
    public void rejectsReorderedPlayerSlotsBeforePredictingTransfers() {
        net.minecraft.entity.player.InventoryPlayer player = new net.minecraft.entity.player.InventoryPlayer(null);
        ContainerChest chest = new ContainerChest(player, new InventoryBasic("storage", false, 27));
        Collections.swap(chest.inventorySlots, 27, 28);
        org.junit.Assert.assertThrows(IllegalStateException.class, () -> SupportedChestLayout.inventory(chest));
    }

    @Test
    public void doesNotInferSupportForSubclassOrMissingContainer() {
        org.junit.Assert.assertFalse(SupportedChestLayout.supports(null));
        ContainerChest subclass = new ContainerChest(
            new net.minecraft.entity.player.InventoryPlayer(null),
            new InventoryBasic("storage", false, 27)) {};
        org.junit.Assert.assertFalse(SupportedChestLayout.supports(subclass));
    }

    @Test
    public void predictsExactVanillaMergeOrderAndPlayerSlotMapping() {
        net.minecraft.entity.player.InventoryPlayer player = new net.minecraft.entity.player.InventoryPlayer(null);
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
        net.minecraft.entity.player.InventoryPlayer player = new net.minecraft.entity.player.InventoryPlayer(null);
        InventoryBasic storage = new InventoryBasic("storage", false, 9);
        for (int slot = 0; slot < storage.getSizeInventory(); slot++) {
            storage.setInventorySlotContents(slot, new ItemStack(DIRT, 64));
        }
        player.setInventorySlotContents(0, new ItemStack(ORE, 1));
        ContainerChest chest = new ContainerChest(player, storage);

        assertEquals(
            0,
            new VanillaChestQuickMovePredictor(SNAPSHOTS)
                .predict(chest, null, EMPTY, StorageItemFilter.acceptAll(), "full")
                .getPredictions()
                .size());
        assertSame(
            ORE,
            player.getStackInSlot(0)
                .getItem());
        assertEquals(1, player.getStackInSlot(0).stackSize);
    }

    @Test
    public void rejectsNonEmptyCursorBeforeAnyPrediction() {
        net.minecraft.entity.player.InventoryPlayer player = new net.minecraft.entity.player.InventoryPlayer(null);
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

    @Test
    public void oneFreeSlotProducesVerifiedBatchInsteadOfDiscardingAllTransfers() {
        net.minecraft.entity.player.InventoryPlayer player = new net.minecraft.entity.player.InventoryPlayer(null);
        InventoryBasic storage = new InventoryBasic("storage", false, 9);
        for (int slot = 0; slot < 8; slot++) storage.setInventorySlotContents(slot, new ItemStack(DIRT, 64));
        player.setInventorySlotContents(0, new ItemStack(ORE, 64));
        player.setInventorySlotContents(1, new ItemStack(ORE, 64));
        VanillaChestQuickMovePredictor.Prediction predicted = new VanillaChestQuickMovePredictor(SNAPSHOTS)
            .predict(new ContainerChest(player, storage), null, EMPTY, StorageItemFilter.acceptAll(), "batch");
        assertEquals(
            1,
            predicted.getPredictions()
                .size());
        io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick click = predicted.getPredictions()
            .get(0)
            .getClick();
        ContainerTransaction transaction = UnloadTransactionPlanner.createBatch(
            "batch",
            41,
            io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlanner.plan(EMPTY, predicted.getPlayerSlots()),
            predicted.getPlayerSlots(),
            predicted.getPredictions());
        org.junit.Assert.assertTrue(
            transaction.nextClick(click.getExpectedBefore(), 41)
                .isPresent());
        org.junit.Assert.assertTrue(transaction.confirm(click.getClickId(), true, click.getExpectedAfter(), 41));
        assertEquals(
            io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState.COMPLETED,
            transaction.getState());
        assertNull(storage.getStackInSlot(8)); // Prediction never mutates live inventory.
        assertEquals(64, player.getStackInSlot(1).stackSize);
    }

    @Test
    public void skipsUnfittableFirstStackAndStillUsesLaterCompatibleCapacity() {
        net.minecraft.entity.player.InventoryPlayer player = new net.minecraft.entity.player.InventoryPlayer(null);
        InventoryBasic storage = new InventoryBasic("storage", false, 9);
        for (int slot = 0; slot < 9; slot++)
            storage.setInventorySlotContents(slot, new ItemStack(DIRT, slot == 0 ? 63 : 64));
        player.setInventorySlotContents(0, new ItemStack(ORE, 64));
        player.setInventorySlotContents(1, new ItemStack(DIRT, 9));
        VanillaChestQuickMovePredictor.Prediction predicted = new VanillaChestQuickMovePredictor(SNAPSHOTS)
            .predict(new ContainerChest(player, storage), null, EMPTY, StorageItemFilter.acceptAll(), "merge");
        assertEquals(
            1,
            predicted.getPredictions()
                .size());
        assertEquals(
            1,
            predicted.getPredictions()
                .get(0)
                .getPlayerSlot());
        assertEquals(
            0,
            predicted.getPredictions()
                .get(0)
                .getClick()
                .getExpectedBefore()
                .getRevision());
        assertEquals(
            64,
            predicted.getPredictions()
                .get(0)
                .getClick()
                .getExpectedAfter()
                .getSlots()
                .get(0)
                .getCount());
    }
}
