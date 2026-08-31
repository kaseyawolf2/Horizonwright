package io.github.kaseyawolf2.horizonwright.forge.client.container;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.tileentity.TileEntity;

import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadResult;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Resolves unload policy from the exact active profile partition. */
public final class ProfileVanillaChestUnloadConfiguration implements LiveVanillaChestUnloadBackend.ConfigurationSource {

    private final Minecraft minecraft;
    private final HorizonwrightPersistenceStore store;
    private final WorldProfileIdentity identity;

    public ProfileVanillaChestUnloadConfiguration(Minecraft minecraft, HorizonwrightPersistenceStore store,
        WorldProfileIdentity identity) {
        if (minecraft == null || store == null || identity == null) {
            throw new IllegalArgumentException("minecraft, store, and identity are required");
        }
        this.minecraft = minecraft;
        this.store = store;
        this.identity = identity;
    }

    @Override
    public LiveVanillaChestUnloadBackend.Configuration resolve(String loadoutId, String storageId,
        ContainerChest chest) {
        if (!minecraft.func_152345_ab() || minecraft.theWorld == null || chest == null) {
            throw new IllegalStateException("a joined client thread with an open chest is required");
        }
        ProfileEnvelope profile = requireProfile();
        NamedLoadout resolvedLoadout = null;
        for (NamedLoadout loadout : profile.getNamedLoadouts()) {
            if (loadout.getId()
                .equals(loadoutId)) {
                resolvedLoadout = loadout;
                break;
            }
        }
        if (resolvedLoadout == null) {
            throw new IllegalStateException("profile has no named loadout '" + loadoutId + "'");
        }
        NamedStorageEndpoint endpoint = requireEndpoint(profile, storageId);
        NamedLocation location = requireLocation(profile, endpoint.getLocationId());
        if (minecraft.theWorld.provider == null
            || minecraft.theWorld.provider.dimensionId != location.getDimensionId()) {
            throw mismatch(storageId);
        }
        TileEntity tile = minecraft.theWorld.getTileEntity(location.getX(), location.getY(), location.getZ());
        if (!(tile instanceof IInventory)) {
            throw mismatch(storageId);
        }
        IInventory configured = (IInventory) tile;
        IInventory open = chest.getLowerChestInventory();
        if (open != configured && (!(open instanceof InventoryLargeChest)
            || !((InventoryLargeChest) open).isPartOfLargeChest(configured))) {
            throw mismatch(storageId);
        }
        return new LiveVanillaChestUnloadBackend.Configuration(resolvedLoadout, endpoint.getDestinationFilter());
    }

    private static NamedStorageEndpoint requireEndpoint(ProfileEnvelope profile, String storageId) {
        for (NamedStorageEndpoint endpoint : profile.getNamedStorageEndpoints()) {
            if (endpoint.getId()
                .equals(storageId)) {
                return endpoint;
            }
        }
        throw new IllegalStateException("profile has no named storage endpoint '" + storageId + "'");
    }

    private static NamedLocation requireLocation(ProfileEnvelope profile, String locationId) {
        for (NamedLocation location : profile.getNamedLocations()) {
            if (location.getId()
                .equals(locationId)) {
                return location;
            }
        }
        throw new IllegalStateException("profile has no named storage location '" + locationId + "'");
    }

    private static IllegalStateException mismatch(String storageId) {
        return new IllegalStateException("the open chest is not configured storage '" + storageId + "'");
    }

    private ProfileEnvelope requireProfile() {
        PersistenceLoadResult<ProfileEnvelope> loaded = store
            .loadProfile(store.pathsForProfile(identity.getProfileId()));
        if (!loaded.isLoaded()) {
            throw new IllegalStateException("active profile cannot be read: " + loaded.getDiagnostic());
        }
        ProfileEnvelope profile = loaded.getValue();
        if (!identity.equals(profile.getIdentity())) {
            throw new IllegalStateException("active profile identity changed while a container was open");
        }
        return profile;
    }
}
