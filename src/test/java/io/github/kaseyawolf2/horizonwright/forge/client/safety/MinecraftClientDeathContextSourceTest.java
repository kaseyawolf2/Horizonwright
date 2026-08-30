package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.Test;

public class MinecraftClientDeathContextSourceTest {

    @Test
    public void canonicalNbtIgnoresCompoundInsertionOrderButPreservesValues() {
        NBTTagCompound first = new NBTTagCompound();
        first.setString("zeta", "last");
        first.setInteger("alpha", 7);
        NBTTagCompound nestedFirst = new NBTTagCompound();
        nestedFirst.setString("right", "two");
        nestedFirst.setString("left", "one");
        first.setTag("nested", nestedFirst);

        NBTTagCompound second = new NBTTagCompound();
        NBTTagCompound nestedSecond = new NBTTagCompound();
        nestedSecond.setString("left", "one");
        nestedSecond.setString("right", "two");
        second.setTag("nested", nestedSecond);
        second.setInteger("alpha", 7);
        second.setString("zeta", "last");

        assertEquals(
            MinecraftClientDeathContextSource.canonicalNbt(first),
            MinecraftClientDeathContextSource.canonicalNbt(second));
        second.setInteger("alpha", 8);
        assertNotEquals(
            MinecraftClientDeathContextSource.canonicalNbt(first),
            MinecraftClientDeathContextSource.canonicalNbt(second));
    }

    @Test
    public void canonicalNbtPreservesListOrder() {
        NBTTagList forward = new NBTTagList();
        forward.appendTag(new NBTTagString("a"));
        forward.appendTag(new NBTTagString("b"));
        NBTTagList reverse = new NBTTagList();
        reverse.appendTag(new NBTTagString("b"));
        reverse.appendTag(new NBTTagString("a"));

        assertNotEquals(
            MinecraftClientDeathContextSource.canonicalNbt(forward),
            MinecraftClientDeathContextSource.canonicalNbt(reverse));
    }

    @Test
    public void carriedCursorStackIsPartOfTheDeathManifestWithoutInventingEmptyCapacity() {
        ItemStack main = new ItemStack(new Item());
        ItemStack armor = new ItemStack(new Item());
        ItemStack cursor = new ItemStack(new Item(), 3);

        ClientInventorySnapshot snapshot = MinecraftClientDeathContextSource.captureInventory(
            new ItemStack[] { main },
            new ItemStack[] { armor },
            cursor,
            stack -> new io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryStack(
                stack == cursor ? "cursor" : stack == main ? "main" : "armor",
                stack.stackSize,
                stack.getMaxStackSize()));

        assertEquals(3, snapshot.getSlotCount());
        assertEquals(
            3,
            snapshot.getStacks()
                .size());
        assertEquals(
            0,
            snapshot.toManifest()
                .getEmptySlotCount());
        assertTrue(
            snapshot.getStacks()
                .stream()
                .anyMatch(
                    stack -> stack.getItemFingerprint()
                        .equals("cursor")));
    }

    @Test
    public void emptyCursorDoesNotBecomeARecoveryCapacitySlot() {
        ClientInventorySnapshot snapshot = MinecraftClientDeathContextSource
            .captureInventory(new ItemStack[] { null }, new ItemStack[] { null }, null);

        assertEquals(2, snapshot.getSlotCount());
        assertEquals(
            2,
            snapshot.toManifest()
                .getEmptySlotCount());
    }
}
