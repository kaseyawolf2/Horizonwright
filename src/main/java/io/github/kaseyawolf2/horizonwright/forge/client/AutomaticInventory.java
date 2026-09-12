package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemSeedFood;
import net.minecraft.item.ItemSeeds;
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
    public static final int CONSUMABLE_RESERVE = 16;

    private AutomaticInventory() {}

    public static NamedLoadout inspect(Minecraft mc, ProfileEnvelope profile) {
        if (mc.thePlayer == null) throw new IllegalStateException("Join the world before inspecting inventory.");
        return inspect(mc, profile, true);
    }

    public static NamedLoadout inspect(Minecraft mc, ProfileEnvelope profile, boolean keepPlantingSupplies) {
        if (mc.thePlayer == null) throw new IllegalStateException("Join the world before inspecting inventory.");
        return inspect(
            mc.thePlayer.inventory.mainInventory,
            profile.getNamedLoadouts(),
            new MinecraftContainerSnapshotter(),
            keepPlantingSupplies);
    }

    static NamedLoadout inspect(ItemStack[] inventory, List<NamedLoadout> savedLoadouts,
        MinecraftContainerSnapshotter snapshots) {
        return inspect(inventory, savedLoadouts, snapshots, true);
    }

    static NamedLoadout inspect(ItemStack[] inventory, List<NamedLoadout> savedLoadouts,
        MinecraftContainerSnapshotter snapshots, boolean keepPlantingSupplies) {
        List<LoadoutReservation> reservations = new ArrayList<>();
        for (NamedLoadout saved : savedLoadouts) {
            if (ID.equals(saved.getId())) for (LoadoutReservation item : saved.getReservations()) {
                if (item.getRole() == LoadoutRole.REPAIR_MATERIAL) reservations.add(item);
            }
        }
        io.github.kaseyawolf2.horizonwright.forge.client.inventory.PortableInventoryAdapters extensions = new io.github.kaseyawolf2.horizonwright.forge.client.inventory.PortableInventoryAdapters();
        // Extended/offhand entries are not part of the chest's 36 transferable player slots.
        // They cannot be unloaded here, nor required as if they were missing from that window.
        for (int slot = 0; slot < Math.min(36, inventory.length); slot++) {
            ItemStack stack = inventory[slot];
            if (stack == null) continue;
            // Food provisioning is disabled until auto-eating owns an explicit supply policy.
            if (stack.getItem() instanceof ItemFood) continue;
            boolean carrier = extensions.find(stack) != null;
            boolean tool = carrier || stack.getMaxStackSize() == 1
                || !ToolCapabilities.classes(stack)
                    .isEmpty();
            boolean planting = keepPlantingSupplies && plantingSupply(stack);
            if (!tool && !planting) continue;
            ItemFingerprint item = snapshots.fingerprint(stack);
            reserve(
                reservations,
                item,
                slot,
                tool,
                planting ? LoadoutRole.OTHER_RESERVED
                    : stack.getItem() instanceof ItemArmor ? LoadoutRole.ARMOR : LoadoutRole.TOOL);
        }
        return new NamedLoadout(ID, "Automatic inventory", reservations);
    }

    static void reserve(List<LoadoutReservation> reservations, ItemFingerprint item, int slot, boolean tool,
        LoadoutRole role) {
        boolean bounded = role == LoadoutRole.FOOD || role == LoadoutRole.OTHER_RESERVED && !tool;
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
                        existing.getDataHash(),
                        bounded
                            ? Math.min(CONSUMABLE_RESERVE, Math.addExact(existing.getMinimumCount(), item.getCount()))
                            : Math.addExact(existing.getMinimumCount(), item.getCount())));
            }
        } else reservations.add(
            new LoadoutReservation(
                "inventory-" + slot,
                role,
                item.getItemId(),
                tool && !bounded ? -1 : item.getMetadata(),
                bounded ? item.getDataHash() : null,
                bounded ? Math.min(CONSUMABLE_RESERVE, item.getCount()) : item.getCount()));
    }

    /** Small working reserves keep ordinary harvest output eligible for packing and final unloading. */
    public static int automaticReserveCount(ItemStack stack) {
        return automaticReserveCount(stack, true);
    }

    public static int automaticReserveCount(ItemStack stack, boolean keepPlantingSupplies) {
        return stack != null && !(stack.getItem() instanceof ItemFood) && keepPlantingSupplies && plantingSupply(stack)
            ? CONSUMABLE_RESERVE
            : 0;
    }

    private static boolean plantingSupply(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof ItemSeeds || stack.getItem() instanceof ItemSeedFood
            || stack.getItem() == Item.getItemFromBlock(Blocks.sapling)
            || stack.getItem() == Items.dye && stack.getItemDamage() == 3);
    }

}
