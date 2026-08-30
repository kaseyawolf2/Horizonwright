package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationStatus;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryVerificationObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RespawnObservation;

/** Pure construction boundary from client/scanner snapshots to kernel observations. */
public final class RecoveryObservationFactory {

    private RecoveryObservationFactory() {}

    public static RespawnObservation respawn(String playerIdentity, double health, boolean dead, boolean worldLoaded,
        boolean normalInventoryContainer, DimensionBlockPosition playerPosition,
        ClientInventorySnapshot inventorySnapshot) {
        requireInventory(inventorySnapshot);
        return new RespawnObservation(
            playerIdentity,
            health,
            dead,
            worldLoaded,
            normalInventoryContainer,
            playerPosition,
            inventorySnapshot.toManifest());
    }

    public static RecoveryNavigationObservation navigation(RecoveryNavigationStatus status,
        DimensionBlockPosition playerPosition, ClientInventorySnapshot inventorySnapshot,
        boolean genericInteractionsEnabled, boolean graveTargetedByRouteAction) {
        requireInventory(inventorySnapshot);
        return new RecoveryNavigationObservation(
            status,
            playerPosition,
            inventorySnapshot.toManifest(),
            genericInteractionsEnabled,
            graveTargetedByRouteAction);
    }

    public static GraveSearchObservation graveSearch(GraveRegionScan scan, ClientInventorySnapshot inventorySnapshot,
        boolean emptyHotbarHandAvailable) {
        if (scan == null) {
            throw new IllegalArgumentException("scan must not be null");
        }
        requireInventory(inventorySnapshot);
        return new GraveSearchObservation(
            scan.getStatus(),
            scan.getCandidates(),
            inventorySnapshot.toManifest(),
            emptyHotbarHandAvailable);
    }

    public static RecoveryVerificationObservation verification(GraveInspection inspection,
        ClientInventorySnapshot inventorySnapshot) {
        if (inspection == null) {
            throw new IllegalArgumentException("inspection must not be null");
        }
        requireInventory(inventorySnapshot);
        return new RecoveryVerificationObservation(
            inspection.getResolution(),
            inspection.getCandidate(),
            inventorySnapshot.toManifest());
    }

    private static InventoryManifest requireInventory(ClientInventorySnapshot inventorySnapshot) {
        if (inventorySnapshot == null) {
            throw new IllegalArgumentException("inventorySnapshot must not be null");
        }
        return inventorySnapshot.toManifest();
    }
}
