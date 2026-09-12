package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.*;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLog;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class PillarMaterialPolicyTest {

    private static class Log extends BlockLog {
    }

    @Test
    public void permitsLogsFromMainInventory() {
        ItemStack logs = new ItemStack(new ItemBlock(new Log()), 16);
        ItemStack[] inventory = new ItemStack[36];
        inventory[17] = logs;
        assertTrue(PillarMaterialPolicy.suitable(logs));
        assertEquals(17, PillarMaterialPolicy.findSlot(inventory, -1));
        assertTrue(
            PillarMaterialPolicy.hotbarItems(inventory)
                .isEmpty());
        inventory[8] = logs;
        assertEquals(
            1,
            PillarMaterialPolicy.hotbarItems(inventory)
                .size());
    }

    @Test
    public void avoidsTaggedBlocksGravityBlocksAndEquipment() {
        ItemStack tagged = new ItemStack(new ItemBlock(new Log()));
        tagged.setTagCompound(new NBTTagCompound());
        assertFalse(PillarMaterialPolicy.suitable(tagged));
        assertFalse(PillarMaterialPolicy.suitable(new ItemStack(new ItemBlock(new BlockFalling()))));
        assertFalse(PillarMaterialPolicy.suitable(new ItemStack(new Item())));
        assertFalse(PillarMaterialPolicy.suitable(new ItemStack(new ItemBlock(new Log() {

            @Override
            public boolean hasTileEntity(int meta) {
                return true;
            }
        }))));
    }

    @Test
    public void excludesAnItemWithUnsafeHotbarVariant() {
        Item item = new ItemBlock(new Log());
        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(item);
        inventory[1] = new ItemStack(item);
        inventory[1].setTagCompound(new NBTTagCompound());
        assertTrue(
            PillarMaterialPolicy.hotbarItems(inventory)
                .isEmpty());
    }

    @Test
    public void stagingPreservesHeldAndStagedToolSlots() {
        ItemStack[] inventory = new ItemStack[36];
        assertEquals(6, PillarMaterialStaging.stagingSlot(inventory, 8, 7));
        for (int i = 0; i < 9; i++) inventory[i] = new ItemStack(new Item());
        assertEquals(6, PillarMaterialStaging.stagingSlot(inventory, 8, 7));
    }
}
