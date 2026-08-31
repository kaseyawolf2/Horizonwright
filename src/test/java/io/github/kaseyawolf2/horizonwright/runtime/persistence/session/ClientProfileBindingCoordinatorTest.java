package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRoute;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadStatus;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingIndex;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingIndexStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingKey;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileReassociation;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileStatePaths;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

public class ClientProfileBindingCoordinatorTest {

    private static final String ENDPOINT = "server.example.test:25565";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void observationRequiresTheExplicitMultiplayerFactsToMatchItsOpaqueKey() {
        ProfileBindingKey key = ProfileBindingKey.multiplayer(ENDPOINT, "world-one");

        assertThrows(IllegalArgumentException.class, () -> observation(key, "other.example.test:25565", "world-one"));
        assertThrows(IllegalArgumentException.class, () -> observation(key, ENDPOINT, "world-two"));

        ClientProfileBindingObservation accepted = observation(key, ENDPOINT, "world-one");
        assertEquals(key, accepted.getKey());
        assertEquals("world-one", accepted.getWorldFingerprint());
    }

    @Test
    public void enrollmentRequiresConfirmationAndExposesIdentityOnlyAfterProfileValidation() throws Exception {
        Path root = temporaryFolder.newFolder("enrollment")
            .toPath();
        Stores stores = new Stores(root);
        ClientProfileBindingCoordinator coordinator = coordinator(
            stores,
            ids("profile-a"),
            ids("confirm-enroll-a"),
            100L);
        ProfileBindingKey key = ProfileBindingKey.multiplayer(ENDPOINT, "world-a");

        assertEquals(
            ClientProfileBindingState.NO_WORLD,
            coordinator.getSnapshot()
                .getState());
        assertFalse(
            coordinator.getSnapshot()
                .getSelectedIdentity()
                .isPresent());
        ClientProfileBindingSnapshot observed = coordinator.observe(observation(key, ENDPOINT, "world-a"));
        assertEquals(ClientProfileBindingState.NEEDS_EXPLICIT_ENROLLMENT, observed.getState());
        assertFalse(
            observed.getSelectedIdentity()
                .isPresent());

        assertThrows(IllegalArgumentException.class, () -> coordinator.confirmEnrollment(false));
        assertEquals(
            PersistenceLoadStatus.MISSING,
            stores.index.load()
                .getStatus());

        ClientProfileBindingSnapshot enrolled = coordinator.confirmEnrollment(true);
        assertEquals(ClientProfileBindingState.READY, enrolled.getState());
        WorldProfileIdentity identity = enrolled.getSelectedIdentity()
            .get();
        assertEquals("profile-a", identity.getProfileId());
        assertEquals(
            identity,
            stores.index.load()
                .getValue()
                .find(key)
                .get());
        assertEquals(
            identity,
            stores.profiles.loadProfile(stores.profiles.pathsForProfile("profile-a"))
                .getValue()
                .getIdentity());

        ClientProfileBindingCoordinator restarted = coordinator(stores, ids(), ids(), 101L);
        assertEquals(
            ClientProfileBindingState.READY,
            restarted.observe(observation(key, ENDPOINT, "world-a"))
                .getState());
    }

    @Test
    public void sameLocatorFingerprintChangeNeedsExplicitReassociationAndPreservesProfileAssets() throws Exception {
        Path root = temporaryFolder.newFolder("same-locator-reassociation")
            .toPath();
        Stores stores = new Stores(root);
        ProfileBindingKey previousKey = ProfileBindingKey.multiplayer(ENDPOINT, "world-before-reset");
        WorldProfileIdentity previous = identity("stable-profile", ENDPOINT, "world-before-reset", 10L);
        NamedLocation home = new NamedLocation("home", "Home", 0, 12, 64, -8);
        seed(stores, previousKey, previous, "confirm-original", 11L, Collections.singletonList(home));
        NamedLoadout loadout = new NamedLoadout("mining", "Mining", Collections.emptyList());
        NamedStorageEndpoint storage = new NamedStorageEndpoint(
            "home-chest",
            "Home chest",
            home.getId(),
            StorageItemFilter.acceptAll());
        NamedRepairStation repair = new NamedRepairStation("home-forge", "Home forge", home.getId(), loadout.getId());
        stores.profiles.saveProfile(
            stores.profiles.pathsForProfile(previous.getProfileId()),
            new ProfileEnvelope(
                11L,
                previous,
                Collections.emptyList(),
                Collections.singletonList(home),
                Collections.emptyList(),
                Collections.singletonList(loadout),
                Collections.singletonList(storage),
                Collections.singletonList(repair)));
        ProfileBindingKey replacementKey = ProfileBindingKey.multiplayer(ENDPOINT, "world-after-reset");
        ClientProfileBindingCoordinator coordinator = coordinator(stores, ids(), ids("confirm-reset"), 20L);

        ClientProfileBindingSnapshot candidate = coordinator
            .observe(observation(replacementKey, ENDPOINT, "world-after-reset"));
        assertEquals(ClientProfileBindingState.NEEDS_EXPLICIT_REASSOCIATION, candidate.getState());
        assertEquals(Collections.singletonList("stable-profile"), candidate.getReassociationCandidateProfileIds());
        assertFalse(
            candidate.getSelectedIdentity()
                .isPresent());

        ClientProfileBindingSnapshot reassociated = coordinator.confirmReassociation("stable-profile", true);
        assertEquals(ClientProfileBindingState.READY, reassociated.getState());
        WorldProfileIdentity replacement = reassociated.getSelectedIdentity()
            .get();
        assertEquals("stable-profile", replacement.getProfileId());
        assertEquals("world-after-reset", replacement.getWorldFingerprint());
        assertFalse(
            stores.index.load()
                .getValue()
                .find(previousKey)
                .isPresent());
        assertEquals(
            replacement,
            stores.index.load()
                .getValue()
                .find(replacementKey)
                .get());

        ProfileEnvelope persisted = stores.profiles.loadProfile(stores.profiles.pathsForProfile("stable-profile"))
            .getValue();
        assertEquals(Collections.singletonList(home), persisted.getNamedLocations());
        assertEquals(Collections.singletonList(loadout), persisted.getNamedLoadouts());
        assertEquals(Collections.singletonList(storage), persisted.getNamedStorageEndpoints());
        assertEquals(Collections.singletonList(repair), persisted.getNamedRepairStations());
        assertEquals(
            1,
            persisted.getReassociations()
                .size());
        assertEquals(
            "confirm-reset",
            persisted.getReassociations()
                .get(0)
                .getConfirmationId());
    }

    @Test
    public void changedLocatorIsNotInferredAndRequiresExplicitProfileSelection() throws Exception {
        Path root = temporaryFolder.newFolder("changed-locator-reassociation")
            .toPath();
        Stores stores = new Stores(root);
        String oldEndpoint = "old.example.test:25565";
        String newEndpoint = "new.example.test:25565";
        ProfileBindingKey oldKey = ProfileBindingKey.multiplayer(oldEndpoint, "world-stable");
        WorldProfileIdentity previous = identity("stable-profile", oldEndpoint, "world-stable", 10L);
        seed(stores, oldKey, previous, "confirm-original", 11L, Collections.<NamedLocation>emptyList());
        ProfileBindingKey newKey = ProfileBindingKey.multiplayer(newEndpoint, "world-stable");
        ClientProfileBindingCoordinator coordinator = coordinator(stores, ids(), ids("confirm-address-change"), 20L);

        ClientProfileBindingSnapshot unknown = coordinator.observe(observation(newKey, newEndpoint, "world-stable"));
        assertEquals(ClientProfileBindingState.NEEDS_EXPLICIT_ENROLLMENT, unknown.getState());
        assertTrue(
            unknown.getReassociationCandidateProfileIds()
                .isEmpty());
        assertFalse(
            unknown.getSelectedIdentity()
                .isPresent());

        ClientProfileBindingSnapshot selected = coordinator.requestReassociation("stable-profile");
        assertEquals(ClientProfileBindingState.NEEDS_EXPLICIT_REASSOCIATION, selected.getState());
        assertEquals(Collections.singletonList("stable-profile"), selected.getReassociationCandidateProfileIds());
        ClientProfileBindingSnapshot ready = coordinator.confirmReassociation("stable-profile", true);
        assertEquals(ClientProfileBindingState.READY, ready.getState());
        assertEquals(
            newEndpoint,
            ready.getSelectedIdentity()
                .get()
                .getServerAddress());
    }

    @Test
    public void corruptOrNewerDocumentsAreFailedAndPreservedWithoutSelectedIdentity() throws Exception {
        Path newerRoot = temporaryFolder.newFolder("newer-index")
            .toPath();
        Stores newerStores = new Stores(newerRoot);
        ProfileBindingKey key = ProfileBindingKey.multiplayer(ENDPOINT, "world-a");
        WorldProfileIdentity identity = identity("profile-a", ENDPOINT, "world-a", 10L);
        seed(newerStores, key, identity, "confirm-original", 11L, Collections.<NamedLocation>emptyList());
        Path indexFile = newerStores.index.getPaths()
            .getIndexFile();
        String supported = new String(Files.readAllBytes(indexFile), StandardCharsets.UTF_8);
        byte[] newer = supported.replace("\"schemaVersion\": 1", "\"schemaVersion\": 999")
            .getBytes(StandardCharsets.UTF_8);
        Files.write(indexFile, newer);

        ClientProfileBindingSnapshot newerSnapshot = coordinator(newerStores, ids(), ids(), 20L)
            .observe(observation(key, ENDPOINT, "world-a"));
        assertEquals(ClientProfileBindingState.FAILED, newerSnapshot.getState());
        assertEquals(
            PersistenceLoadStatus.NEWER_SCHEMA,
            newerSnapshot.getLoadStatus()
                .get());
        assertFalse(
            newerSnapshot.getSelectedIdentity()
                .isPresent());
        assertArrayEquals(newer, Files.readAllBytes(indexFile));

        Path corruptRoot = temporaryFolder.newFolder("corrupt-profile")
            .toPath();
        Stores corruptStores = new Stores(corruptRoot);
        seed(corruptStores, key, identity, "confirm-original", 11L, Collections.<NamedLocation>emptyList());
        ProfileStatePaths profilePaths = corruptStores.profiles.pathsForProfile("profile-a");
        byte[] corrupt = "{not-json".getBytes(StandardCharsets.UTF_8);
        Files.write(profilePaths.getProfileFile(), corrupt);

        ClientProfileBindingSnapshot corruptSnapshot = coordinator(corruptStores, ids(), ids(), 20L)
            .observe(observation(key, ENDPOINT, "world-a"));
        assertEquals(ClientProfileBindingState.FAILED, corruptSnapshot.getState());
        assertEquals(
            PersistenceLoadStatus.CORRUPT,
            corruptSnapshot.getLoadStatus()
                .get());
        assertFalse(
            corruptSnapshot.getSelectedIdentity()
                .isPresent());
        assertArrayEquals(corrupt, Files.readAllBytes(profilePaths.getProfileFile()));
    }

    @Test
    public void interruptedEnrollmentIsDetectableAndNeedsExplicitRecovery() throws Exception {
        Path root = temporaryFolder.newFolder("interrupted-enrollment")
            .toPath();
        Stores stores = new Stores(root);
        ClientProfileBindingObservation observation = observation(
            ProfileBindingKey.multiplayer(ENDPOINT, "world-a"),
            ENDPOINT,
            "world-a");
        ClientProfileBindingCoordinator interrupted = coordinator(
            stores,
            ids("profile-a"),
            ids("confirm-enroll-a"),
            100L,
            new ClientProfileBindingCoordinator.CommitHook() {

                @Override
                public void afterIndexCommitted() {
                    throw new IllegalStateException("injected interruption");
                }
            });
        interrupted.observe(observation);

        assertThrows(ClientProfileBindingException.class, () -> interrupted.confirmEnrollment(true));
        assertEquals(
            ClientProfileBindingState.FAILED,
            interrupted.getSnapshot()
                .getState());
        assertTrue(
            interrupted.getSnapshot()
                .isInterruptedUpdateRecoverable());
        assertFalse(
            interrupted.getSnapshot()
                .getSelectedIdentity()
                .isPresent());
        assertTrue(Files.exists(root.resolve("profile-binding-transaction.json")));
        assertEquals(
            PersistenceLoadStatus.MISSING,
            stores.profiles.loadProfile(stores.profiles.pathsForProfile("profile-a"))
                .getStatus());

        ClientProfileBindingCoordinator restarted = coordinator(stores, ids(), ids(), 101L);
        ClientProfileBindingObservation equivalentEndpoint = observation(
            ProfileBindingKey.multiplayer("server.example.test", "world-a"),
            "server.example.test",
            "world-a");
        ClientProfileBindingSnapshot detected = restarted.observe(equivalentEndpoint);
        assertEquals(ClientProfileBindingState.FAILED, detected.getState());
        assertTrue(detected.isInterruptedUpdateRecoverable());
        assertFalse(
            detected.getSelectedIdentity()
                .isPresent());

        ClientProfileBindingSnapshot recovered = restarted.recoverInterruptedUpdate();
        assertEquals(ClientProfileBindingState.READY, recovered.getState());
        assertEquals(
            "profile-a",
            recovered.getSelectedIdentity()
                .get()
                .getProfileId());
        assertFalse(Files.exists(root.resolve("profile-binding-transaction.json")));
    }

    @Test
    public void interruptedReassociationPreservesOldProfileUntilExplicitRecovery() throws Exception {
        Path root = temporaryFolder.newFolder("interrupted-reassociation")
            .toPath();
        Stores stores = new Stores(root);
        ProfileBindingKey oldKey = ProfileBindingKey.multiplayer(ENDPOINT, "world-before-reset");
        WorldProfileIdentity previous = identity("stable-profile", ENDPOINT, "world-before-reset", 10L);
        seed(stores, oldKey, previous, "confirm-original", 11L, Collections.<NamedLocation>emptyList());
        ProfileBindingKey newKey = ProfileBindingKey.multiplayer(ENDPOINT, "world-after-reset");
        ClientProfileBindingObservation newObservation = observation(newKey, ENDPOINT, "world-after-reset");
        ClientProfileBindingCoordinator interrupted = coordinator(
            stores,
            ids(),
            ids("confirm-reset"),
            20L,
            new ClientProfileBindingCoordinator.CommitHook() {

                @Override
                public void afterIndexCommitted() {
                    throw new IllegalStateException("injected interruption");
                }
            });
        interrupted.observe(newObservation);

        assertThrows(
            ClientProfileBindingException.class,
            () -> interrupted.confirmReassociation("stable-profile", true));
        ProfileEnvelope unchanged = stores.profiles.loadProfile(stores.profiles.pathsForProfile("stable-profile"))
            .getValue();
        assertEquals(previous, unchanged.getIdentity());
        assertTrue(
            unchanged.getReassociations()
                .isEmpty());

        ClientProfileBindingCoordinator restarted = coordinator(stores, ids(), ids(), 21L);
        assertTrue(
            restarted.observe(newObservation)
                .isInterruptedUpdateRecoverable());
        ClientProfileBindingSnapshot recovered = restarted.recoverInterruptedUpdate();
        assertEquals(ClientProfileBindingState.READY, recovered.getState());
        assertEquals(
            "world-after-reset",
            recovered.getSelectedIdentity()
                .get()
                .getWorldFingerprint());
        ProfileEnvelope updated = stores.profiles.loadProfile(stores.profiles.pathsForProfile("stable-profile"))
            .getValue();
        assertEquals(
            1,
            updated.getReassociations()
                .size());
        assertEquals(
            "confirm-reset",
            updated.getReassociations()
                .get(0)
                .getConfirmationId());
    }

    private static ClientProfileBindingObservation observation(ProfileBindingKey key, String endpoint,
        String fingerprint) {
        return new ClientProfileBindingObservation(key, "Test world", endpoint, fingerprint);
    }

    private static WorldProfileIdentity identity(String profileId, String endpoint, String fingerprint,
        long createdAt) {
        return new WorldProfileIdentity(profileId, "Test world", endpoint, fingerprint, createdAt);
    }

    private static void seed(Stores stores, ProfileBindingKey key, WorldProfileIdentity identity, String confirmationId,
        long confirmedAt, java.util.List<NamedLocation> locations) throws Exception {
        stores.index.save(
            ProfileBindingIndex.empty()
                .enroll(key, identity, confirmationId, confirmedAt, true));
        stores.profiles.saveProfile(
            stores.profiles.pathsForProfile(identity.getProfileId()),
            new ProfileEnvelope(
                confirmedAt,
                identity,
                Collections.<ProfileReassociation>emptyList(),
                locations,
                Collections.<NamedRoute>emptyList()));
    }

    private static ClientProfileBindingCoordinator coordinator(Stores stores, StableRandomIdSource profileIds,
        StableRandomIdSource confirmationIds, long now) {
        return new ClientProfileBindingCoordinator(
            stores.index,
            stores.profiles,
            profileIds,
            confirmationIds,
            () -> now);
    }

    private static ClientProfileBindingCoordinator coordinator(Stores stores, StableRandomIdSource profileIds,
        StableRandomIdSource confirmationIds, long now, ClientProfileBindingCoordinator.CommitHook hook) {
        return new ClientProfileBindingCoordinator(
            stores.index,
            stores.profiles,
            profileIds,
            confirmationIds,
            () -> now,
            hook);
    }

    private static StableRandomIdSource ids(String... values) {
        Queue<String> remaining = new ArrayDeque<>(Arrays.asList(values));
        return () -> {
            if (remaining.isEmpty()) {
                throw new AssertionError("unexpected stable random ID request");
            }
            return remaining.remove();
        };
    }

    private static final class Stores {

        private final ProfileBindingIndexStore index;
        private final HorizonwrightPersistenceStore profiles;

        private Stores(Path root) {
            index = new ProfileBindingIndexStore(root);
            profiles = new HorizonwrightPersistenceStore(root);
        }
    }
}
