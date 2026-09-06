package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;

/** GregTech 5 knife integration; probes copies because getToolStats refreshes enchantment NBT. */
final class GregTechCullingKnife {

    private GregTechCullingKnife() {}

    static Candidate inspect(ItemStack stack, int slot) {
        if (stack == null || stack.stackSize != 1) return null;
        if (!"gregtech.common.items.MetaGeneratedTool01".equals(
            stack.getItem()
                .getClass()
                .getName()))
            return null;
        try {
            Class<?> base = Class.forName("gregtech.api.items.MetaGeneratedTool");
            if (!base.isInstance(stack.getItem())) return null;
            ItemStack copy = stack.copy();
            Object stats = base.getMethod("getToolStats", ItemStack.class)
                .invoke(copy.getItem(), copy);
            if (stats == null || !isKnife(
                stats.getClass()
                    .getName(),
                copy.getItemDamage())) return null;
            long maximum = ((Number) base.getMethod("getToolMaxDamage", ItemStack.class)
                .invoke(null, copy)).longValue();
            long damage = ((Number) base.getMethod("getToolDamage", ItemStack.class)
                .invoke(null, copy)).longValue();
            int cost = ((Number) stats.getClass()
                .getMethod("getToolDamagePerEntityAttack")
                .invoke(stats)).intValue();
            if (damage < 0 || maximum <= damage || cost <= 0 || maximum - damage <= cost) return null;
            int looting = EnchantmentHelper.getEnchantmentLevel(Enchantment.looting.effectId, copy);
            return new Candidate(slot, looting, maximum - damage);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("GregTech knife integration unavailable", failure);
        }
    }

    static boolean isKnife(String statsClass, int metadata) {
        return (metadata == 34 || metadata == 36) && ("gregtech.common.tools.ToolKnife".equals(statsClass)
            || "gregtech.common.tools.ToolButcheryKnife".equals(statsClass));
    }

    static Candidate best(ItemStack[] inventory) {
        Candidate best = null;
        for (int slot = 0; slot < Math.min(36, inventory.length); slot++) {
            Candidate candidate = inspect(inventory[slot], slot);
            if (candidate != null && candidate.preferredTo(best)) best = candidate;
        }
        return best;
    }

    static final class Candidate {

        final int slot;
        final int looting;
        final long remaining;

        Candidate(int slot, int looting, long remaining) {
            this.slot = slot;
            this.looting = looting;
            this.remaining = remaining;
        }

        boolean preferredTo(Candidate other) {
            return other == null || looting > other.looting || looting == other.looting && remaining > other.remaining;
        }
    }
}
