package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.ArrayList;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

/** Predicts ordinary pickup/place/return clicks, including a partial identical-stack merge. */
public final class InventoryTransferClicks {

    private InventoryTransferClicks() {}

    /** Exchanges a carrier and a hotbar stack using ordinary clicks, without the mode-2 shortcut. */
    public static List<VerifiedContainerClick> swap(String id, ContainerSnapshot before, int source, int destination,
        int sourceLimit, int destinationLimit) {
        if (before == null || before.getCursor() != null
            || source == destination
            || source < 0
            || destination < 0
            || source >= before.getSlots()
                .size()
            || destination >= before.getSlots()
                .size())
            throw new IllegalArgumentException("A distinct source/destination and empty cursor are required");
        ItemFingerprint first = before.getSlots()
            .get(source);
        ItemFingerprint second = before.getSlots()
            .get(destination);
        if (first == null && second == null) throw new IllegalArgumentException("There is no carrier to swap");
        if (first != null && first.getCount() > destinationLimit || second != null && second.getCount() > sourceLimit)
            throw new IllegalArgumentException("The swapped stacks do not fit their destination slots");
        if (first == null) return move(id, before, destination, source, sourceLimit);
        if (second == null) return move(id, before, source, destination, destinationLimit);
        // Identical full singleton carriers cannot merge; a stackable identical pair would merge on click two.
        if (sameItem(first, second) && (sourceLimit != 1 || destinationLimit != 1))
            throw new IllegalArgumentException("A carrier swap must not merge identical stackable items");
        List<VerifiedContainerClick> clicks = new ArrayList<>();
        ContainerSnapshot picked = replace(before, source, null, first);
        clicks.add(new VerifiedContainerClick(id + "-pick", source, 0, 0, before, picked));
        ContainerSnapshot exchanged = replace(picked, destination, first, second);
        clicks.add(new VerifiedContainerClick(id + "-exchange", destination, 0, 0, picked, exchanged));
        ContainerSnapshot returned = replace(exchanged, source, second, null);
        clicks.add(new VerifiedContainerClick(id + "-return", source, 0, 0, exchanged, returned));
        return clicks;
    }

    public static List<VerifiedContainerClick> move(String id, ContainerSnapshot before, int source, int destination,
        int destinationLimit) {
        return move(id, before, source, destination, destinationLimit, Integer.MAX_VALUE);
    }

    public static List<VerifiedContainerClick> move(String id, ContainerSnapshot before, int source, int destination,
        int destinationLimit, int requestedCount) {
        if (before == null || before.getCursor() != null
            || source == destination
            || source < 0
            || destination < 0
            || source >= before.getSlots()
                .size()
            || destination >= before.getSlots()
                .size())
            throw new IllegalArgumentException("A distinct source/destination and empty cursor are required");
        ItemFingerprint stack = before.getSlots()
            .get(source);
        ItemFingerprint target = before.getSlots()
            .get(destination);
        if (stack == null || target != null && !sameItem(stack, target))
            throw new IllegalArgumentException("Only an empty slot or an identical stack can receive items");
        int count = target == null ? 0 : target.getCount();
        int capacity = Math.min(stack.getCount(), destinationLimit - count);
        int moved = Math.min(capacity, requestedCount);
        if (moved <= 0) throw new IllegalArgumentException("The destination has no capacity");
        List<VerifiedContainerClick> clicks = new ArrayList<>();
        ContainerSnapshot picked = replace(before, source, null, stack);
        clicks.add(new VerifiedContainerClick(id + "-pick", source, 0, 0, before, picked));
        int left = stack.getCount() - moved;
        ContainerSnapshot placed = picked;
        if (moved == capacity) {
            placed = replace(picked, destination, withCount(stack, count + moved), withCount(stack, left));
            clicks.add(new VerifiedContainerClick(id + "-place", destination, 0, 0, picked, placed));
        } else {
            if (moved > 64) throw new IllegalArgumentException("A bounded transfer moves at most 64 individual items");
            for (int i = 1; i <= moved; i++) {
                ContainerSnapshot next = replace(
                    placed,
                    destination,
                    withCount(stack, count + i),
                    withCount(stack, stack.getCount() - i));
                clicks.add(new VerifiedContainerClick(id + "-place-" + i, destination, 1, 0, placed, next));
                placed = next;
            }
        }
        if (left > 0) {
            ContainerSnapshot returned = replace(placed, source, withCount(stack, left), null);
            clicks.add(new VerifiedContainerClick(id + "-return", source, 0, 0, placed, returned));
        }
        return clicks;
    }

    static boolean sameItem(ItemFingerprint a, ItemFingerprint b) {
        return a != null && b != null
            && a.getItemId()
                .equals(b.getItemId())
            && a.getMetadata() == b.getMetadata()
            && a.getDataHash()
                .equals(b.getDataHash());
    }

    private static ItemFingerprint withCount(ItemFingerprint item, int count) {
        return count == 0 ? null : new ItemFingerprint(item.getItemId(), item.getMetadata(), item.getDataHash(), count);
    }

    private static ContainerSnapshot replace(ContainerSnapshot before, int index, ItemFingerprint item,
        ItemFingerprint cursor) {
        List<ItemFingerprint> slots = new ArrayList<>(before.getSlots());
        slots.set(index, item);
        return new ContainerSnapshot(
            before.getWindowId(),
            before.getContainerType(),
            before.getSlotLayout(),
            Math.addExact(before.getRevision(), 1),
            slots,
            cursor);
    }
}
