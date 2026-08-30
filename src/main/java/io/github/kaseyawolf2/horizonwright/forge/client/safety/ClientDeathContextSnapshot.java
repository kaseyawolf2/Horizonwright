package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathContext;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;

/** Immutable, connection-bound evidence captured before any network-thread death decision. */
public final class ClientDeathContextSnapshot {

    private final long connectionEpoch;
    private final long capturedAtClientTick;
    private final DimensionBlockPosition playerPosition;
    private final String playerIdentity;
    private final String activeTaskId;
    private final InventoryManifest inventory;

    public ClientDeathContextSnapshot(long connectionEpoch, long capturedAtClientTick,
        DimensionBlockPosition playerPosition, String playerIdentity, String activeTaskId,
        ClientInventorySnapshot inventory) {
        if (connectionEpoch <= 0L || capturedAtClientTick < 0L) {
            throw new IllegalArgumentException("connection epoch must be positive and client tick non-negative");
        }
        if (playerPosition == null || inventory == null) {
            throw new IllegalArgumentException("player position and inventory must not be null");
        }
        if (playerIdentity == null || playerIdentity.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("player identity must not be blank");
        }
        this.connectionEpoch = connectionEpoch;
        this.capturedAtClientTick = capturedAtClientTick;
        this.playerPosition = playerPosition;
        this.playerIdentity = playerIdentity.trim();
        this.activeTaskId = activeTaskId == null || activeTaskId.trim()
            .isEmpty() ? null : activeTaskId.trim();
        this.inventory = inventory.toManifest();
    }

    public long getConnectionEpoch() {
        return connectionEpoch;
    }

    public long getCapturedAtClientTick() {
        return capturedAtClientTick;
    }

    public DimensionBlockPosition getPlayerPosition() {
        return playerPosition;
    }

    public String getPlayerIdentity() {
        return playerIdentity;
    }

    public String getActiveTaskId() {
        return activeTaskId;
    }

    public InventoryManifest getInventory() {
        return inventory;
    }

    public DeathContext toDeathContext() {
        return new DeathContext(playerPosition, playerIdentity, activeTaskId, inventory);
    }
}
