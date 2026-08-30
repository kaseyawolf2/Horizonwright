package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhysicalInputPreemptionPolicyTest {

    @Test
    public void inventoryBindingNeverPreemptsAutomationMovement() {
        int inventory = 18;

        assertFalse(PhysicalInputPreemptionPolicy.shouldPreempt(inventory, inventory, 17, 30, -100, 2, inventory));
    }

    @Test
    public void gameplayAndHotbarBindingsStillPreemptWhileUnrelatedKeysDoNot() {
        int inventory = 18;
        int[] gameplay = { 17, 30, -100, 2 };

        assertTrue(PhysicalInputPreemptionPolicy.shouldPreempt(17, inventory, gameplay));
        assertTrue(PhysicalInputPreemptionPolicy.shouldPreempt(-100, inventory, gameplay));
        assertTrue(PhysicalInputPreemptionPolicy.shouldPreempt(2, inventory, gameplay));
        assertFalse(PhysicalInputPreemptionPolicy.shouldPreempt(35, inventory, gameplay));
    }
}
