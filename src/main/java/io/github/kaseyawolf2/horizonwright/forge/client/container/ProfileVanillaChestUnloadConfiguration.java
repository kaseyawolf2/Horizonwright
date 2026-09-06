package io.github.kaseyawolf2.horizonwright.forge.client.container;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
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
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;

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
    public LiveVanillaChestUnloadBackend.Configuration resolve(String loadoutId, String storageId, Container chest) {
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
        if (io.github.kaseyawolf2.horizonwright.forge.client.AutomaticInventory.ID.equals(loadoutId))
            resolvedLoadout = io.github.kaseyawolf2.horizonwright.forge.client.AutomaticInventory
                .inspect(minecraft, profile);
        if (resolvedLoadout == null) {
            throw new IllegalStateException("profile has no named loadout '" + loadoutId + "'");
        }
        NamedStorageEndpoint endpoint = requireEndpoint(profile, storageId);
        NamedLocation location = requireLocation(profile, endpoint.getLocationId());
        if (minecraft.theWorld.provider == null
            || minecraft.theWorld.provider.dimensionId != location.getDimensionId()) {
            throw mismatch(storageId);
        }
        TileEntity tile = MinecraftRuntimeAccess
            .tileEntity(minecraft.theWorld, location.getX(), location.getY(), location.getZ());
        if (!(tile instanceof IInventory)) {
            throw mismatch(storageId);
        }
        IInventory configured = (IInventory) tile;
        IInventory open = SupportedChestLayout.inventory(chest);
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

    @Override
    public NamedLocation location(String storageId) {
        ProfileEnvelope profile = requireProfile();
        return requireLocation(profile, requireEndpoint(profile, storageId).getLocationId());
    }

    @Override
    public boolean matches(String storageId, Container container) {
        NamedLocation target = location(storageId);
        if (minecraft.theWorld == null || minecraft.theWorld.provider.dimensionId != target.getDimensionId()
            || !SupportedChestLayout.supports(container)) return false;
        Object tile = MinecraftRuntimeAccess
            .tileEntity(minecraft.theWorld, target.getX(), target.getY(), target.getZ());
        IInventory open = SupportedChestLayout.inventory(container);
        return tile == open || tile instanceof IInventory && open instanceof InventoryLargeChest
            && ((InventoryLargeChest) open).isPartOfLargeChest((IInventory) tile);
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
