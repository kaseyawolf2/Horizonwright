package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemSeedFood;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.forge.client.AutomaticInventory;
import io.github.kaseyawolf2.horizonwright.forge.client.PillarMaterialPolicy;
import io.github.kaseyawolf2.horizonwright.forge.client.ToolCapabilities;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.HusbandryTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeTask;

/** Derives working supplies from task semantics while retaining explicit profile reservations. */
final class TaskInventoryPolicy {

    private final TaskSpec task;
    private final MinecraftContainerSnapshotter fingerprints;
    private final Function<String, Item> blockItems;
    private final List<LoadoutReservation> reservations = new ArrayList<>();
    private final StorageItemFilter unloadFilter;

    TaskInventoryPolicy(TaskSpec task, ProfileEnvelope profile) {
        this(task, profile, new MinecraftContainerSnapshotter());
    }

    TaskInventoryPolicy(TaskSpec task, ProfileEnvelope profile, MinecraftContainerSnapshotter fingerprints) {
        this(task, profile, fingerprints, configured -> {
            Object block = configured == null ? null : Block.blockRegistry.getObject(configured);
            return block instanceof Block ? Item.getItemFromBlock((Block) block) : null;
        });
    }

    TaskInventoryPolicy(TaskSpec task, ProfileEnvelope profile, MinecraftContainerSnapshotter fingerprints,
        Function<String, Item> blockItems) {
        this.task = task;
        this.fingerprints = fingerprints;
        this.blockItems = blockItems;
        for (NamedLoadout loadout : profile.getNamedLoadouts()) {
            for (LoadoutReservation reservation : loadout.getReservations()) {
                if (!AutomaticInventory.ID.equals(loadout.getId())
                    || reservation.getRole() == LoadoutRole.REPAIR_MATERIAL) reservations.add(reservation);
            }
        }
        StorageItemFilter filter = null;
        String storage = task.getParameters()
            .get("storageId");
        for (NamedStorageEndpoint endpoint : profile.getNamedStorageEndpoints()) if (endpoint.getId()
            .equals(storage)) filter = endpoint.getDestinationFilter();
        unloadFilter = filter;
    }

    List<LoadoutReservation> reservations() {
        return new ArrayList<>(reservations);
    }

    /** Counts working supplies so spare tools and surplus reserved material can leave the player. */
    List<LoadoutReservation> workingReservations(ItemStack[] main) {
        List<LoadoutReservation> result = reservations();
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack stack : main) {
            int desired = stack == null ? 0
                : Math.max(
                    taskRequiredCount(stack),
                    AutomaticInventory.automaticReserveCount(
                        stack,
                        !ExcavationTask.TYPE.equals(task.getType())
                            && !io.github.kaseyawolf2.horizonwright.runtime.task.UnloadTask.isExcavationUnload(task)));
            if (desired <= 0) continue;
            if (isTool(stack)) candidates.add(stack);
            else reserveExact(result, stack, desired);
        }
        candidates.sort(
            Comparator.comparingInt(
                (ItemStack stack) -> ToolCapabilities.classes(stack)
                    .size())
                .reversed());
        List<ItemStack> retained = new ArrayList<>();
        for (ItemStack stack : candidates) {
            if (!RepairTask.TYPE.equals(task.getType()) && covers(retained, stack)) continue;
            retained.add(stack);
            reserveExact(result, stack, 1);
        }
        return result;
    }

    private void reserveExact(List<LoadoutReservation> result, ItemStack stack, int count) {
        ItemFingerprint item = fingerprints.fingerprint(stack);
        for (LoadoutReservation reservation : result) {
            if (reservation.matches(item) && reservation.getMinimumCount() >= count) return;
        }
        result.add(
            new LoadoutReservation(
                "working-" + result.size(),
                LoadoutRole.OTHER_RESERVED,
                item.getItemId(),
                item.getMetadata(),
                item.getDataHash(),
                count));
    }

    /** Hard protection; workingReservations delegates counted protections to the planner. */
    boolean protectedForPlanner(ItemStack stack) {
        if (stack == null) return true;
        if (stack.getItem() instanceof ItemArmor) return true;
        // New task types must opt into equipment inference before any of their tools may be packed.
        return isTool(stack) && (!hasToolPolicy() || RepairTask.TYPE.equals(task.getType()));
    }

    boolean stowForPlanner(ItemStack stack) {
        return hasToolPolicy() && stack != null
            && !protectedForPlanner(stack)
            && (stack.getMaxStackSize() > 1 || isTool(stack));
    }

    boolean protectedItem(ItemStack stack) {
        if (stack == null) return true;
        ItemFingerprint fingerprint = fingerprints.fingerprint(stack);
        for (LoadoutReservation reservation : reservations) if (reservation.matches(fingerprint)) return true;
        return protectedForPlanner(stack) || requiredCount(stack) > 0;
    }

    boolean unloadCargo(ItemStack stack) {
        return stack != null && stack.getMaxStackSize() > 1
            && !isTool(stack)
            && !(stack.getItem() instanceof ItemArmor)
            && !explicitlyReserved(stack)
            && unloadFilter != null
            && unloadFilter.accepts(fingerprints.fingerprint(stack));
    }

    private boolean explicitlyReserved(ItemStack stack) {
        ItemFingerprint item = fingerprints.fingerprint(stack);
        for (LoadoutReservation reservation : reservations) if (reservation.matches(item)) return true;
        return false;
    }

    boolean stow(ItemStack stack) {
        if (!hasToolPolicy() || stack == null || protectedItem(stack)) return false;
        // Keep unfamiliar singleton equipment in hand inventory; supported tools may be stowed.
        return stack.getMaxStackSize() > 1 || isTool(stack);
    }

    /** Native ME shift-click moves a whole stack; it must leave every working reserve satisfied. */
    boolean mayDepositWholeStack(ItemStack[] main, int source) {
        if (source < 0 || source >= main.length || !stowForPlanner(main[source])) return false;
        ItemFingerprint item = fingerprints.fingerprint(main[source]);
        for (LoadoutReservation reservation : workingReservations(main)) {
            if (!reservation.matches(item)) continue;
            int retained = 0;
            for (int slot = 0; slot < main.length; slot++) {
                if (slot != source && main[slot] != null && reservation.matches(fingerprints.fingerprint(main[slot])))
                    retained = Math.addExact(retained, main[slot].stackSize);
            }
            if (retained < reservation.getMinimumCount()) return false;
        }
        return true;
    }

    int requiredCount(ItemStack stack) {
        if (stack == null) return 0;
        int count = Math.max(
            taskRequiredCount(stack),
            AutomaticInventory.automaticReserveCount(
                stack,
                !ExcavationTask.TYPE.equals(task.getType())
                    && !io.github.kaseyawolf2.horizonwright.runtime.task.UnloadTask.isExcavationUnload(task)));
        ItemFingerprint item = fingerprints.fingerprint(stack);
        for (LoadoutReservation reservation : reservations) {
            if (reservation.matches(item)) count = Math.max(count, reservation.getMinimumCount());
        }
        return count;
    }

    private int taskRequiredCount(ItemStack stack) {
        if (stack == null) return 0;
        String type = task.getType();
        if (isTool(stack) && !usableTool(stack) && !RepairTask.TYPE.equals(type)) return 0;
        Set<String> classes = ToolCapabilities.classes(stack);
        if (TreeTask.TYPE.equals(type)) {
            if (PillarMaterialPolicy.suitable(stack)) return 16;
            if (classes.contains("axe")) return 1;
            if (stack.getItem() == Item.getItemFromBlock(Blocks.sapling)
                && stack.getItemDamage() == integer("plantSpecies", -1))
                return Math.min(64, Math.max(16, integer("minimumSaplingReserve", 0) + 1));
        }
        if (ExcavationTask.TYPE.equals(type)) {
            if (classes.contains("pickaxe") || classes.contains("shovel") || classes.contains("axe")) return 1;
            for (String parameter : new String[] { ExcavationTask.RAMP_MATERIAL, ExcavationTask.LIGHT_MATERIAL,
                ExcavationTask.FLUID_FILLER_MATERIAL }) {
                String configured = task.getParameters()
                    .get(parameter);
                if (configured != null && blockItems.apply(configured) == stack.getItem()) return 64;
            }
        }
        if (FarmTask.TYPE.equals(type)) {
            if (plantingSupply(stack)) return 64;
            if (classes.contains("shovel") || classes.contains("hoe") || spade(stack)) return 1;
        }
        if (HusbandryTask.TYPE.equals(type)) {
            if (stack.getItem() instanceof ItemSeeds || stack.getItem() instanceof ItemSeedFood
                || stack.getItem() == net.minecraft.init.Items.wheat
                || "minecraft:carrot".equals(
                    fingerprints.fingerprint(stack)
                        .getItemId()))
                return 64;
            if (Boolean.parseBoolean(
                task.getParameters()
                    .get("allowCulling"))
                && stack.getItem()
                    .getClass()
                    .getName()
                    .equals("gregtech.common.items.MetaGeneratedTool01")
                && (stack.getItemDamage() == 34 || stack.getItemDamage() == 36)) return 1;
        }
        if (RepairTask.TYPE.equals(type) && isTool(stack)) return 1;
        return 0;
    }

    /** Tools serving an already materialized class need not all occupy the working inventory. */
    boolean alreadyEquipped(ItemStack candidate, ItemStack[] main) {
        if (candidate == null) return false;
        ItemFingerprint item = fingerprints.fingerprint(candidate);
        boolean explicitlyReserved = false;
        for (LoadoutReservation reservation : reservations) {
            if (!reservation.matches(item)) continue;
            explicitlyReserved = true;
            int count = 0;
            for (ItemStack present : main) {
                if (present != null && reservation.matches(fingerprints.fingerprint(present)))
                    count = Math.addExact(count, present.stackSize);
            }
            if (count < reservation.getMinimumCount()) return false;
        }
        if (explicitlyReserved) return true;
        if (!isTool(candidate)) return false;
        List<ItemStack> present = new ArrayList<>();
        for (ItemStack stack : main) if (stack != null) present.add(stack);
        return covers(present, candidate);
    }

    private static boolean covers(List<ItemStack> retained, ItemStack candidate) {
        Set<String> classes = ToolCapabilities.classes(candidate);
        for (ItemStack present : retained) {
            if (!usableTool(present)) continue;
            if (!classes.isEmpty() && ToolCapabilities.classes(present)
                .containsAll(classes)) return true;
            if (present.getItem() == candidate.getItem() && present.getItemDamage() == candidate.getItemDamage())
                return true;
        }
        return false;
    }

    private boolean hasToolPolicy() {
        String type = task.getType();
        return TreeTask.TYPE.equals(type) || ExcavationTask.TYPE.equals(type)
            || FarmTask.TYPE.equals(type)
            || HusbandryTask.TYPE.equals(type)
            || RepairTask.TYPE.equals(type);
    }

    private boolean plantingSupply(ItemStack stack) {
        if (stack.getItem() instanceof ItemSeeds || stack.getItem() instanceof ItemSeedFood) return true;
        String id = fingerprints.fingerprint(stack)
            .getItemId();
        return "minecraft:carrot".equals(id) || "minecraft:potato".equals(id)
            || "minecraft:nether_wart".equals(id)
            || "minecraft:dye".equals(id) && stack.getItemDamage() == 3;
    }

    static boolean isTool(ItemStack stack) {
        return stack != null && (!ToolCapabilities.classes(stack)
            .isEmpty() || stack.getItem()
                .getClass()
                .getName()
                .equals("gregtech.common.items.MetaGeneratedTool01")
            || spade(stack));
    }

    /** Broken tools cannot satisfy an operational tool class; repair preparation still retains them in place. */
    static boolean usableTool(ItemStack stack) {
        return ToolCapabilities.usable(stack);
    }

    private static boolean spade(ItemStack stack) {
        String name = stack.getItem()
            .getClass()
            .getName();
        return name.equals("com.gtnewhorizon.cropsnh.items.tools.ItemSpade")
            || name.equals("com.gtnewhorizon.cropsnh.items.tools.ItemReinforcedSpade");
    }

    private int integer(String key, int fallback) {
        try {
            return Integer.parseInt(
                task.getParameters()
                    .get(key));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
