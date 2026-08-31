package io.github.kaseyawolf2.horizonwright.core.safety.death;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class InventoryManifestTest {

    @Test
    public void subtractsRespawnRetainedItemsIntoConservativeGraveContents() {
        InventoryManifest preDeath = inventory(
            stack("minecraft:diamond", 12, 64),
            stack("minecraft:bread", 8, 64),
            stack("gregtech:soulbound_tool", 1, 1));
        InventoryManifest retained = inventory(stack("gregtech:soulbound_tool", 1, 1));

        InventoryManifest grave = preDeath.subtractContents(retained)
            .get();

        assertEquals(
            2,
            grave.getStacks()
                .size());
        assertEquals(preDeath.getContentFingerprint(), retained.fingerprintWith(grave));
    }

    @Test
    public void splitCountsRemainValidInventoryStacks() {
        InventoryManifest preDeath = inventory(
            stack("minecraft:cobblestone", 64, 64),
            stack("minecraft:cobblestone", 36, 64));

        InventoryManifest grave = preDeath.subtractContents(InventoryManifest.empty(36))
            .get();

        assertEquals(
            2,
            grave.getStacks()
                .size());
        assertEquals(
            64,
            grave.getStacks()
                .get(0)
                .getCount());
        assertEquals(
            36,
            grave.getStacks()
                .get(1)
                .getCount());
    }

    @Test
    public void unexplainedRespawnItemsOrCountsReturnNoReconstruction() {
        InventoryManifest preDeath = inventory(stack("minecraft:diamond", 2, 64));

        assertFalse(
            preDeath.subtractContents(inventory(stack("minecraft:diamond", 3, 64)))
                .isPresent());
        assertFalse(
            preDeath.subtractContents(inventory(stack("minecraft:dirt", 1, 64)))
                .isPresent());
        assertTrue(
            preDeath.subtractContents(inventory(stack("minecraft:diamond", 2, 64)))
                .get()
                .isEmpty());
    }

    private static InventoryManifest inventory(InventoryStack... stacks) {
        return new InventoryManifest(36, Arrays.asList(stacks));
    }

    private static InventoryStack stack(String item, int count, int maximum) {
        return new InventoryStack(item + "|meta=0|nbt=none", count, maximum);
    }
}
