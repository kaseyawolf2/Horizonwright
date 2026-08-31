package io.github.kaseyawolf2.horizonwright.core.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;

/** Selects only whole, non-reserved player stacks for a later verified container transaction. */
public final class UnloadPlanner {

    private UnloadPlanner() {}

    public static UnloadPlan plan(NamedLoadout loadout, List<ItemFingerprint> playerSlots) {
        return plan(loadout, playerSlots, StorageItemFilter.acceptAll());
    }

    public static UnloadPlan plan(NamedLoadout loadout, List<ItemFingerprint> playerSlots,
        StorageItemFilter destinationFilter) {
        if (loadout == null || playerSlots == null || destinationFilter == null) {
            throw new IllegalArgumentException("loadout, playerSlots, and destinationFilter must not be null");
        }
        loadout.validate();
        Set<Integer> reserved = new LinkedHashSet<>();
        Map<String, Integer> missing = new LinkedHashMap<>();

        for (LoadoutReservation reservation : loadout.getReservations()) {
            int retained = 0;
            for (int slot = 0; slot < playerSlots.size() && retained < reservation.getMinimumCount(); slot++) {
                ItemFingerprint item = playerSlots.get(slot);
                if (reservation.matches(item)) {
                    reserved.add(slot);
                    retained = addWithoutOverflow(retained, item.getCount());
                }
            }
            if (retained < reservation.getMinimumCount()) {
                missing.put(reservation.getId(), reservation.getMinimumCount() - retained);
            }
        }

        if (!missing.isEmpty()) {
            return new UnloadPlan(
                UnloadPlanStatus.LOADOUT_INCOMPLETE,
                new ArrayList<>(reserved),
                Collections.<Integer>emptyList(),
                Collections.<Integer>emptyList(),
                missing);
        }

        List<Integer> unloadable = new ArrayList<>();
        List<Integer> deferred = new ArrayList<>();
        for (int slot = 0; slot < playerSlots.size(); slot++) {
            if (playerSlots.get(slot) != null && !reserved.contains(slot)) {
                if (destinationFilter.accepts(playerSlots.get(slot))) {
                    unloadable.add(slot);
                } else {
                    deferred.add(slot);
                }
            }
        }
        return new UnloadPlan(
            UnloadPlanStatus.READY,
            new ArrayList<>(reserved),
            unloadable,
            deferred,
            Collections.<String, Integer>emptyMap());
    }

    private static int addWithoutOverflow(int left, int right) {
        if (Integer.MAX_VALUE - left < right) {
            return Integer.MAX_VALUE;
        }
        return left + right;
    }
}
