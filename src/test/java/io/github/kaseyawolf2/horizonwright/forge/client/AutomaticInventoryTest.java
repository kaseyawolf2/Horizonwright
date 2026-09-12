package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;

public class AutomaticInventoryTest {

    @Test
    public void excavationUnloadReleasesPlantingSuppliesAndRetainsFoodAndTools() {
        net.minecraft.item.Item seed = new net.minecraft.item.ItemSeeds(
            net.minecraft.init.Blocks.wheat,
            net.minecraft.init.Blocks.farmland);
        net.minecraft.item.Item food = new net.minecraft.item.ItemFood(4, false);
        net.minecraft.item.Item tool = new net.minecraft.item.Item().setMaxStackSize(1);
        net.minecraft.item.ItemStack[] inventory = { new net.minecraft.item.ItemStack(seed, 16),
            new net.minecraft.item.ItemStack(food, 16), new net.minecraft.item.ItemStack(tool) };
        io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter snapshots = new io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter(
            item -> item == seed ? "test:seed" : item == food ? "test:food" : "test:tool");
        NamedLoadout mining = AutomaticInventory
            .inspect(inventory, java.util.Collections.emptyList(), snapshots, false);
        assertFalse(
            mining.getReservations()
                .stream()
                .anyMatch(
                    r -> r.getItemId()
                        .equals("test:seed")));
        assertEquals(
            2,
            mining.getReservations()
                .size());
        assertEquals(
            3,
            AutomaticInventory.inspect(inventory, java.util.Collections.emptyList(), snapshots, true)
                .getReservations()
                .size());
        assertEquals(0, AutomaticInventory.automaticReserveCount(inventory[0], false));
        assertEquals(16, AutomaticInventory.automaticReserveCount(inventory[1], false));
    }

    @Test
    public void extendedSlotToolDoesNotBlockChestUnloadingAndNormalToolRemainsReserved() {
        net.minecraft.item.ItemStack[] inventory = new net.minecraft.item.ItemStack[40];
        net.minecraft.item.Item cargo = new net.minecraft.item.Item();
        net.minecraft.item.Item pick = new net.minecraft.item.Item().setMaxStackSize(1);
        net.minecraft.item.Item axe = new net.minecraft.item.Item().setMaxStackSize(1);
        inventory[0] = new net.minecraft.item.ItemStack(cargo, 64);
        inventory[35] = new net.minecraft.item.ItemStack(pick);
        inventory[36] = new net.minecraft.item.ItemStack(axe);
        io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter snapshotter = new io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter(
            item -> item == cargo ? "test:dirt" : item == pick ? "test:pick" : "test:axe");
        NamedLoadout loadout = AutomaticInventory.inspect(inventory, java.util.Collections.emptyList(), snapshotter);
        assertEquals(
            1,
            loadout.getReservations()
                .size());
        assertEquals(
            "inventory-35",
            loadout.getReservations()
                .get(0)
                .getId());
        List<ItemFingerprint> chestPlayerSlots = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) chestPlayerSlots.add(snapshotter.fingerprint(inventory[slot]));
        io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlan plan = io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlanner
            .plan(loadout, chestPlayerSlots);
        assertTrue(plan.mayStartTransaction());
        assertEquals(java.util.Collections.singletonList(0), plan.getUnloadableSlots());
        assertEquals(axe, inventory[36].getItem());
    }

    @Test
    public void duplicateToolsAreMergedAndBothKeptDespiteDifferentDamage() {
        List<LoadoutReservation> items = new ArrayList<>();
        AutomaticInventory.reserve(items, new ItemFingerprint("test:pick", 1, "a", 1), 0, true, LoadoutRole.TOOL);
        AutomaticInventory.reserve(items, new ItemFingerprint("test:pick", 70, "b", 1), 35, true, LoadoutRole.TOOL);
        new NamedLoadout("test", "test", items).validate();
        assertEquals(1, items.size());
        assertEquals(
            2,
            items.get(0)
                .getMinimumCount());
    }

    @Test
    public void multipleFoodStacksKeepBoundedReserveAndLeaveHarvestSurplusUnloadable() {
        List<LoadoutReservation> items = new ArrayList<>();
        AutomaticInventory.reserve(items, new ItemFingerprint("test:food", 0, "a", 12), 0, false, LoadoutRole.FOOD);
        AutomaticInventory.reserve(items, new ItemFingerprint("test:food", 0, "a", 20), 1, false, LoadoutRole.FOOD);
        assertEquals(
            16,
            items.get(0)
                .getMinimumCount());
        new NamedLoadout("test", "test", items).validate();
    }

    @Test
    public void automaticFoodReservesDistinguishExactNbtIdentity() {
        List<LoadoutReservation> items = new ArrayList<>();
        AutomaticInventory.reserve(items, new ItemFingerprint("test:food", 0, "a", 64), 0, false, LoadoutRole.FOOD);
        AutomaticInventory.reserve(items, new ItemFingerprint("test:food", 0, "b", 32), 1, false, LoadoutRole.FOOD);
        assertEquals(2, items.size());
        assertEquals(
            "a",
            items.get(0)
                .getDataHash());
        assertEquals(
            "b",
            items.get(1)
                .getDataHash());
        assertEquals(
            16,
            items.get(0)
                .getMinimumCount());
        assertEquals(
            16,
            items.get(1)
                .getMinimumCount());
        new NamedLoadout("test", "test", items).validate();
    }

    @Test
    public void seedsKeepBoundedReplantingReserveInsteadOfAllHarvestedSeeds() {
        List<LoadoutReservation> items = new ArrayList<>();
        AutomaticInventory
            .reserve(items, new ItemFingerprint("test:seed", 0, "a", 64), 0, false, LoadoutRole.OTHER_RESERVED);
        AutomaticInventory
            .reserve(items, new ItemFingerprint("test:seed", 0, "a", 64), 1, false, LoadoutRole.OTHER_RESERVED);
        assertEquals(1, items.size());
        assertEquals(
            16,
            items.get(0)
                .getMinimumCount());
    }

    @Test
    public void smallFoodStockDoesNotCreateAnImpossibleMinimum() {
        List<LoadoutReservation> items = new ArrayList<>();
        AutomaticInventory.reserve(items, new ItemFingerprint("test:food", 0, "a", 4), 0, false, LoadoutRole.FOOD);
        assertEquals(
            4,
            items.get(0)
                .getMinimumCount());
    }

    @Test
    public void explicitRepairReservationIsPreserved() {
        List<LoadoutReservation> items = new ArrayList<>();
        items.add(new LoadoutReservation("supply", LoadoutRole.REPAIR_MATERIAL, "test:supply", 0, null, 16));
        AutomaticInventory.reserve(items, new ItemFingerprint("test:supply", 0, "a", 30), 0, false, LoadoutRole.FOOD);
        assertEquals(1, items.size());
        assertEquals(
            16,
            items.get(0)
                .getMinimumCount());
    }
}
