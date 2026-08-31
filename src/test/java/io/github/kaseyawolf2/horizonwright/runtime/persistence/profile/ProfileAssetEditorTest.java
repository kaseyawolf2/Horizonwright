package io.github.kaseyawolf2.horizonwright.runtime.persistence.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRoute;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileReassociation;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

public class ProfileAssetEditorTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void dependentAssetsAreWrittenInOneValidAtomicProfileUpdate() throws Exception {
        HorizonwrightPersistenceStore store = store();
        WorldProfileIdentity identity = identity("world-one");
        store.saveProfile(
            store.pathsForProfile(identity.getProfileId()),
            new ProfileEnvelope(
                20L,
                identity,
                Collections.<ProfileReassociation>emptyList(),
                Collections.<NamedLocation>emptyList(),
                Collections.<NamedRoute>emptyList()));
        NamedLocation location = new NamedLocation("service-room", "Service room", 0, 10, 64, 20);
        NamedLoadout loadout = loadout("mining", 16);
        NamedStorageEndpoint storage = new NamedStorageEndpoint(
            "ore-chest",
            "Ore chest",
            location.getId(),
            StorageItemFilter.acceptAll());
        NamedRepairStation station = new NamedRepairStation(
            "tool-forge",
            "Tool forge",
            location.getId(),
            loadout.getId());
        ProfileAssetEditor editor = new ProfileAssetEditor(store, identity, () -> 30L);

        ProfileEnvelope saved = editor.apply(ProfileAssetUpdate.of(location, loadout, storage, station));

        assertEquals(30L, saved.getWrittenAtEpochMillis());
        assertEquals(Collections.singletonList(location), saved.getNamedLocations());
        assertEquals(Collections.singletonList(loadout), saved.getNamedLoadouts());
        assertEquals(Collections.singletonList(storage), saved.getNamedStorageEndpoints());
        assertEquals(Collections.singletonList(station), saved.getNamedRepairStations());
        assertEquals(saved, editor.load());
    }

    @Test
    public void replacementKeepsOrderAndPreservesUnrelatedAssets() throws Exception {
        HorizonwrightPersistenceStore store = store();
        WorldProfileIdentity identity = identity("world-one");
        NamedLocation first = new NamedLocation("first", "First", 0, 1, 2, 3);
        NamedLocation second = new NamedLocation("second", "Second", 0, 4, 5, 6);
        NamedLoadout oldLoadout = loadout("mining", 8);
        store.saveProfile(
            store.pathsForProfile(identity.getProfileId()),
            new ProfileEnvelope(
                40L,
                identity,
                Collections.emptyList(),
                Arrays.asList(first, second),
                Collections.emptyList(),
                Collections.singletonList(oldLoadout)));
        ProfileAssetEditor editor = new ProfileAssetEditor(store, identity, () -> 35L);
        NamedLocation replacement = new NamedLocation("first", "Renamed", -1, 7, 8, 9);
        NamedLoadout replacementLoadout = loadout("mining", 32);

        ProfileEnvelope saved = editor.apply(ProfileAssetUpdate.of(replacement, replacementLoadout, null, null));

        assertEquals(40L, saved.getWrittenAtEpochMillis());
        assertEquals(Arrays.asList(replacement, second), saved.getNamedLocations());
        assertEquals(Collections.singletonList(replacementLoadout), saved.getNamedLoadouts());
    }

    @Test
    public void invalidCrossReferenceLeavesThePreviousProfileUntouched() throws Exception {
        HorizonwrightPersistenceStore store = store();
        WorldProfileIdentity identity = identity("world-one");
        ProfileEnvelope original = new ProfileEnvelope(
            20L,
            identity,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        store.saveProfile(store.pathsForProfile(identity.getProfileId()), original);
        ProfileAssetEditor editor = new ProfileAssetEditor(store, identity, () -> 30L);
        NamedStorageEndpoint orphan = new NamedStorageEndpoint(
            "orphan",
            "Orphan chest",
            "missing-location",
            StorageItemFilter.acceptAll());

        assertThrows(
            IllegalArgumentException.class,
            () -> editor.apply(ProfileAssetUpdate.of(null, null, orphan, null)));
        assertEquals(original, editor.load());
        assertTrue(
            store.loadProfile(store.pathsForProfile(identity.getProfileId()))
                .isLoaded());
    }

    @Test
    public void staleWorldIdentityCannotEditTheReboundProfile() throws Exception {
        HorizonwrightPersistenceStore store = store();
        WorldProfileIdentity oldIdentity = identity("world-one");
        store.saveProfile(
            store.pathsForProfile(oldIdentity.getProfileId()),
            new ProfileEnvelope(
                20L,
                oldIdentity,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()));
        ProfileAssetEditor stale = new ProfileAssetEditor(store, oldIdentity, () -> 40L);
        WorldProfileIdentity rebound = identity("world-two");
        store.saveProfile(
            store.pathsForProfile(rebound.getProfileId()),
            new ProfileEnvelope(
                30L,
                rebound,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()));

        assertThrows(
            ProfileAssetEditingException.class,
            () -> stale.apply(ProfileAssetUpdate.of(new NamedLocation("home", "Home", 0, 0, 64, 0), null, null, null)));
    }

    private HorizonwrightPersistenceStore store() throws Exception {
        return new HorizonwrightPersistenceStore(
            temporaryFolder.newFolder("state")
                .toPath());
    }

    private static NamedLoadout loadout(String id, int materialCount) {
        return new NamedLoadout(
            id,
            "Mining",
            Arrays.asList(
                new LoadoutReservation("tool", LoadoutRole.TOOL, "TConstruct:pickaxe", 0, null, 1),
                new LoadoutReservation(
                    "repair",
                    LoadoutRole.REPAIR_MATERIAL,
                    "TConstruct:materials",
                    3,
                    null,
                    materialCount)));
    }

    private static WorldProfileIdentity identity(String fingerprint) {
        return new WorldProfileIdentity("profile", "Test", "singleplayer", fingerprint, 10L);
    }
}
