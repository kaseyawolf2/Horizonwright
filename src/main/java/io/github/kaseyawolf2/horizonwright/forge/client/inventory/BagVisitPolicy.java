package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

/** A negative decision is valid only when the caller has current, complete bag item data. */
final class BagVisitPolicy {

    private BagVisitPolicy() {}

    static boolean needsVisit(List<ItemStack> contents, ItemStack[] main, TaskInventoryPolicy policy, boolean unloading,
        Predicate<ItemStack> cargo, Predicate<ItemStack> accepts, MinecraftContainerSnapshotter fingerprints) {
        for (ItemStack stored : contents) {
            if (unloading) {
                if (cargo.test(stored)) return true;
            } else if (policy.requiredCount(stored) > count(main, fingerprints.fingerprint(stored), fingerprints)
                && !policy.alreadyEquipped(stored, main)) return true;
        }
        if (unloading) return false;
        List<LoadoutReservation> reservations = policy.workingReservations(main);
        for (ItemStack item : main) {
            if (item == null || !policy.stowForPlanner(item) || !accepts.test(item)) continue;
            ItemFingerprint fingerprint = fingerprints.fingerprint(item);
            int keep = 0;
            for (LoadoutReservation reserve : reservations)
                if (reserve.matches(fingerprint)) keep = Math.max(keep, reserve.getMinimumCount());
            if (count(main, fingerprint, fingerprints) > keep) return true;
        }
        return false;
    }

    private static int count(ItemStack[] main, ItemFingerprint item, MinecraftContainerSnapshotter fingerprints) {
        int result = 0;
        for (ItemStack stack : main) if (stack != null && item.hasSameIdentity(fingerprints.fingerprint(stack)))
            result = Math.addExact(result, stack.stackSize);
        return result;
    }
}
