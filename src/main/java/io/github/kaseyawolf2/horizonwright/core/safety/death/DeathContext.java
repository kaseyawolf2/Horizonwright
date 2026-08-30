package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Optional;

/** Evidence captured synchronously when death is first suspected. */
public final class DeathContext {

    private final DimensionBlockPosition deathPosition;
    private final String oldPlayerIdentity;
    private final String activeTaskId;
    private final InventoryManifest preDeathInventory;

    public DeathContext(DimensionBlockPosition deathPosition, String oldPlayerIdentity, String activeTaskId,
        InventoryManifest preDeathInventory) {
        if (deathPosition == null) {
            throw new IllegalArgumentException("deathPosition must not be null");
        }
        if (preDeathInventory == null) {
            throw new IllegalArgumentException("preDeathInventory must not be null");
        }
        this.deathPosition = deathPosition;
        this.oldPlayerIdentity = ConnectionIdentity.requireText(oldPlayerIdentity, "oldPlayerIdentity");
        this.activeTaskId = activeTaskId == null || activeTaskId.trim()
            .isEmpty() ? null : activeTaskId.trim();
        this.preDeathInventory = preDeathInventory;
    }

    public DimensionBlockPosition getDeathPosition() {
        return deathPosition;
    }

    public String getOldPlayerIdentity() {
        return oldPlayerIdentity;
    }

    public Optional<String> getActiveTaskId() {
        return Optional.ofNullable(activeTaskId);
    }

    public InventoryManifest getPreDeathInventory() {
        return preDeathInventory;
    }
}
