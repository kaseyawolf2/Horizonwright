package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.forge.client.ToolCapabilities;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

/** Detects work that needs a bag, rather than treating every player-inventory edit as dirty. */
final class InventoryPreparationTracker {

    private final Snapshot checked;
    private final int checkedTick;
    private Snapshot previous;

    InventoryPreparationTracker(Snapshot checked, int checkedTick) {
        this.checked = checked;
        this.previous = checked;
        this.checkedTick = checkedTick;
    }

    boolean needsPreparation(Snapshot current, int tick) {
        if (!checked.carriers.equals(current.carriers)) return true;
        for (Map.Entry<String, Integer> supply : previous.supplyLevels.entrySet()) {
            if (current.supplyLevels.getOrDefault(supply.getKey(), 0) < supply.getValue()) return true;
        }
        // Refill/loot can replenish supplies between checks; remember those improvements so their
        // later depletion is detected, without reopening a bag merely because something was picked up.
        boolean spaceBecameTight = previous.freeSlots > 4 && current.freeSlots <= 4
            || previous.freeSlots > 0 && current.freeSlots == 0;
        previous = current;
        if (current.freeSlots > 4) return false;
        if (spaceBecameTight) return true;
        // A full or unsuitable bag must not cause a loop. Retry only after a meaningful new cargo batch.
        return tick - checkedTick >= 100 && current.cargoItems - checked.cargoItems >= 32;
    }

    static Snapshot observe(ItemStack[] main, ItemStack worn, TaskInventoryPolicy policy,
        MinecraftContainerSnapshotter fingerprints, Function<ItemStack, String> carrierKey) {
        Snapshot result = new Snapshot();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> desired = new HashMap<>();
        for (ItemStack stack : main) {
            if (stack == null) {
                result.freeSlots++;
                continue;
            }
            String carrier = carrierKey.apply(stack);
            if (carrier != null) {
                result.carriers.add(carrier);
                continue;
            }
            int reserve = policy.requiredCount(stack);
            if (reserve > 0 && TaskInventoryPolicy.isTool(stack)) {
                if (!TaskInventoryPolicy.usableTool(stack)) continue;
                java.util.Set<String> classes = ToolCapabilities.classes(stack);
                if (classes.isEmpty()) result.supplyLevels.put(
                    "tool:" + fingerprints.fingerprint(stack)
                        .getItemId(),
                    1);
                else for (String toolClass : classes) result.supplyLevels.put("tool-class:" + toolClass, 1);
            } else if (reserve > 0) {
                ItemFingerprint item = fingerprints.fingerprint(stack);
                String key = item.getItemId() + ":" + item.getMetadata() + "#" + item.getDataHash();
                counts.merge(key, stack.stackSize, Integer::sum);
                desired.merge(key, reserve, Math::max);
            }
            if (!TaskInventoryPolicy.isTool(stack) && policy.stowForPlanner(stack))
                result.cargoItems += stack.stackSize;
        }
        for (Map.Entry<String, Integer> supply : counts.entrySet()) {
            int lowWater = Math.max(1, Math.min(16, desired.get(supply.getKey()) / 4));
            result.supplyLevels.put(supply.getKey(), supply.getValue() <= lowWater ? 1 : 2);
        }
        if (worn != null) result.carriers.add("worn:" + carrierKey.apply(worn));
        Collections.sort(result.carriers);
        return result;
    }

    static final class Snapshot {

        final List<String> carriers = new ArrayList<>();
        final Map<String, Integer> supplyLevels = new HashMap<>();
        int freeSlots;
        int cargoItems;
    }
}
