package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLog;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Ordinary solid scaffold supplies; does not consume machines, ores, plants or gravity blocks. */
public final class PillarMaterialPolicy {

    private PillarMaterialPolicy() {}

    public static boolean suitable(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock) || stack.hasTagCompound())
            return false;
        Block block = ((ItemBlock) stack.getItem()).field_150939_a;
        int meta = stack.getItem()
            .getMetadata(stack.getItemDamage());
        if (block == null || block instanceof BlockFalling
            || block.hasTileEntity(meta)
            || !block.isOpaqueCube()
            || !block.renderAsNormalBlock()) return false;
        return block instanceof BlockLog || block == Blocks.log
            || block == Blocks.log2
            || block == Blocks.planks
            || block == Blocks.dirt
            || block == Blocks.cobblestone
            || block == Blocks.stone
            || block == Blocks.netherrack
            || block == Blocks.stonebrick
            || block == Blocks.brick_block
            || block == Blocks.sandstone
            || block == Blocks.hardened_clay
            || block == Blocks.stained_hardened_clay;
    }

    public static int findSlot(ItemStack[] inventory, int excludedSource) {
        for (int slot = 0; slot < Math.min(36, inventory.length); slot++)
            if (slot != excludedSource && suitable(inventory[slot])) return slot;
        return -1;
    }

    public static List<Item> hotbarItems(ItemStack[] inventory) {
        List<Item> items = new ArrayList<>();
        for (int slot = 0; slot < Math.min(9, inventory.length); slot++) {
            ItemStack stack = inventory[slot];
            if (!suitable(stack) || items.contains(stack.getItem())) continue;
            boolean safe = true;
            for (int other = 0; other < Math.min(9, inventory.length); other++)
                if (inventory[other] != null && inventory[other].getItem() == stack.getItem()
                    && !suitable(inventory[other])) safe = false;
            if (safe) items.add(stack.getItem());
        }
        return items;
    }
}
