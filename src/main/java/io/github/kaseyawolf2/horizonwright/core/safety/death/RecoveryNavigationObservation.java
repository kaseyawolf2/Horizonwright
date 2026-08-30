package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** One deterministic observation from the recovery navigation adapter. */
public final class RecoveryNavigationObservation {

    private final RecoveryNavigationStatus status;
    private final DimensionBlockPosition playerPosition;
    private final InventoryManifest inventory;
    private final boolean genericInteractionsEnabled;
    private final boolean graveTargetedByRouteAction;

    public RecoveryNavigationObservation(RecoveryNavigationStatus status, DimensionBlockPosition playerPosition,
        InventoryManifest inventory, boolean genericInteractionsEnabled, boolean graveTargetedByRouteAction) {
        if (status == null || playerPosition == null || inventory == null) {
            throw new IllegalArgumentException("status, playerPosition, and inventory must not be null");
        }
        this.status = status;
        this.playerPosition = playerPosition;
        this.inventory = inventory;
        this.genericInteractionsEnabled = genericInteractionsEnabled;
        this.graveTargetedByRouteAction = graveTargetedByRouteAction;
    }

    public RecoveryNavigationStatus getStatus() {
        return status;
    }

    public DimensionBlockPosition getPlayerPosition() {
        return playerPosition;
    }

    public InventoryManifest getInventory() {
        return inventory;
    }

    public boolean isGenericInteractionsEnabled() {
        return genericInteractionsEnabled;
    }

    public boolean isGraveTargetedByRouteAction() {
        return graveTargetedByRouteAction;
    }
}
