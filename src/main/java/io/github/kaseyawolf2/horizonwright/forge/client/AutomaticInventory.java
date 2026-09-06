package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

/** Inventory-derived reservations; users never need to name a loadout or identify tool slots. */
public final class AutomaticInventory {

    public static final String ID = "automatic-inventory";

    private AutomaticInventory() {}

    public static NamedLoadout inspect(Minecraft mc, ProfileEnvelope profile) {
        if (mc.thePlayer == null) throw new IllegalStateException("Join the world before inspecting inventory.");
        List<LoadoutReservation> reservations = new ArrayList<>();
        for (NamedLoadout saved : profile.getNamedLoadouts()) {
            if (ID.equals(saved.getId())) for (LoadoutReservation item : saved.getReservations()) {
                if (item.getRole() == LoadoutRole.REPAIR_MATERIAL) reservations.add(item);
            }
        }
        MinecraftContainerSnapshotter snapshots = new MinecraftContainerSnapshotter();
        for (int slot = 0; slot < mc.thePlayer.inventory.mainInventory.length; slot++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack == null) continue;
            boolean tool = stack.getMaxStackSize() == 1 || !stack.getItem()
                .getToolClasses(stack)
                .isEmpty();
            boolean food = stack.getItem() instanceof ItemFood;
            if (!tool && !food) continue;
            ItemFingerprint item = snapshots.fingerprint(stack);
            reserve(
                reservations,
                item,
                slot,
                tool,
                food ? LoadoutRole.FOOD : stack.getItem() instanceof ItemArmor ? LoadoutRole.ARMOR : LoadoutRole.TOOL);
        }
        return new NamedLoadout(ID, "Automatic inventory", reservations);
    }

    static void reserve(List<LoadoutReservation> reservations, ItemFingerprint item, int slot, boolean tool,
        LoadoutRole role) {
        LoadoutReservation existing = null;
        for (LoadoutReservation value : reservations) if (value.matches(item)) {
            existing = value;
            break;
        }
        if (existing != null) {
            if (existing.getRole() != LoadoutRole.REPAIR_MATERIAL) {
                reservations.remove(existing);
                reservations.add(
                    new LoadoutReservation(
                        existing.getId(),
                        existing.getRole(),
                        existing.getItemId(),
                        existing.getMetadata(),
                        null,
                        Math.addExact(existing.getMinimumCount(), item.getCount())));
            }
        } else reservations.add(
            new LoadoutReservation(
                "inventory-" + slot,
                role,
                item.getItemId(),
                tool ? -1 : item.getMetadata(),
                null,
                item.getCount()));
    }

}
