package io.github.kaseyawolf2.horizonwright.core.inventory;

import java.util.Objects;

/** An address in an observed inventory, never a hotbar slot unless its endpoint is the player. */
public final class InventoryLocation {

    private final String endpointId;
    private final int slot;

    public InventoryLocation(String endpointId, int slot) {
        if (endpointId == null || endpointId.trim()
            .isEmpty() || slot < 0) {
            throw new IllegalArgumentException("An inventory location needs an endpoint and a non-negative slot");
        }
        this.endpointId = endpointId;
        this.slot = slot;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public int getSlot() {
        return slot;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof InventoryLocation)) return false;
        InventoryLocation that = (InventoryLocation) other;
        return slot == that.slot && endpointId.equals(that.endpointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointId, slot);
    }

    @Override
    public String toString() {
        return endpointId + ":" + slot;
    }
}
