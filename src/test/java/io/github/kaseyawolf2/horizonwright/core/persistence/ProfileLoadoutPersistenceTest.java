package io.github.kaseyawolf2.horizonwright.core.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageFilterMode;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemRule;

public class ProfileLoadoutPersistenceTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void namedLoadoutRoundTripsWithExactModdedReservationRules() throws Exception {
        NamedLoadout loadout = new NamedLoadout(
            "excavation",
            "Excavation",
            Arrays.asList(
                new LoadoutReservation("pick", LoadoutRole.TOOL, "TConstruct:pickaxe", 0, null, 1),
                new LoadoutReservation("repair", LoadoutRole.REPAIR_MATERIAL, "TConstruct:materials", 3, null, 16)));
        ProfileEnvelope profile = new ProfileEnvelope(
            20L,
            identity(),
            Collections.<ProfileReassociation>emptyList(),
            Collections.<NamedLocation>emptyList(),
            Collections.<NamedRoute>emptyList(),
            Collections.singletonList(loadout));
        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile("profile");

        store.saveProfile(paths, profile);
        ProfileEnvelope reloaded = store.loadProfile(paths)
            .getValue();

        assertEquals(profile, reloaded);
        assertEquals(
            loadout,
            reloaded.getNamedLoadouts()
                .get(0));
    }

    @Test
    public void profilesWithoutLoadoutsRemainValidAndNormalizeToAnEmptyList() throws Exception {
        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile("profile");
        ProfileEnvelope legacyShape = new ProfileEnvelope(
            20L,
            identity(),
            Collections.<ProfileReassociation>emptyList(),
            Collections.<NamedLocation>emptyList(),
            Collections.<NamedRoute>emptyList());

        store.saveProfile(paths, legacyShape);
        ProfileEnvelope reloaded = store.loadProfile(paths)
            .getValue();

        assertTrue(
            reloaded.getNamedLoadouts()
                .isEmpty());
        assertTrue(
            reloaded.getNamedStorageEndpoints()
                .isEmpty());
        assertTrue(
            reloaded.getNamedRepairStations()
                .isEmpty());
        assertTrue(
            reloaded.getNamedAreas()
                .isEmpty());
    }

    @Test
    public void namedStorageEndpointRoundTripsItsLocationAndExactDestinationFilter() throws Exception {
        NamedLocation location = new NamedLocation("ore-room", "Ore room", 0, -12, 64, 30);
        NamedStorageEndpoint endpoint = new NamedStorageEndpoint(
            "ore-chest",
            "Ore chest",
            location.getId(),
            new StorageItemFilter(
                StorageFilterMode.ALLOW_MATCHES,
                Collections.singletonList(new StorageItemRule("gregtech:gt.blockores", -1, null))));
        ProfileEnvelope profile = new ProfileEnvelope(
            20L,
            identity(),
            Collections.emptyList(),
            Collections.singletonList(location),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList(endpoint));
        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile("profile");

        store.saveProfile(paths, profile);
        ProfileEnvelope reloaded = store.loadProfile(paths)
            .getValue();

        assertEquals(profile, reloaded);
        assertEquals(
            endpoint,
            reloaded.getNamedStorageEndpoints()
                .get(0));
    }

    @Test
    public void legacyProfileWithoutStorageOrRepairFieldsNormalizesToEmpty() {
        ProfileEnvelope profile = new ProfileEnvelope(
            20L,
            identity(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        PersistenceJsonCodec codec = new PersistenceJsonCodec();
        String encoded = new String(codec.encodeProfile(profile), StandardCharsets.UTF_8);
        String legacy = encoded.replaceFirst(",?\\s*\"namedStorageEndpoints\"\\s*:\\s*\\[\\s*\\]", "");
        legacy = legacy.replaceFirst(",?\\s*\"namedRepairStations\"\\s*:\\s*\\[\\s*\\]", "");
        legacy = legacy.replaceFirst(",?\\s*\"namedAreas\"\\s*:\\s*\\[\\s*\\]", "");

        PersistenceJsonCodec.DecodeResult<ProfileEnvelope> decoded = codec
            .decodeProfile(legacy.getBytes(StandardCharsets.UTF_8));

        assertEquals(PersistenceLoadStatus.LOADED, decoded.getStatus());
        assertTrue(
            decoded.getValue()
                .getNamedStorageEndpoints()
                .isEmpty());
        assertTrue(
            decoded.getValue()
                .getNamedRepairStations()
                .isEmpty());
        assertTrue(
            decoded.getValue()
                .getNamedAreas()
                .isEmpty());
    }

    @Test
    public void namedAreaRoundTripsNormalizedInclusiveCorners() throws Exception {
        NamedArea plot = new NamedArea(
            "north-field",
            "North field",
            new BasePosition(0, 12, 70, -4),
            new BasePosition(0, 2, 60, 8));
        ProfileEnvelope profile = new ProfileEnvelope(
            20L,
            identity(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList(plot));
        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile("profile");

        store.saveProfile(paths, profile);
        ProfileEnvelope reloaded = store.loadProfile(paths)
            .getValue();

        assertEquals(profile, reloaded);
        assertEquals(Collections.singletonList(plot), reloaded.getNamedAreas());
        assertEquals(
            new BasePosition(0, 2, 60, -4),
            reloaded.getNamedAreas()
                .get(0)
                .getMinimum());
        assertEquals(
            new BasePosition(0, 12, 70, 8),
            reloaded.getNamedAreas()
                .get(0)
                .getMaximum());
    }

    @Test
    public void namedRepairStationRoundTripsExactLocationAndLoadoutBindings() throws Exception {
        NamedLocation location = new NamedLocation("forge-room", "Tool forge", 0, 8, 65, -2);
        NamedLoadout loadout = new NamedLoadout("mining", "Mining", Collections.emptyList());
        NamedRepairStation station = new NamedRepairStation(
            "tool-forge",
            "Main tool forge",
            location.getId(),
            loadout.getId());
        ProfileEnvelope profile = new ProfileEnvelope(
            20L,
            identity(),
            Collections.emptyList(),
            Collections.singletonList(location),
            Collections.emptyList(),
            Collections.singletonList(loadout),
            Collections.emptyList(),
            Collections.singletonList(station));
        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile("profile");

        store.saveProfile(paths, profile);
        ProfileEnvelope reloaded = store.loadProfile(paths)
            .getValue();

        assertEquals(profile, reloaded);
        assertEquals(
            station,
            reloaded.getNamedRepairStations()
                .get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void storageEndpointCannotReferenceAnUnknownLocation() {
        new ProfileEnvelope(
            20L,
            identity(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList(
                new NamedStorageEndpoint("ore-chest", "Ore chest", "missing", StorageItemFilter.acceptAll())));
    }

    @Test(expected = IllegalArgumentException.class)
    public void repairStationCannotReferenceAnUnknownLoadout() {
        NamedLocation location = new NamedLocation("forge-room", "Tool forge", 0, 8, 65, -2);
        new ProfileEnvelope(
            20L,
            identity(),
            Collections.emptyList(),
            Collections.singletonList(location),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections
                .singletonList(new NamedRepairStation("tool-forge", "Main tool forge", location.getId(), "missing")));
    }

    private HorizonwrightPersistenceStore store() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        return new HorizonwrightPersistenceStore(root);
    }

    private static WorldProfileIdentity identity() {
        return new WorldProfileIdentity("profile", "Profile", "singleplayer:test", "sha256:world", 10L);
    }
}
