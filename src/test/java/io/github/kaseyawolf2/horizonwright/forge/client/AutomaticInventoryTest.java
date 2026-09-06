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
    public void multipleFoodStacksAreReservedWithoutOverlappingRules() {
        List<LoadoutReservation> items = new ArrayList<>();
        AutomaticInventory.reserve(items, new ItemFingerprint("test:food", 0, "a", 12), 0, false, LoadoutRole.FOOD);
        AutomaticInventory.reserve(items, new ItemFingerprint("test:food", 0, "a", 20), 1, false, LoadoutRole.FOOD);
        assertEquals(
            32,
            items.get(0)
                .getMinimumCount());
        new NamedLoadout("test", "test", items).validate();
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
