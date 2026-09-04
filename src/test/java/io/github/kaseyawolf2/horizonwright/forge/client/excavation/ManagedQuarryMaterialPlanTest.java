package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class ManagedQuarryMaterialPlanTest {

    @Test
    public void existingHotbarMaterialNeedsNoInventorySwap() {
        ManagedQuarryMaterialPlan plan = ManagedQuarryMaterialPlan.choose(4, occupied(false), 2);

        assertEquals(4, plan.getInventorySlot());
        assertEquals(4, plan.getHotbarSlot());
        assertFalse(plan.requiresStaging());
    }

    @Test
    public void mainInventoryMaterialUsesFirstEmptyHotbarSlot() {
        boolean[] occupied = occupied(true);
        occupied[3] = false;

        ManagedQuarryMaterialPlan plan = ManagedQuarryMaterialPlan.choose(17, occupied, 6);

        assertEquals(17, plan.getInventorySlot());
        assertEquals(3, plan.getHotbarSlot());
        assertTrue(plan.requiresStaging());
    }

    @Test
    public void fullHotbarUsesSelectedSlotSoDisplacedStackCanBeSwappedBack() {
        ManagedQuarryMaterialPlan plan = ManagedQuarryMaterialPlan.choose(35, occupied(true), 7);

        assertEquals(7, plan.getHotbarSlot());
        assertTrue(plan.requiresStaging());
    }

    @Test
    public void malformedSlotEvidenceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryMaterialPlan.choose(36, occupied(true), 0));
        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryMaterialPlan.choose(10, new boolean[8], 0));
        assertThrows(IllegalArgumentException.class, () -> ManagedQuarryMaterialPlan.choose(10, occupied(true), 9));
    }

    private static boolean[] occupied(boolean value) {
        boolean[] result = new boolean[9];
        Arrays.fill(result, value);
        return result;
    }
}
