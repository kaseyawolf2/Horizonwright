package io.github.kaseyawolf2.horizonwright.core.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One adapter's current observations; a terminal is available only after access has been established. */
public final class InventoryEndpoint {

    public enum Kind {
        PLAYER,
        PORTABLE,
        NETWORK
    }

    private final String id;
    private final Kind kind;
    private final boolean available;
    private final boolean readable;
    private final boolean writable;
    private final InventoryLocation carrierLocation;
    private final List<InventorySlot> slots;

    public InventoryEndpoint(String id, Kind kind, boolean available, boolean readable, boolean writable,
        InventoryLocation carrierLocation, List<InventorySlot> slots) {
        if (id == null || id.trim()
            .isEmpty()) throw new IllegalArgumentException("Endpoint id must not be blank");
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.available = available;
        this.readable = readable;
        this.writable = writable;
        this.carrierLocation = carrierLocation;
        Set<Integer> indices = new HashSet<>();
        for (InventorySlot slot : slots) {
            if (!indices.add(slot.getIndex())) throw new IllegalArgumentException("Duplicate slot in " + id);
        }
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public String getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public InventoryLocation getCarrierLocation() {
        return carrierLocation;
    }

    public List<InventorySlot> getSlots() {
        return slots;
    }
}
