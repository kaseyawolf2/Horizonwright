package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

/** Exact mode-2 supply swap in the vanilla player container; never shifts an item into an inferred slot. */
final class PlayerInventoryHotbarSwap {

    private PlayerInventoryHotbarSwap() {}

    static ContainerTransaction plan(String id, long epoch, ContainerSnapshot before, int inventorySlot, int hotbar) {
        if (before == null || before.getWindowId() != 0
            || before.getSlots()
                .size() != 45
            || !"net.minecraft.inventory.ContainerPlayer".equals(before.getContainerType())
            || before.getCursor() != null
            || inventorySlot < 9
            || inventorySlot >= 36
            || hotbar < 0
            || hotbar >= 9)
            throw new IllegalArgumentException("an empty cursor and exact player inventory slots are required");
        List<ItemFingerprint> slots = new ArrayList<>(before.getSlots());
        Collections.swap(slots, inventorySlot, 36 + hotbar);
        ContainerSnapshot after = new ContainerSnapshot(
            before.getWindowId(),
            before.getContainerType(),
            before.getSlotLayout(),
            Math.addExact(before.getRevision(), 1L),
            slots,
            null);
        return new ContainerTransaction(
            id,
            epoch,
            Collections
                .singletonList(new VerifiedContainerClick(id + "-click", inventorySlot, hotbar, 2, before, after)));
    }
}
