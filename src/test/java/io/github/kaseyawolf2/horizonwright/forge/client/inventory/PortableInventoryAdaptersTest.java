package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class PortableInventoryAdaptersTest {

    @Test
    public void adventureNormalizationOnlyRemovesNestedContentsFromACopy() {
        ItemStack carrier = carrier();
        NBTTagCompound wearable = new NBTTagCompound();
        wearable.setTag("inventory", new NBTTagList());
        wearable.setInteger("type", 8);
        wearable.setString("tankLeft", "preserve-tank-state");
        wearable.setString("extendedProperties", "preserve-settings");
        carrier.getTagCompound()
            .setTag("wearableData", wearable);
        NBTTagCompound before = (NBTTagCompound) carrier.getTagCompound()
            .copy();
        ItemStack stable = adapter("adventure-backpack").stableCarrier(carrier);
        assertNotSame(carrier, stable);
        assertEquals(before, carrier.getTagCompound());
        assertFalse(
            stable.getTagCompound()
                .getCompoundTag("wearableData")
                .hasKey("inventory"));
        assertEquals(
            8,
            stable.getTagCompound()
                .getCompoundTag("wearableData")
                .getInteger("type"));
        assertEquals(
            "preserve-tank-state",
            stable.getTagCompound()
                .getCompoundTag("wearableData")
                .getString("tankLeft"));
        assertEquals(
            "preserve-settings",
            stable.getTagCompound()
                .getCompoundTag("wearableData")
                .getString("extendedProperties"));
        assertEquals(
            "carrier-name",
            stable.getTagCompound()
                .getString("displayName"));
    }

    @Test
    public void forestryNormalizationPreservesUidAndConfiguration() {
        ItemStack carrier = carrier();
        carrier.getTagCompound()
            .setString("UID", "unique-physical-carrier");
        carrier.getTagCompound()
            .setTag("Slots", new NBTTagCompound());
        carrier.getTagCompound()
            .setTag("Items", new NBTTagList());
        carrier.getTagCompound()
            .setString("filter", "preserve-filter");
        ItemStack stable = adapter("forestry-backpack").stableCarrier(carrier);
        assertTrue(
            carrier.getTagCompound()
                .hasKey("Slots"));
        assertTrue(
            carrier.getTagCompound()
                .hasKey("Items"));
        assertFalse(
            stable.getTagCompound()
                .hasKey("Slots"));
        assertFalse(
            stable.getTagCompound()
                .hasKey("Items"));
        assertEquals("unique-physical-carrier", adapter("forestry-backpack").identity(stable));
        assertEquals(
            "preserve-filter",
            stable.getTagCompound()
                .getString("filter"));
        assertEquals(carrier.getItemDamage(), stable.getItemDamage());
    }

    @Test
    public void terminalNormalizationPreservesNetworkAndUpgradesButAllowsExpectedPowerDrain() {
        ItemStack carrier = carrier();
        carrier.getTagCompound()
            .setDouble("internalCurrentPower", 123.0);
        carrier.getTagCompound()
            .setDouble("internalMaxPower", 1600000.0);
        carrier.getTagCompound()
            .setString("key", "network-key");
        carrier.getTagCompound()
            .setTag("BoosterSlot", new NBTTagCompound());
        ItemStack stable = adapter("ae2-terminal").stableCarrier(carrier);
        assertEquals(
            123.0,
            carrier.getTagCompound()
                .getDouble("internalCurrentPower"),
            0.0);
        assertFalse(
            stable.getTagCompound()
                .hasKey("internalCurrentPower"));
        assertEquals(
            1600000.0,
            stable.getTagCompound()
                .getDouble("internalMaxPower"),
            0.0);
        assertEquals(
            "network-key",
            stable.getTagCompound()
                .getString("key"));
        assertTrue(
            stable.getTagCompound()
                .hasKey("BoosterSlot"));
    }

    @Test
    public void unknownInventoryCannotBeAssumedEmptyOrSupportedByItsNbtShape() {
        ItemStack ordinary = carrier();
        ordinary.getTagCompound()
            .setTag("Items", new NBTTagList());
        assertNull(new PortableInventoryAdapters().find(ordinary));
        assertNull(new PortableInventoryAdapters().find(null));
        assertFalse(adapter("eydamos-backpack").contentsKnown(ordinary));
        assertFalse(adapter("ae2-terminal").contentsKnown(ordinary));
        assertFalse(adapter("adventure-backpack").matchesCarrier(null, ordinary));
        assertFalse(adapter("forestry-backpack").matchesCarrier(null, ordinary));
    }

    private static ItemStack carrier() {
        ItemStack stack = new ItemStack(new Item(), 1, 3);
        NBTTagCompound root = new NBTTagCompound();
        root.setString("displayName", "carrier-name");
        stack.setTagCompound(root);
        return stack;
    }

    private static PortableInventoryAdapter adapter(String id) {
        for (PortableInventoryAdapter adapter : new PortableInventoryAdapters().adapters()) {
            if (adapter.id()
                .equals(id)) return adapter;
        }
        throw new AssertionError("Missing adapter " + id);
    }
}
