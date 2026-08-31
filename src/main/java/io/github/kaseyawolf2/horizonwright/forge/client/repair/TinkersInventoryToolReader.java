package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;

/** Reuses the pinned Tinkers tool decoder for inventory-side pre-work durability checks. */
public final class TinkersInventoryToolReader {

    public RepairToolSnapshot read(ItemStack stack, int reservedInventorySlot) {
        if (stack == null) throw new IllegalStateException("reserved excavation tool slot is empty");
        return TinkersRepairContainerAdapter.readTool(stack, reservedInventorySlot);
    }
}
