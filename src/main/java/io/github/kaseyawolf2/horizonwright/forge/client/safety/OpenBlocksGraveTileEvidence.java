package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;

/** Client-visible, immutable evidence from one exact OpenBlocks grave tile. */
public final class OpenBlocksGraveTileEvidence {

    private final GraveIdentity identity;
    private final String ownerUsername;
    private final boolean inventoryEmpty;

    OpenBlocksGraveTileEvidence(GraveIdentity identity, String ownerUsername, boolean inventoryEmpty) {
        if (identity == null || ownerUsername == null
            || ownerUsername.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("grave identity and owner username must not be empty");
        }
        this.identity = identity;
        this.ownerUsername = ownerUsername;
        this.inventoryEmpty = inventoryEmpty;
    }

    public GraveIdentity getIdentity() {
        return identity;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public boolean isInventoryEmpty() {
        return inventoryEmpty;
    }
}
