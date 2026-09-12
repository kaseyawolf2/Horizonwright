package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.core.inventory.GeneralizedInventoryPlanner;
import io.github.kaseyawolf2.horizonwright.core.inventory.InventorySlot;

/** Forestry 4.11.31 SlotUtil routing, including vanilla's repeated shift-click transfer. */
final class BagQuickMove {

    private BagQuickMove() {}

    static VerifiedContainerClick predict(String id, Container opened, ContainerSnapshot before, List<Slot> storage,
        List<InventorySlot> playerModel, List<InventorySlot> bagModel, GeneralizedInventoryPlanner.Plan plan) {
        if (!opened.getClass()
            .getName()
            .equals("forestry.storage.gui.ContainerBackpack")) return null;
        // Forestry hard-codes main inventory 0..26, hotbar 27..35, then bag slots.
        if (opened.inventorySlots.size() != 36 + storage.size() || playerModel.size() != 36) return null;
        for (int i = 0; i < 36; i++) {
            Slot slot = (Slot) opened.inventorySlots.get(i);
            if (!(slot.inventory instanceof net.minecraft.entity.player.InventoryPlayer) || slot.slotNumber != i
                || slot.getSlotIndex() != (i < 27 ? i + 9 : i - 27)) return null;
        }
        for (int i = 36; i < opened.inventorySlots.size(); i++) {
            if (!storage.contains(opened.inventorySlots.get(i))) return null;
        }
        GeneralizedInventoryPlanner.Move first = plan.getMoves()
            .get(0);
        int source = first.getSource()
            .getSlot();
        ItemStack stack = ((Slot) opened.inventorySlots.get(source)).getStack();
        if (stack == null || !stack.isStackable()) return null;
        for (ItemFingerprint item : before.getSlots()) {
            // Forestry treats wildcard metadata asymmetrically; use exact clicks for it.
            if (item != null && item.getMetadata() == 32767) return null;
        }
        int approved = 0;
        for (GeneralizedInventoryPlanner.Move move : plan.getMoves()) {
            if (move.getSource()
                .equals(first.getSource())
                && move.getDestination()
                    .getEndpointId()
                    .equals(
                        first.getDestination()
                            .getEndpointId()))
                approved += move.getCount();
        }
        int[] limits = new int[opened.inventorySlots.size()];
        boolean[] accepts = new boolean[limits.length];
        for (int i = 0; i < limits.length; i++) {
            Slot slot = (Slot) opened.inventorySlots.get(i);
            limits[i] = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
            accepts[i] = slot.isItemValid(stack);
            if (i >= 36 && OptionalInventoryReflection.instance(slot, "forestry.core.gui.slots.SlotForestry")) {
                accepts[i] &= Boolean.TRUE.equals(OptionalInventoryReflection.call(slot, "canShift"))
                    && !Boolean.TRUE.equals(OptionalInventoryReflection.call(slot, "isPhantom"));
            }
        }
        VerifiedContainerClick click = forestry(id, before, source, approved, limits, accepts);
        if (click == null) return null;
        // Native shift-click must not fill slots protected by the task/carrier policy.
        List<InventorySlot> allowed = source < 36 ? bagModel : playerModel;
        for (int i = 0; i < limits.length; i++) {
            if (i == source || java.util.Objects.equals(
                before.getSlots()
                    .get(i),
                click.getExpectedAfter()
                    .getSlots()
                    .get(i)))
                continue;
            final int index = i;
            InventorySlot target = allowed.stream()
                .filter(slot -> slot.getIndex() == index)
                .findFirst()
                .orElse(null);
            ItemFingerprint after = click.getExpectedAfter()
                .getSlots()
                .get(i);
            if (target == null || target.capacityFor(after) < after.getCount()) return null;
        }
        return click;
    }

    static VerifiedContainerClick forestry(String id, ContainerSnapshot before, int source, int approved, int[] limits,
        boolean[] accepts) {
        if (before.getCursor() != null || before.getSlots()
            .size() <= 36
            || source < 0
            || source >= before.getSlots()
                .size()
            || limits.length != before.getSlots()
                .size()
            || accepts.length != limits.length) return null;
        ItemFingerprint item = before.getSlots()
            .get(source);
        if (item == null || approved < item.getCount()) return null;
        List<ItemFingerprint> slots = new ArrayList<>(before.getSlots());
        int left = item.getCount();
        if (source < 36) {
            // Each native invocation tries one occupied slot, then one eligible slot;
            // vanilla repeats while the source still contains the same item.
            int previous;
            do {
                previous = left;
                for (int pass = 0; pass < 2 && left > 0; pass++) {
                    for (int i = 36; i < slots.size(); i++) {
                        if (!accepts[i] || pass == 0 && slots.get(i) == null) continue;
                        int remaining = merge(slots, i, item, left, limits[i]);
                        if (remaining < left) {
                            left = remaining;
                            break;
                        }
                    }
                }
            } while (left > 0 && left < previous);
        } else {
            // Merge hotbar then main, then fill empty hotbar then main. SlotUtil's
            // player route does not consult isItemValid; policy is checked afterward.
            for (int pass = 0; pass < 2; pass++) {
                for (int offset = 0; offset < 36 && left > 0; offset++) {
                    int i = offset < 9 ? offset + 27 : offset - 9;
                    if ((slots.get(i) == null) != (pass == 1)) continue;
                    left = merge(slots, i, item, left, limits[i]);
                }
            }
        }
        // Full-source transfers avoid native fallback shuffling between main/hotbar
        // when a bag fills halfway through vanilla's recursive shift-click.
        if (left != 0) return null;
        slots.set(source, null);
        ContainerSnapshot after = new ContainerSnapshot(
            before.getWindowId(),
            before.getContainerType(),
            before.getSlotLayout(),
            before.getRevision() + 1,
            slots,
            null);
        return new VerifiedContainerClick(id + "-shift", source, 0, 1, before, after);
    }

    private static int merge(List<ItemFingerprint> slots, int index, ItemFingerprint item, int left, int limit) {
        ItemFingerprint target = slots.get(index);
        if (target != null && !InventoryTransferClicks.sameItem(item, target)) return left;
        int count = target == null ? 0 : target.getCount();
        int moved = Math.min(left, Math.max(0, limit - count));
        if (moved > 0) slots
            .set(index, new ItemFingerprint(item.getItemId(), item.getMetadata(), item.getDataHash(), count + moved));
        return left - moved;
    }
}
