package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryStack;

/** Immutable result of enumerating the normal player inventory on the client thread. */
public final class ClientInventorySnapshot {

    private final int slotCount;
    private final List<InventoryStack> stacks;
    private final InventoryManifest manifest;

    public ClientInventorySnapshot(int slotCount, List<InventoryStack> stacks) {
        if (stacks == null || stacks.contains(null)) {
            throw new IllegalArgumentException("stacks must not be null or contain null");
        }
        this.slotCount = slotCount;
        this.stacks = Collections.unmodifiableList(new ArrayList<>(stacks));
        manifest = new InventoryManifest(slotCount, this.stacks);
    }

    public int getSlotCount() {
        return slotCount;
    }

    public List<InventoryStack> getStacks() {
        return stacks;
    }

    public InventoryManifest toManifest() {
        return manifest;
    }
}
