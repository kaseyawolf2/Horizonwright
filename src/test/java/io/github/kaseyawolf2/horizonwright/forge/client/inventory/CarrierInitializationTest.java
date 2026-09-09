package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class CarrierInitializationTest {

    @Test
    public void forestryAcceptsOnlyFirstIntegerUidAndDoesNotMutateSnapshots() {
        ItemStack before = new ItemStack(new Item());
        ItemStack after = before.copy();
        after.setTagCompound(new NBTTagCompound());
        after.getTagCompound()
            .setInteger("UID", Integer.MIN_VALUE);
        after.getTagCompound()
            .setTag("Slots", new NBTTagCompound());
        NBTTagCompound observed = (NBTTagCompound) after.getTagCompound()
            .copy();
        assertTrue(CarrierInitialization.forestry(before, after));
        assertNull(before.getTagCompound());
        assertEquals(observed, after.getTagCompound());
        assertEquals("int:-2147483648", CarrierInitialization.forestryIdentity(after));
    }

    @Test
    public void forestryCannotReplaceExistingUidEvenWhenItIsEmptyOrMalformed() {
        ItemStack before = carrier();
        before.getTagCompound()
            .setInteger("UID", 7);
        ItemStack after = before.copy();
        assertTrue(CarrierInitialization.forestry(before, after));
        after.getTagCompound()
            .setInteger("UID", 8);
        assertFalse(CarrierInitialization.forestry(before, after));
        before.getTagCompound()
            .setString("UID", "");
        assertFalse(CarrierInitialization.forestry(before, after));
        before.getTagCompound()
            .setTag("UID", new NBTTagCompound());
        assertFalse(CarrierInitialization.forestry(before, after));
        before.getTagCompound()
            .removeTag("UID");
        after.getTagCompound()
            .setString("UID", "new-string-is-not-the-native-initializer");
        assertFalse(CarrierInitialization.forestry(before, after));
    }

    @Test
    public void forestryPreservesLegacyIdentityAndEveryNoncontentTag() {
        ItemStack before = carrier();
        before.getTagCompound()
            .setString("UID", "legacy-identity");
        before.getTagCompound()
            .setString("filter", "miner");
        before.getTagCompound()
            .setTag("Items", new NBTTagList());
        ItemStack after = before.copy();
        after.getTagCompound()
            .removeTag("Items");
        after.getTagCompound()
            .setTag("Slots", new NBTTagCompound());
        assertTrue(CarrierInitialization.forestry(before, after));
        assertEquals("legacy-identity", CarrierInitialization.forestryIdentity(after));
        after.getTagCompound()
            .setString("filter", "forester");
        assertFalse(CarrierInitialization.forestry(before, after));
    }

    @Test
    public void forestryUidIntroductionDoesNotPermitAnUnrelatedConfigurationChange() {
        ItemStack before = carrier();
        ItemStack after = before.copy();
        after.getTagCompound()
            .setInteger("UID", 1);
        after.getTagCompound()
            .setInteger("backpackMode", 2);
        assertFalse(CarrierInitialization.forestry(before, after));
    }

    @Test
    public void adventureAcceptsVerifiedEmptyFirstSaveWithoutChangingEitherSnapshot() {
        ItemStack before = carrier();
        ItemStack after = before.copy();
        after.getTagCompound()
            .setTag("wearableData", initialWearable());
        NBTTagCompound previous = (NBTTagCompound) before.getTagCompound()
            .copy();
        NBTTagCompound observed = (NBTTagCompound) after.getTagCompound()
            .copy();
        assertTrue(CarrierInitialization.adventure(before, after));
        assertEquals(previous, before.getTagCompound());
        assertEquals(observed, after.getTagCompound());
        before.setTagCompound(null);
        after.getTagCompound()
            .removeTag("displayName");
        assertTrue(CarrierInitialization.adventure(before, after));
    }

    @Test
    public void adventureRejectsChangedExistingVariantConfigurationAndTankState() {
        ItemStack before = carrier();
        before.getTagCompound()
            .setTag("wearableData", initialWearable());
        ItemStack after = before.copy();
        after.getTagCompound()
            .getCompoundTag("wearableData")
            .setByte("type", (byte) 7);
        assertFalse(CarrierInitialization.adventure(before, after));
        after = before.copy();
        after.getTagCompound()
            .getCompoundTag("wearableData")
            .setBoolean("disableCycling", true);
        assertFalse(CarrierInitialization.adventure(before, after));
        after = before.copy();
        after.getTagCompound()
            .getCompoundTag("wearableData")
            .getCompoundTag("leftTank")
            .setString("FluidName", "water");
        assertFalse(CarrierInitialization.adventure(before, after));
    }

    @Test
    public void adventureOnlyIntroducesDefaultValuesAndNeverUnknownKeys() {
        ItemStack before = carrier();
        ItemStack after = before.copy();
        after.getTagCompound()
            .setTag("wearableData", initialWearable());
        after.getTagCompound()
            .getCompoundTag("wearableData")
            .setInteger("lastTime", 123);
        assertFalse(CarrierInitialization.adventure(before, after));
        after.getTagCompound()
            .getCompoundTag("wearableData")
            .setInteger("lastTime", 0);
        after.getTagCompound()
            .getCompoundTag("wearableData")
            .getCompoundTag("extendedProperties")
            .setBoolean("sleepingBag", true);
        assertFalse(CarrierInitialization.adventure(before, after));
        after.getTagCompound()
            .getCompoundTag("wearableData")
            .setTag("extendedProperties", new NBTTagCompound());
        after.getTagCompound()
            .getCompoundTag("wearableData")
            .setBoolean("newUnknownField", false);
        assertFalse(CarrierInitialization.adventure(before, after));
    }

    @Test
    public void adventureRejectsLegacyMigrationAndMalformedData() {
        ItemStack before = carrier();
        before.getTagCompound()
            .setTag("backpackData", new NBTTagCompound());
        ItemStack after = before.copy();
        after.getTagCompound()
            .removeTag("backpackData");
        after.getTagCompound()
            .setTag("wearableData", initialWearable());
        assertFalse(CarrierInitialization.adventure(before, after));
        before.getTagCompound()
            .removeTag("backpackData");
        before.getTagCompound()
            .setString("wearableData", "malformed");
        assertFalse(CarrierInitialization.adventure(before, after));
    }

    @Test
    public void firstOpenCannotReplaceTheItemDamageOrCount() {
        ItemStack before = carrier();
        ItemStack after = before.copy();
        after.stackSize = 2;
        assertFalse(CarrierInitialization.forestry(before, after));
        assertFalse(CarrierInitialization.adventure(before, after));
        after = before.copy();
        after.setItemDamage(4);
        assertFalse(CarrierInitialization.forestry(before, after));
        assertFalse(CarrierInitialization.adventure(before, after));
        after = carrier();
        assertFalse(CarrierInitialization.forestry(before, after));
        assertFalse(CarrierInitialization.adventure(before, after));
    }

    @Test
    public void terminalNetworkCountRetainsLongPrecision() {
        long moreThanInt = (long) Integer.MAX_VALUE + 123L;
        assertEquals(moreThanInt, Ae2TerminalAdapter.networkCount(new CountEntry(moreThanInt)));
        assertEquals(Long.MAX_VALUE, Ae2TerminalAdapter.networkCount(new CountEntry(Long.MAX_VALUE)));
        assertEquals(0, Ae2TerminalAdapter.networkCount(new CountEntry(-1)));
        assertEquals(-1, new Ae2TerminalAdapter().availableCount(null, carrier()));
    }

    private static ItemStack carrier() {
        ItemStack result = new ItemStack(new Item(), 1, 3);
        result.setTagCompound(new NBTTagCompound());
        result.getTagCompound()
            .setString("displayName", "carrier-name");
        return result;
    }

    private static NBTTagCompound initialWearable() {
        NBTTagCompound wearable = new NBTTagCompound();
        wearable.setByte("type", (byte) 0);
        wearable.setTag("inventory", new NBTTagList());
        wearable.setBoolean("disableCycling", false);
        wearable.setBoolean("disableNVision", false);
        wearable.setInteger("lastTime", 0);
        wearable.setTag("extendedProperties", new NBTTagCompound());
        NBTTagCompound tank = new NBTTagCompound();
        tank.setString("Empty", "");
        wearable.setTag("leftTank", tank);
        wearable.setTag("rightTank", tank.copy());
        return wearable;
    }

    private static final class CountEntry {

        private final long count;

        CountEntry(long count) {
            this.count = count;
        }

        public long getStackSize() {
            return count;
        }
    }
}
