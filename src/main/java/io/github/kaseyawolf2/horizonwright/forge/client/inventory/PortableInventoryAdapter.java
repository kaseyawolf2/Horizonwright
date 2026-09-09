package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Optional mod boundary. Reads never mutate carriers; writes use the mod's ordinary server protocol. */
public interface PortableInventoryAdapter {

    String id();

    boolean supports(ItemStack stack);

    /** Occupied, copied stacks only. Consult contentsKnown before treating an empty result as empty storage. */
    List<ItemStack> readContents(ItemStack stack);

    boolean contentsKnown(ItemStack stack);

    boolean canStore(ItemStack bag, ItemStack cargo);

    boolean matches(Container opened);

    /** Real storage slots only; never crafting, filters, upgrades, tanks, or the player inventory. */
    List<Slot> storageSlots(Container opened);

    boolean matchesCarrier(Container opened, ItemStack carrier);

    /** Persistent server identity where supplied by the mod; otherwise empty. */
    String identity(ItemStack stack);

    /** Copy excluding only nested contents, preserving all identity and configuration tags. */
    ItemStack stableCarrier(ItemStack stack);

    /**
     * Called once after an independently validated open-container/carrier binding. Allows only the
     * adapter's verified initialization additions, never replacing an existing identity or setting.
     */
    default boolean initializedCarrierMatches(ItemStack before, ItemStack after) {
        return supports(before) && supports(after)
            && ItemStack.areItemStacksEqual(stableCarrier(before), stableCarrier(after));
    }

    /** Caller must hold USE, HELD_ITEM_USE and CONTAINER capabilities and the supplied item in hand. */
    void openHeld(Minecraft mc, ItemStack stack);
}
