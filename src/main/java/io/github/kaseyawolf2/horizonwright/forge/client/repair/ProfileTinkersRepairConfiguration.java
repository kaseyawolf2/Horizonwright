package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntity;

import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadResult;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;

/** Atomically resolves an exact profile repair-station location and material loadout. */
public final class ProfileTinkersRepairConfiguration implements LiveTinkersRepairBackend.ConfigurationSource {

    private final Minecraft minecraft;
    private final HorizonwrightPersistenceStore store;
    private final WorldProfileIdentity identity;

    public ProfileTinkersRepairConfiguration(Minecraft minecraft, HorizonwrightPersistenceStore store,
        WorldProfileIdentity identity) {
        if (minecraft == null || store == null || identity == null) {
            throw new IllegalArgumentException("minecraft, store, and identity are required");
        }
        this.minecraft = minecraft;
        this.store = store;
        this.identity = identity;
    }

    @Override
    public NamedLoadout resolve(String stationId, Container container) {
        if (!minecraft.func_152345_ab() || minecraft.theWorld == null || container == null) {
            throw new IllegalStateException("a joined client thread with an open repair station is required");
        }
        ProfileEnvelope profile = requireProfile();
        NamedRepairStation station = null;
        for (NamedRepairStation candidate : profile.getNamedRepairStations()) {
            if (candidate.getId()
                .equals(stationId)) {
                station = candidate;
                break;
            }
        }
        if (station == null) {
            throw new IllegalStateException("profile has no named repair station '" + stationId + "'");
        }
        NamedLocation location = location(profile, station.getLocationId());
        if (minecraft.theWorld.provider == null
            || minecraft.theWorld.provider.dimensionId != location.getDimensionId()) {
            throw mismatch(stationId);
        }
        TileEntity tile = MinecraftRuntimeAccess
            .tileEntity(minecraft.theWorld, location.getX(), location.getY(), location.getZ());
        Object firstSlot = container.inventorySlots.isEmpty() ? null : container.inventorySlots.get(0);
        if (tile == null || !(firstSlot instanceof Slot) || ((Slot) firstSlot).inventory != tile) {
            throw mismatch(stationId);
        }
        for (NamedLoadout loadout : profile.getNamedLoadouts()) {
            if (loadout.getId()
                .equals(station.getLoadoutId())) return loadout;
        }
        throw new IllegalStateException("repair station references missing loadout '" + station.getLoadoutId() + "'");
    }

    private ProfileEnvelope requireProfile() {
        PersistenceLoadResult<ProfileEnvelope> loaded = store
            .loadProfile(store.pathsForProfile(identity.getProfileId()));
        if (!loaded.isLoaded()) {
            throw new IllegalStateException("active profile cannot be read: " + loaded.getDiagnostic());
        }
        ProfileEnvelope profile = loaded.getValue();
        if (!identity.equals(profile.getIdentity())) {
            throw new IllegalStateException("active profile identity changed while a repair station was open");
        }
        return profile;
    }

    private static NamedLocation location(ProfileEnvelope profile, String id) {
        for (NamedLocation location : profile.getNamedLocations()) {
            if (location.getId()
                .equals(id)) return location;
        }
        throw new IllegalStateException("repair station references missing location '" + id + "'");
    }

    private static IllegalStateException mismatch(String stationId) {
        return new IllegalStateException("the open container is not configured repair station '" + stationId + "'");
    }
}
