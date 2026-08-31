package io.github.kaseyawolf2.horizonwright.core.logistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;

public class LoadoutAndUnloadPlannerTest {

    private static final ItemFingerprint TINKERS_PICK = item("TConstruct:pickaxe", 0, "tool-uuid-a", 1);
    private static final ItemFingerprint SCANNER = item("Thaumcraft:ItemThaumometer", 0, "scanner", 1);
    private static final ItemFingerprint FOOD = item("minecraft:bread", 0, "none", 32);
    private static final ItemFingerprint EXTRA_FOOD = item("minecraft:bread", 0, "none", 16);
    private static final ItemFingerprint ORE = item("gregtech:gt.blockores", 32, "ore", 48);

    @Test
    public void explicitlyReservedModdedGearIsNeverInferredFromVanillaTypes() {
        NamedLoadout loadout = loadout(
            reservation("pick", LoadoutRole.TOOL, "TConstruct:pickaxe", 0, null, 1),
            reservation("scanner", LoadoutRole.SCANNING_TOOL, "Thaumcraft:ItemThaumometer", -1, null, 1),
            reservation("food", LoadoutRole.FOOD, "minecraft:bread", 0, "none", 16));

        UnloadPlan plan = UnloadPlanner
            .plan(loadout, Arrays.asList(TINKERS_PICK, SCANNER, FOOD, EXTRA_FOOD, ORE, null));

        assertEquals(UnloadPlanStatus.READY, plan.getStatus());
        assertEquals(Arrays.asList(0, 1, 2), plan.getReservedSlots());
        assertEquals(Arrays.asList(3, 4), plan.getUnloadableSlots());
        assertTrue(plan.mayStartTransaction());
    }

    @Test
    public void incompleteLoadoutRefusesAllUnloadingAndReportsExactDeficit() {
        NamedLoadout loadout = loadout(
            reservation("food", LoadoutRole.FOOD, "minecraft:bread", 0, "none", 48),
            reservation("repair", LoadoutRole.REPAIR_MATERIAL, "TConstruct:materials", 3, null, 4));

        UnloadPlan plan = UnloadPlanner.plan(loadout, Arrays.asList(FOOD, ORE));

        assertEquals(UnloadPlanStatus.LOADOUT_INCOMPLETE, plan.getStatus());
        assertFalse(plan.mayStartTransaction());
        assertTrue(
            plan.getUnloadableSlots()
                .isEmpty());
        assertEquals(
            Integer.valueOf(16),
            plan.getMissingCounts()
                .get("food"));
        assertEquals(
            Integer.valueOf(4),
            plan.getMissingCounts()
                .get("repair"));
    }

    @Test
    public void ambiguousRulesAreRejectedInsteadOfDoubleCountingOneStack() {
        LoadoutReservation anyPick = reservation("any-pick", LoadoutRole.TOOL, "TConstruct:pickaxe", -1, null, 1);
        LoadoutReservation exactPick = reservation(
            "exact-pick",
            LoadoutRole.COMBAT_GEAR,
            "TConstruct:pickaxe",
            0,
            "tool-uuid-a",
            1);

        assertThrows(
            IllegalArgumentException.class,
            () -> new NamedLoadout("ambiguous", "Ambiguous", Arrays.asList(anyPick, exactPick)));
    }

    @Test
    public void destinationFilterDefersNonmatchingStacksWithoutTreatingThemAsReserved() {
        NamedLoadout loadout = loadout(reservation("pick", LoadoutRole.TOOL, "TConstruct:pickaxe", 0, null, 1));
        StorageItemFilter oresOnly = new StorageItemFilter(
            StorageFilterMode.ALLOW_MATCHES,
            Collections.singletonList(new StorageItemRule("gregtech:gt.blockores", -1, null)));

        UnloadPlan plan = UnloadPlanner.plan(loadout, Arrays.asList(TINKERS_PICK, ORE, FOOD), oresOnly);

        assertEquals(Collections.singletonList(0), plan.getReservedSlots());
        assertEquals(Collections.singletonList(1), plan.getUnloadableSlots());
        assertEquals(Collections.singletonList(2), plan.getDeferredSlots());
    }

    private static NamedLoadout loadout(LoadoutReservation... reservations) {
        return new NamedLoadout("excavation", "Excavation", Arrays.asList(reservations));
    }

    private static LoadoutReservation reservation(String id, LoadoutRole role, String itemId, int metadata,
        String dataHash, int minimumCount) {
        return new LoadoutReservation(id, role, itemId, metadata, dataHash, minimumCount);
    }

    private static ItemFingerprint item(String id, int metadata, String hash, int count) {
        return new ItemFingerprint(id, metadata, hash, count);
    }
}
