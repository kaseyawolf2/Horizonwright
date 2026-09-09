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
