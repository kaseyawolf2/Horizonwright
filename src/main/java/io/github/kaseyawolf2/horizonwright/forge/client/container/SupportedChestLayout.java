package io.github.kaseyawolf2.horizonwright.forge.client.container;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Explicitly integrated chest layouts; never infers support from an arbitrary mod inventory. */
final class SupportedChestLayout {

    private SupportedChestLayout() {}

    static boolean supports(Container container) {
        if (container == null) return false;
        String name = container.getClass()
            .getName();
        return container.getClass() == ContainerChest.class || name.equals("cpw.mods.ironchest.ContainerIronChest")
            || name.equals("ganymedes01.etfuturum.inventory.ContainerChestGeneric");
    }

    static IInventory inventory(Container container) {
        if (!supports(container) || container.inventorySlots.isEmpty())
            throw new IllegalArgumentException("Unsupported storage container");
        Slot first = (Slot) container.inventorySlots.get(0);
        IInventory inventory = first.inventory;
        int count = inventory.getSizeInventory();
        if (count < 1 || count > 256 || container.inventorySlots.size() != count + 36)
            throw new IllegalStateException("Unexpected supported-chest inventory size");
        IInventory playerInventory = ((Slot) container.inventorySlots.get(count)).inventory;
        if (!(playerInventory instanceof InventoryPlayer)) throw new IllegalStateException("Missing player inventory");
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = (Slot) container.inventorySlots.get(i);
            int expectedIndex = i < count ? i : i - count < 27 ? i - count + 9 : i - count - 27;
            if (slot.slotNumber != i || slot.getSlotIndex() != expectedIndex
                || slot.inventory != (i < count ? inventory : playerInventory)
                || slot.getSlotStackLimit() != 64)
                throw new IllegalStateException("Unexpected supported-chest slot layout at " + i);
        }
        return inventory;
    }

    static boolean accepts(Container container, ItemStack stack, int count) {
        for (int i = 0; i < count; i++) {
            if (!((Slot) container.inventorySlots.get(i)).isItemValid(stack)) return false;
        }
        return true;
    }
}
