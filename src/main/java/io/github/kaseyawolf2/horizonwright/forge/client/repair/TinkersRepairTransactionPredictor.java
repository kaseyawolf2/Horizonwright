package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

/** Predicts the exact two-click take-output/return-tool operation for the pinned Tinkers layouts. */
final class TinkersRepairTransactionPredictor {

    private final TinkersRepairContainerAdapter adapter;
    private final MinecraftContainerSnapshotter snapshots;

    TinkersRepairTransactionPredictor(TinkersRepairContainerAdapter adapter, MinecraftContainerSnapshotter snapshots) {
        if (adapter == null || snapshots == null) {
            throw new IllegalArgumentException("adapter and snapshots are required");
        }
        this.adapter = adapter;
        this.snapshots = snapshots;
    }

    Prediction predict(Container container, InventoryPlayer playerInventory, int reservedInventorySlot,
        NamedLoadout loadout, String transactionId, long actionEpoch) {
        if (container == null || playerInventory == null || loadout == null) {
            throw new IllegalArgumentException("container, player inventory, and loadout are required");
        }
        TinkersRepairContainerInspection inspection = adapter
            .inspect(container, playerInventory, reservedInventorySlot);
        if (inspection.getStatus() != TinkersRepairContainerInspection.Status.RECOGNIZED) {
            throw new IllegalStateException(inspection.getDiagnostic());
        }
        TinkersRepairContainerEvidence evidence = inspection.getEvidence()
            .get();
        return predictRecognized(
            container,
            playerInventory,
            reservedInventorySlot,
            loadout,
            transactionId,
            actionEpoch,
            evidence);
    }

    Prediction predictRecognized(Container container, InventoryPlayer playerInventory, int reservedInventorySlot,
        NamedLoadout loadout, String transactionId, long actionEpoch, TinkersRepairContainerEvidence evidence) {
        if (container == null || playerInventory == null
            || loadout == null
            || evidence == null
            || evidence.getInputTool()
                .getReservedInventorySlot() != reservedInventorySlot) {
            throw new IllegalArgumentException("recognized repair evidence does not match the prediction request");
        }
        ContainerSnapshot initial = snapshots.capture(container, playerInventory.getItemStack(), 0L);
        if (initial.getCursor() != null) {
            throw new IllegalStateException("repair execution requires an empty cursor");
        }
        if (initial.getSlots()
            .get(evidence.getReservedContainerSlot()) != null) {
            throw new IllegalStateException(
                "the reserved player inventory slot must be empty while its tool is in the station");
        }
        // Tool identity is authenticated by the pinned Tinkers decoder and the repair verifier.
        // Do not restrict repair to the single historical loadout tool: excavation can legitimately
        // choose a shovel, axe, mattock, or another compatible tool from any inventory slot.
        if (evidence.getPredictedOutput() == null || evidence.getPredictedMaterialConsumed() == 0) {
            return Prediction.noOperation(evidence);
        }

        List<ItemStack> materials = materialStacks(container, evidence.getStationSlotCount());
        List<Integer> removals = TinkersRepairContainerAdapter
            .predictedMaterialRemovals(slot(container, 0).getStack(), materials);
        List<Integer> approvedMaterialSlots = new ArrayList<>();
        for (int index = 0; index < materials.size(); index++) {
            if (removals.get(index) == 0) continue;
            int containerSlot = index + 2;
            requireReservation(
                loadout,
                LoadoutRole.REPAIR_MATERIAL,
                initial.getSlots()
                    .get(containerSlot),
                "repair material slot " + containerSlot);
            approvedMaterialSlots.add(containerSlot);
        }

        ItemStack output = TinkersRepairContainerAdapter.finalizedOutput(slot(container, 0).getStack(), materials);
        ItemFingerprint outputItem = snapshots.fingerprint(output);
        List<ItemFingerprint> afterTakeSlots = new ArrayList<>(initial.getSlots());
        afterTakeSlots.set(0, null);
        afterTakeSlots.set(1, null);
        for (int index = 0; index < removals.size(); index++) {
            int amount = removals.get(index);
            if (amount == 0) continue;
            int containerSlot = index + 2;
            afterTakeSlots.set(containerSlot, reduced(afterTakeSlots.get(containerSlot), amount));
        }
        ContainerSnapshot afterTake = snapshot(initial, 1L, afterTakeSlots, outputItem);
        List<ItemFingerprint> afterReturnSlots = new ArrayList<>(afterTakeSlots);
        afterReturnSlots.set(evidence.getReservedContainerSlot(), outputItem);
        ContainerSnapshot afterReturn = snapshot(initial, 2L, afterReturnSlots, null);
        List<VerifiedContainerClick> clicks = new ArrayList<>();
        clicks.add(new VerifiedContainerClick(transactionId + "-take-output", 0, 0, 0, initial, afterTake));
        clicks.add(
            new VerifiedContainerClick(
                transactionId + "-return-tool",
                evidence.getReservedContainerSlot(),
                0,
                0,
                afterTake,
                afterReturn));
        return new Prediction(
            evidence,
            approvedMaterialSlots,
            new ContainerTransaction(transactionId, actionEpoch, clicks));
    }

    private static List<ItemStack> materialStacks(Container container, int stationSlotCount) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 2; slot < stationSlotCount; slot++) {
            ItemStack stack = slot(container, slot).getStack();
            result.add(stack == null ? null : stack.copy());
        }
        return result;
    }

    private static void requireReservation(NamedLoadout loadout, LoadoutRole role, ItemFingerprint item,
        String description) {
        for (LoadoutReservation reservation : loadout.getReservations()) {
            if (reservation.getRole() == role && reservation.matches(item)) return;
        }
        throw new IllegalStateException(description + " is not approved by loadout '" + loadout.getId() + "'");
    }

    private static ItemFingerprint reduced(ItemFingerprint item, int amount) {
        if (item == null || amount <= 0 || amount > item.getCount()) {
            throw new IllegalStateException("predicted material reduction exceeds the observed stack");
        }
        int remaining = item.getCount() - amount;
        return remaining == 0 ? null
            : new ItemFingerprint(item.getItemId(), item.getMetadata(), item.getDataHash(), remaining);
    }

    private static ContainerSnapshot snapshot(ContainerSnapshot identity, long revision, List<ItemFingerprint> slots,
        ItemFingerprint cursor) {
        return new ContainerSnapshot(
            identity.getWindowId(),
            identity.getContainerType(),
            identity.getSlotLayout(),
            revision,
            slots,
            cursor);
    }

    private static Slot slot(Container container, int index) {
        Object value = container.inventorySlots.get(index);
        if (!(value instanceof Slot)) {
            throw new IllegalStateException("container slot " + index + " is not a Minecraft Slot");
        }
        return (Slot) value;
    }

    static final class Prediction {

        private final TinkersRepairContainerEvidence evidence;
        private final List<Integer> approvedMaterialSlots;
        private final ContainerTransaction transaction;

        private Prediction(TinkersRepairContainerEvidence evidence, List<Integer> approvedMaterialSlots,
            ContainerTransaction transaction) {
            this.evidence = evidence;
            this.approvedMaterialSlots = Collections.unmodifiableList(new ArrayList<>(approvedMaterialSlots));
            this.transaction = transaction;
        }

        private static Prediction noOperation(TinkersRepairContainerEvidence evidence) {
            return new Prediction(evidence, Collections.<Integer>emptyList(), null);
        }

        TinkersRepairContainerEvidence getEvidence() {
            return evidence;
        }

        List<Integer> getApprovedMaterialSlots() {
            return approvedMaterialSlots;
        }

        ContainerTransaction getTransaction() {
            return transaction;
        }
    }
}
