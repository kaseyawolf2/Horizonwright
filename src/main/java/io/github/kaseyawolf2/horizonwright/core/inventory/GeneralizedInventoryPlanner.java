package io.github.kaseyawolf2.horizonwright.core.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;

/**
 * Adapter-neutral planning from observations. Plans neither open inventories nor mutate them. Executors must revalidate
 * availability, filters and each move's expected stacks before transferring through the adapter's supported operations.
 */
public final class GeneralizedInventoryPlanner {

    public enum Purpose {
        STOW,
        RETRIEVE
    }

    /** The expectations describe the sequential plan, including any earlier moves in that plan. */
    public static final class Move {

        private final InventoryLocation source;
        private final InventoryLocation destination;
        private final ItemFingerprint expectedSource;
        private final ItemFingerprint expectedDestination;
        private final int count;
        private final Purpose purpose;

        private Move(StateSlot source, StateSlot destination, int count, Purpose purpose) {
            this.source = source.location;
            this.destination = destination.location;
            this.expectedSource = source.item;
            this.expectedDestination = destination.item;
            this.count = count;
            this.purpose = purpose;
        }

        public InventoryLocation getSource() {
            return source;
        }

        public InventoryLocation getDestination() {
            return destination;
        }

        public ItemFingerprint getExpectedSource() {
            return expectedSource;
        }

        public ItemFingerprint getExpectedDestination() {
            return expectedDestination;
        }

        public int getCount() {
            return count;
        }

        public Purpose getPurpose() {
            return purpose;
        }
    }

    public static final class MissingRequirement {

        private final LoadoutReservation reservation;
        private final int missingCount;

        private MissingRequirement(LoadoutReservation reservation, int missingCount) {
            this.reservation = reservation;
            this.missingCount = missingCount;
        }

        public LoadoutReservation getReservation() {
            return reservation;
        }

        public int getMissingCount() {
            return missingCount;
        }
    }

    public static final class Plan {

        private final List<Move> moves;
        private final List<MissingRequirement> missingRequirements;

        private Plan(List<Move> moves, List<MissingRequirement> missingRequirements) {
            this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
            this.missingRequirements = Collections.unmodifiableList(new ArrayList<>(missingRequirements));
        }

        public List<Move> getMoves() {
            return moves;
        }

        /** Requirements still absent from the player after the plan; external ownership alone never satisfies use. */
        public List<MissingRequirement> getMissingRequirements() {
            return missingRequirements;
        }

        public boolean isReadyForTask() {
            return missingRequirements.isEmpty();
        }
    }

    /**
     * Keeps required quantities and protected stacks in the player, stows eligible surplus, then retrieves missing task
     * items into player slots. Carrier locations are always protected, including carriers of currently unavailable
     * bags.
     */
    public Plan plan(List<InventoryEndpoint> endpoints, List<LoadoutReservation> requiredInPlayer,
        Predicate<ItemFingerprint> keepInPlayer, Predicate<ItemFingerprint> mayStore) {
        Objects.requireNonNull(keepInPlayer, "keepInPlayer");
        Objects.requireNonNull(mayStore, "mayStore");
        List<StateSlot> slots = snapshot(endpoints);
        Set<InventoryLocation> carriers = carriers(endpoints);
        List<Move> moves = new ArrayList<>();
        for (StateSlot slot : slots) {
            if (slot.isPlayer() && slot.item != null && keepInPlayer.test(slot.item)) {
                slot.reserved = slot.item.getCount();
            }
        }
        for (LoadoutReservation requirement : requiredInPlayer) {
            int remaining = requirement.getMinimumCount();
            for (StateSlot slot : slots) {
                if (!slot.isPlayer() || !slot.isReadable() || !requirement.matches(slot.item)) continue;
                int kept = Math.min(remaining, slot.item.getCount());
                slot.reserved = Math.max(slot.reserved, kept);
                remaining -= kept;
                if (remaining == 0) break;
            }
        }
        for (StateSlot source : slots) {
            if (!source.isPlayer() || !source.canExtract()
                || carriers.contains(source.location)
                || !mayStore.test(source.item)) continue;
            int surplus = source.item.getCount() - source.reserved;
            transferTo(slots, source, surplus, false, carriers, moves, Purpose.STOW);
        }
        List<MissingRequirement> missing = new ArrayList<>();
        for (LoadoutReservation requirement : requiredInPlayer) {
            int needed = Math.max(0, requirement.getMinimumCount() - countPlayer(slots, requirement));
            for (StateSlot source : slots) {
                if (needed == 0) break;
                if (source.isPlayer() || !source.canExtract()
                    || carriers.contains(source.location)
                    || !requirement.matches(source.item)) continue;
                needed -= transferTo(slots, source, needed, true, carriers, moves, Purpose.RETRIEVE);
            }
            if (needed > 0) missing.add(new MissingRequirement(requirement, needed));
        }
        return new Plan(moves, missing);
    }

    /** Counts only currently inspectable and extractable items, never cached contents of an unavailable endpoint. */
    public int availableCount(List<InventoryEndpoint> endpoints, Predicate<ItemFingerprint> matches) {
        int count = 0;
        for (StateSlot slot : snapshot(endpoints)) {
            if (slot.canExtract() && matches.test(slot.item)) count = Math.addExact(count, slot.item.getCount());
        }
        return count;
    }

    /** Capacity for this exact item, with access, insertion filters, NBT identity and carrier protection applied. */
    public int availableCapacity(List<InventoryEndpoint> endpoints, ItemFingerprint item, boolean player) {
        int count = 0;
        Set<InventoryLocation> carriers = carriers(endpoints);
        for (StateSlot slot : snapshot(endpoints)) {
            if (slot.isPlayer() == player && !carriers.contains(slot.location)) {
                count = Math.addExact(count, slot.freeCapacity(item));
            }
        }
        return count;
    }

    private static int transferTo(List<StateSlot> slots, StateSlot source, int requested, boolean player,
        Set<InventoryLocation> carriers, List<Move> moves, Purpose purpose) {
        if (requested <= 0) return 0;
        List<StateSlot> destinations = new ArrayList<>();
        for (StateSlot slot : slots) {
            if (slot.isPlayer() == player && !carriers.contains(slot.location) && slot.freeCapacity(source.item) > 0)
                destinations.add(slot);
        }
        destinations.sort(
            Comparator.comparingInt((StateSlot slot) -> slot.slot.getRestrictionPriority())
                .reversed()
                .thenComparingInt(slot -> slot.item == null ? 1 : 0)
                .thenComparing(slot -> slot.location.getEndpointId())
                .thenComparingInt(slot -> slot.location.getSlot()));
        int remaining = Math.min(requested, source.item.getCount());
        int initial = remaining;
        for (StateSlot destination : destinations) {
            if (remaining == 0) break;
            int count = Math.min(remaining, destination.freeCapacity(source.item));
            if (count == 0) continue;
            moves.add(new Move(source, destination, count, purpose));
            destination.item = withCount(
                source.item,
                count + (destination.item == null ? 0 : destination.item.getCount()));
            source.item = withCount(source.item, source.item.getCount() - count);
            remaining -= count;
        }
        return initial - remaining;
    }

    private static int countPlayer(List<StateSlot> slots, LoadoutReservation requirement) {
        int count = 0;
        for (StateSlot slot : slots) {
            if (slot.isPlayer() && slot.isReadable() && requirement.matches(slot.item)) {
                count = Math.addExact(count, slot.item.getCount());
            }
        }
        return count;
    }

    private static List<StateSlot> snapshot(List<InventoryEndpoint> endpoints) {
        List<StateSlot> slots = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (InventoryEndpoint endpoint : endpoints) {
            if (!ids.add(endpoint.getId())) throw new IllegalArgumentException("Duplicate inventory endpoint id");
            for (InventorySlot slot : endpoint.getSlots()) slots.add(new StateSlot(endpoint, slot));
        }
        slots.sort(
            Comparator.comparing((StateSlot slot) -> slot.location.getEndpointId())
                .thenComparingInt(slot -> slot.location.getSlot()));
        return slots;
    }

    private static Set<InventoryLocation> carriers(List<InventoryEndpoint> endpoints) {
        Set<InventoryLocation> carriers = new HashSet<>();
        for (InventoryEndpoint endpoint : endpoints) {
            if (endpoint.getCarrierLocation() != null) carriers.add(endpoint.getCarrierLocation());
        }
        return carriers;
    }

    private static ItemFingerprint withCount(ItemFingerprint item, int count) {
        return count == 0 ? null : new ItemFingerprint(item.getItemId(), item.getMetadata(), item.getDataHash(), count);
    }

    private static final class StateSlot {

        private final InventoryEndpoint endpoint;
        private final InventorySlot slot;
        private final InventoryLocation location;
        private ItemFingerprint item;
        private int reserved;

        private StateSlot(InventoryEndpoint endpoint, InventorySlot slot) {
            this.endpoint = endpoint;
            this.slot = slot;
            this.location = new InventoryLocation(endpoint.getId(), slot.getIndex());
            this.item = slot.getItem();
        }

        private boolean isPlayer() {
            return endpoint.getKind() == InventoryEndpoint.Kind.PLAYER;
        }

        private boolean isReadable() {
            return endpoint.isAvailable() && endpoint.isReadable();
        }

        private boolean canExtract() {
            return isReadable() && endpoint.isWritable() && slot.isExtractable() && item != null;
        }

        private int freeCapacity(ItemFingerprint value) {
            if (!isReadable() || !endpoint.isWritable() || (item != null && !item.hasSameIdentity(value))) return 0;
            return Math.max(0, slot.capacityFor(value) - (item == null ? 0 : item.getCount()));
        }
    }
}
