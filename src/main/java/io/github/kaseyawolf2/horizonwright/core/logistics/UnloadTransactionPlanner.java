package io.github.kaseyawolf2.horizonwright.core.logistics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

/** Converts layout-adapter predictions into a reservation-checked, exact-snapshot transaction. */
public final class UnloadTransactionPlanner {

    private static final int QUICK_MOVE_MODE = 1;

    private UnloadTransactionPlanner() {}

    public static ContainerTransaction create(String transactionId, long actionEpoch, UnloadPlan plan,
        List<ItemFingerprint> playerSlots, List<UnloadClickPrediction> predictions) {
        if (plan == null || playerSlots == null || predictions == null || predictions.contains(null)) {
            throw new IllegalArgumentException("plan, playerSlots, and predictions are required");
        }
        if (!plan.mayStartTransaction()) {
            throw new IllegalArgumentException("an incomplete loadout cannot produce an unload transaction");
        }
        if (predictions.size() != plan.getUnloadableSlots()
            .size()) {
            throw new IllegalArgumentException("the container adapter must predict every approved unload slot");
        }

        Set<Integer> approved = new HashSet<>(plan.getUnloadableSlots());
        Set<Integer> observed = new HashSet<>();
        List<VerifiedContainerClick> clicks = new ArrayList<>();
        for (UnloadClickPrediction prediction : predictions) {
            int playerSlot = prediction.getPlayerSlot();
            if (!approved.contains(playerSlot) || !observed.add(playerSlot) || playerSlot >= playerSlots.size()) {
                throw new IllegalArgumentException("container adapter proposed an unapproved or duplicate player slot");
            }
            ItemFingerprint planned = playerSlots.get(playerSlot);
            VerifiedContainerClick click = prediction.getClick();
            if (planned == null || click.getClickMode() != QUICK_MOVE_MODE || click.getMouseButton() != 0) {
                throw new IllegalArgumentException(
                    "unload predictions must be left-button quick-moves of occupied slots");
            }
            validateSourceReduction(planned, click);
            clicks.add(click);
        }
        if (!observed.equals(approved)) {
            throw new IllegalArgumentException("container adapter predictions do not cover the exact unload plan");
        }
        return new ContainerTransaction(transactionId, actionEpoch, clicks);
    }

    private static void validateSourceReduction(ItemFingerprint planned, VerifiedContainerClick click) {
        ContainerSnapshot before = click.getExpectedBefore();
        ContainerSnapshot after = click.getExpectedAfter();
        int windowSlot = click.getSlot();
        if (windowSlot < 0 || windowSlot >= before.getSlots()
            .size()) {
            throw new IllegalArgumentException("unload prediction points outside the container layout");
        }
        ItemFingerprint beforeSource = before.getSlots()
            .get(windowSlot);
        ItemFingerprint afterSource = after.getSlots()
            .get(windowSlot);
        if (!planned.equals(beforeSource)) {
            throw new IllegalArgumentException("unload prediction source differs from the approved player stack");
        }
        if (afterSource != null
            && (!beforeSource.hasSameIdentity(afterSource) || afterSource.getCount() >= beforeSource.getCount())) {
            throw new IllegalArgumentException("unload prediction does not reduce the approved source stack");
        }
        if (before.getCursor() != null || after.getCursor() != null) {
            throw new IllegalArgumentException("quick-move unloading requires an empty cursor before and after");
        }
        if (!counts(before).equals(counts(after))) {
            throw new IllegalArgumentException("unload prediction must conserve every container item");
        }
    }

    private static Map<ItemIdentity, Long> counts(ContainerSnapshot snapshot) {
        Map<ItemIdentity, Long> result = new LinkedHashMap<>();
        for (ItemFingerprint item : snapshot.getSlots()) {
            if (item == null) {
                continue;
            }
            ItemIdentity identity = new ItemIdentity(item);
            long previous = result.containsKey(identity) ? result.get(identity) : 0L;
            long combined = previous + item.getCount();
            if (combined < previous) {
                throw new IllegalArgumentException("container item count overflow");
            }
            result.put(identity, combined);
        }
        return result;
    }

    private static final class ItemIdentity {

        private final String itemId;
        private final int metadata;
        private final String dataHash;

        private ItemIdentity(ItemFingerprint item) {
            itemId = item.getItemId();
            metadata = item.getMetadata();
            dataHash = item.getDataHash();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ItemIdentity)) {
                return false;
            }
            ItemIdentity that = (ItemIdentity) other;
            return metadata == that.metadata && itemId.equals(that.itemId) && dataHash.equals(that.dataHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, metadata, dataHash);
        }
    }
}
