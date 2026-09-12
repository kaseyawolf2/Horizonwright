package io.github.kaseyawolf2.horizonwright.core.container;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Allows storage extraction and incidental pickups without accepting missing player items. */
final class StorageExtractionSnapshot {

    private StorageExtractionSnapshot() {}

    static boolean matches(ContainerSnapshot expected, ContainerSnapshot observed, int storageSlots, int sourceSlot,
        boolean afterClick) {
        if (!expected.sameIdentityAndLayout(observed) || expected.getRevision() != observed.getRevision()
            || !Objects.equals(expected.getCursor(), observed.getCursor())) return false;
        for (int slot = storageSlots; slot < expected.getSlots()
            .size(); slot++) {
            ItemFingerprint planned = expected.getSlots()
                .get(slot);
            ItemFingerprint actual = observed.getSlots()
                .get(slot);
            if (Objects.equals(planned, actual)) continue;
            if (slot != sourceSlot) {
                // Nearby drops can fill an empty slot or add to an existing stack while
                // the chest is open. The next single-click plan includes that new cargo.
                if (planned == null
                    || (actual != null && planned.hasSameIdentity(actual) && actual.getCount() >= planned.getCount()))
                    continue;
                return false;
            }
            // Extraction can free extra capacity between planning and the server click,
            // causing more of a partially unloadable source stack to move.
            if (!afterClick || planned == null
                || (actual != null && (!planned.hasSameIdentity(actual) || actual.getCount() > planned.getCount())))
                return false;
        }
        Map<ItemFingerprint, Long> available = new HashMap<>();
        for (int slot = 0; slot < storageSlots; slot++) {
            ItemFingerprint item = expected.getSlots()
                .get(slot);
            if (item != null) available.merge(identity(item), (long) item.getCount(), Long::sum);
        }
        if (afterClick) {
            ItemFingerprint planned = expected.getSlots()
                .get(sourceSlot);
            ItemFingerprint actual = observed.getSlots()
                .get(sourceSlot);
            if (planned != null) {
                long extra = planned.getCount() - (actual == null ? 0 : actual.getCount());
                available.merge(identity(planned), extra, Long::sum);
            }
        }
        for (int slot = 0; slot < storageSlots; slot++) {
            ItemFingerprint item = observed.getSlots()
                .get(slot);
            if (item == null) continue;
            ItemFingerprint key = identity(item);
            long remaining = available.getOrDefault(key, 0L) - item.getCount();
            if (remaining < 0L) return false;
            available.put(key, remaining);
        }
        return true;
    }

    private static ItemFingerprint identity(ItemFingerprint item) {
        return new ItemFingerprint(item.getItemId(), item.getMetadata(), item.getDataHash(), 1);
    }
}
