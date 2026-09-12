package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;

/** Forge classes plus the exact TConstruct tool implementations shipped by the pack. */
public final class ToolCapabilities {

    private ToolCapabilities() {}

    public static Set<String> classes(ItemStack stack) {
        Set<String> result = new HashSet<>();
        if (stack == null) return result;
        result.addAll(
            stack.getItem()
                .getToolClasses(stack.copy()));
        addKnownClasses(
            result,
            stack.getItem()
                .getClass()
                .getName());
        return result;
    }

    static void addKnownClasses(Set<String> result, String implementation) {
        switch (implementation) {
            case "tconstruct.items.tools.Hatchet":
            case "tconstruct.items.tools.LumberAxe":
            case "tconstruct.items.tools.Battleaxe":
                result.add("axe");
                break;
            case "tconstruct.items.tools.Mattock":
                result.add("axe");
                result.add("shovel");
                break;
            case "tconstruct.items.tools.Pickaxe":
            case "tconstruct.items.tools.Hammer":
                result.add("pickaxe");
                break;
            case "tconstruct.items.tools.Shovel":
            case "tconstruct.items.tools.Excavator":
                result.add("shovel");
                break;
            default:
                break;
        }
    }

    public static boolean usable(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return false;
        if (stack.isItemStackDamageable() && stack.getItemDamage() >= stack.getMaxDamage()) return false;
        return !stack.hasTagCompound() || !stack.getTagCompound()
            .getCompoundTag("InfiTool")
            .getBoolean("Broken");
    }
}
