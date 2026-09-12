package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class ExcavationDropCollectorTest {

    @Test
    public void pickupRequiresEmptySpaceOrAnExactNonFullStack() {
        Item item = new Item();
        ItemStack drop = new ItemStack(item, 8);
        assertTrue(ExcavationDropCollector.hasRoom(new ItemStack[36], drop));
        ItemStack[] inventory = new ItemStack[40];
        for (int i = 0; i < 36; i++) inventory[i] = new ItemStack(item, 64);
        assertFalse(ExcavationDropCollector.hasRoom(inventory, drop)); // Extra equipment slots do not count.
        inventory[0].stackSize = 63;
        assertTrue(ExcavationDropCollector.hasRoom(inventory, drop));
        inventory[0].setTagCompound(new NBTTagCompound());
        inventory[0].getTagCompound()
            .setString("owner", "different");
        assertFalse(ExcavationDropCollector.hasRoom(inventory, drop));
        assertFalse(ExcavationDropCollector.hasRoom(inventory, null));
    }
}
