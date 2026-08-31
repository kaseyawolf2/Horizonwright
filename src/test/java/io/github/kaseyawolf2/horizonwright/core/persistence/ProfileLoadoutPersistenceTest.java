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
    public void legacyProfileWithoutStorageEndpointFieldNormalizesToEmpty() {
        ProfileEnvelope profile = new ProfileEnvelope(
            20L,
            identity(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        PersistenceJsonCodec codec = new PersistenceJsonCodec();
        String encoded = new String(codec.encodeProfile(profile), StandardCharsets.UTF_8);
        String legacy = encoded.replaceFirst(",?\\s*\"namedStorageEndpoints\"\\s*:\\s*\\[\\s*\\]", "");

        PersistenceJsonCodec.DecodeResult<ProfileEnvelope> decoded = codec
            .decodeProfile(legacy.getBytes(StandardCharsets.UTF_8));

        assertEquals(PersistenceLoadStatus.LOADED, decoded.getStatus());
        assertTrue(
            decoded.getValue()
                .getNamedStorageEndpoints()
                .isEmpty());
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

    private HorizonwrightPersistenceStore store() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        return new HorizonwrightPersistenceStore(root);
    }

    private static WorldProfileIdentity identity() {
        return new WorldProfileIdentity("profile", "Profile", "singleplayer:test", "sha256:world", 10L);
    }
}
