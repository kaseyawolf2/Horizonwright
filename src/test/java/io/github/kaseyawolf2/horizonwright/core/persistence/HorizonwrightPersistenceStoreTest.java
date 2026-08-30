package io.github.kaseyawolf2.horizonwright.core.persistence;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;

public class HorizonwrightPersistenceStoreTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void goldenProfileCarriesStableWorldIdentityReassociationAndDimensionBearingAssets() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        fileSystem.put(paths.getProfileFile(), resource("/persistence/profile-v1.json"));

        PersistenceLoadResult<ProfileEnvelope> result = store.loadProfile(paths);

        assertEquals(PersistenceLoadStatus.LOADED, result.getStatus());
        ProfileEnvelope profile = result.getValue();
        assertEquals(
            "gtnh-main",
            profile.getIdentity()
                .getProfileId());
        assertEquals(
            "gtnh.example.test:25565",
            profile.getIdentity()
                .getServerAddress());
        assertEquals(
            "sha256:world-beta",
            profile.getIdentity()
                .getWorldFingerprint());
        assertTrue(
            profile.getReassociations()
                .get(0)
                .isUserConfirmed());
        assertEquals(
            "sha256:world-alpha",
            profile.getReassociations()
                .get(0)
                .getPreviousWorldFingerprint());
        assertEquals(
            0,
            profile.getNamedLocations()
                .get(0)
                .getDimensionId());
        assertEquals(
            -27,
            profile.getNamedLocations()
                .get(1)
                .getDimensionId());
        assertEquals(
            0,
            profile.getNamedRoutes()
                .get(0)
                .getNodes()
                .get(0)
                .getDimensionId());
        assertEquals(
            -27,
            profile.getNamedRoutes()
                .get(0)
                .getNodes()
                .get(1)
                .getDimensionId());
    }

    @Test
    public void unresolvedDeathStateSurvivesLoadSaveAndReload() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        fileSystem.put(paths.getRuntimeFile(), resource("/persistence/runtime-v1-unresolved-death.json"));

        RuntimeEnvelope loaded = store.loadRuntime(paths)
            .getValue();
        assertEquals(0L, loaded.getLastConnectionEpoch());
        assertTrue(
            loaded.getTaskControllerState()
                .getTasks()
                .isEmpty());
        UnresolvedDeathState death = loaded.getUnresolvedDeathState();
        assertNotNull(death);
        assertEquals(41L, death.getDeathEpoch());
        assertEquals(RecoveryPhase.MANUAL_HOLD, death.getRecoveryPhase());
        assertEquals(ManualHoldReason.GRAVE_MISSING, death.getManualHoldReason());
        assertTrue(death.isRespawnRequestConsumed());
        assertEquals(7L, death.getDeathConnectionEpoch());
        assertEquals(9L, death.getLastObservedConnectionEpoch());
        assertFalse(
            death.getGraveState()
                .isActivationConsumed());
        assertEquals(
            0,
            death.getDeathLocation()
                .getDimensionId());

        store.saveRuntime(paths, loaded);
        RuntimeEnvelope reloaded = store.loadRuntime(paths)
            .getValue();

        assertEquals(loaded, reloaded);
        assertEquals(death, reloaded.getUnresolvedDeathState());
        assertTrue(
            new String(fileSystem.get(paths.getRuntimeFile()), StandardCharsets.UTF_8)
                .contains("\"unresolvedDeathState\""));
    }

    @Test
    public void atomicReplacementRetainsThePreviousValidDocumentAsRollingBackup() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        ProfileEnvelope first = profile("First", 100L);
        ProfileEnvelope second = profile("Second", 200L);
        store.saveProfile(paths, first);
        fileSystem.clearOperations();

        store.saveProfile(paths, second);

        assertEquals(
            second,
            store.loadProfile(paths)
                .getValue());
        assertEquals(
            first,
            store.loadProfileBackup(paths)
                .getValue());
        assertEquals(
            Arrays.asList(
                "mkdir " + paths.getProfileDirectory(),
                "write-sync " + ProfileStatePaths.temporaryOf(paths.getProfileFile()),
                "write-sync " + ProfileStatePaths.backupTemporaryOf(paths.getProfileFile()),
                "atomic-replace " + ProfileStatePaths.backupTemporaryOf(paths.getProfileFile())
                    + " -> "
                    + paths.getProfileBackupFile(),
                "atomic-replace " + ProfileStatePaths.temporaryOf(paths.getProfileFile())
                    + " -> "
                    + paths.getProfileFile()),
            fileSystem.getOperations());
    }

    @Test
    public void corruptPrimaryIsReportedPreservedAndNeverSilentlyReplacedByBackup() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        byte[] corrupt = resource("/persistence/profile-corrupt.json");
        fileSystem.put(paths.getProfileFile(), corrupt);
        fileSystem.put(paths.getProfileBackupFile(), resource("/persistence/profile-v1.json"));

        PersistenceLoadResult<ProfileEnvelope> result = store.loadProfile(paths);

        assertEquals(PersistenceLoadStatus.CORRUPT, result.getStatus());
        assertFalse(result.isLoaded());
        assertTrue(result.isBackupAvailable());
        assertEquals(
            PersistenceLoadStatus.LOADED,
            store.loadProfileBackup(paths)
                .getStatus());
        assertSaveProfileRefused(store, paths, profile("Replacement", 300L));
        assertArrayEquals(corrupt, fileSystem.get(paths.getProfileFile()));
    }

    @Test
    public void newerSchemaIsReportedAndCannotBeOverwritten() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        byte[] newer = resource("/persistence/profile-newer-v2.json");
        fileSystem.put(paths.getProfileFile(), newer);

        PersistenceLoadResult<ProfileEnvelope> result = store.loadProfile(paths);

        assertEquals(PersistenceLoadStatus.NEWER_SCHEMA, result.getStatus());
        assertTrue(
            result.getDiagnostic()
                .contains("source was preserved"));
        assertSaveProfileRefused(store, paths, profile("Replacement", 300L));
        assertArrayEquals(newer, fileSystem.get(paths.getProfileFile()));
    }

    @Test
    public void failedFinalAtomicMoveLeavesPrimaryAndBackupAtThePreviousValidVersion() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        ProfileEnvelope first = profile("First", 100L);
        store.saveProfile(paths, first);
        byte[] originalPrimary = fileSystem.get(paths.getProfileFile());
        fileSystem.failAtomicReplaceTo(paths.getProfileFile());

        try {
            store.saveProfile(paths, profile("Second", 200L));
            fail("expected atomic write failure");
        } catch (PersistenceException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("Atomic persistence write failed"));
        }

        assertArrayEquals(originalPrimary, fileSystem.get(paths.getProfileFile()));
        assertArrayEquals(originalPrimary, fileSystem.get(paths.getProfileBackupFile()));
        assertFalse(fileSystem.contains(ProfileStatePaths.temporaryOf(paths.getProfileFile())));
        assertFalse(fileSystem.contains(ProfileStatePaths.backupTemporaryOf(paths.getProfileFile())));
    }

    @Test
    public void staleTemporaryFileRequiresExplicitInspectionBeforeAnotherWrite() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        Path temporary = ProfileStatePaths.temporaryOf(paths.getProfileFile());
        byte[] interrupted = "possibly recoverable".getBytes(StandardCharsets.UTF_8);
        fileSystem.put(temporary, interrupted);

        assertSaveProfileRefused(store, paths, profile("First", 100L));

        assertArrayEquals(interrupted, fileSystem.get(temporary));
        assertFalse(fileSystem.contains(paths.getProfileFile()));
    }

    @Test
    public void absentRuntimeSerializesAnExplicitNullDeathState() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        RuntimeEnvelope runtime = new RuntimeEnvelope(
            100L,
            "gtnh-main",
            "gtnh.example.test:25565",
            "sha256:world-beta",
            null);

        store.saveRuntime(paths, runtime);

        String json = new String(fileSystem.get(paths.getRuntimeFile()), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"unresolvedDeathState\": null"));
        assertEquals(
            runtime,
            store.loadRuntime(paths)
                .getValue());
    }

    @Test
    public void realNioStoreAtomicallyReplacesAndLoadsTheRollingBackup() throws Exception {
        Path root = temporaryFolder.newFolder("persistence-nio")
            .toPath();
        HorizonwrightPersistenceStore store = new HorizonwrightPersistenceStore(root);
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        ProfileEnvelope first = profile("First", 100L);
        ProfileEnvelope second = profile("Second", 200L);

        store.saveProfile(paths, first);
        store.saveProfile(paths, second);

        assertTrue(Files.isRegularFile(paths.getProfileFile()));
        assertTrue(Files.isRegularFile(paths.getProfileBackupFile()));
        assertEquals(
            second,
            store.loadProfile(paths)
                .getValue());
        assertEquals(
            first,
            store.loadProfileBackup(paths)
                .getValue());
    }

    @Test
    public void profilePartitionRejectsTraversalAndMismatchedIdentity() throws Exception {
        try {
            new ProfileStatePaths(
                temporaryFolder.getRoot()
                    .toPath(),
                "../escape");
            fail("expected unsafe profile id rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("profileId"));
        }

        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths otherPaths = store.pathsForProfile("other-profile");
        try {
            store.saveProfile(otherPaths, profile("Wrong partition", 100L));
            fail("expected profile partition rejection");
        } catch (PersistenceException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("Refusing to write profile identity"));
        }
    }

    @Test
    public void loadRejectsAValidDocumentStoredInTheWrongProfilePartition() throws Exception {
        InMemoryPersistenceFileSystem fileSystem = new InMemoryPersistenceFileSystem();
        HorizonwrightPersistenceStore store = store(fileSystem);
        ProfileStatePaths wrongPaths = store.pathsForProfile("other-profile");
        fileSystem.put(wrongPaths.getProfileFile(), resource("/persistence/profile-v1.json"));

        PersistenceLoadResult<ProfileEnvelope> result = store.loadProfile(wrongPaths);

        assertEquals(PersistenceLoadStatus.PROFILE_MISMATCH, result.getStatus());
        assertFalse(result.isLoaded());
        assertTrue(
            result.getDiagnostic()
                .contains("source was preserved"));
    }

    private static HorizonwrightPersistenceStore store(InMemoryPersistenceFileSystem fileSystem) {
        return new HorizonwrightPersistenceStore(java.nio.file.Paths.get("build", "persistence-test-root"), fileSystem);
    }

    private static ProfileEnvelope profile(String displayName, long writtenAt) {
        WorldProfileIdentity identity = new WorldProfileIdentity(
            "gtnh-main",
            displayName,
            "gtnh.example.test:25565",
            "sha256:world-beta",
            10L);
        NamedLocation home = new NamedLocation("home", "Home", 0, 12, 70, -24);
        NamedRoute route = new NamedRoute(
            "home-loop",
            "Home Loop",
            Arrays.asList(new RouteNode(0, 12, 70, -24, "start"), new RouteNode(0, 13, 70, -24, "end")));
        return new ProfileEnvelope(
            writtenAt,
            identity,
            Collections.<ProfileReassociation>emptyList(),
            Collections.singletonList(home),
            Collections.singletonList(route));
    }

    private static void assertSaveProfileRefused(HorizonwrightPersistenceStore store, ProfileStatePaths paths,
        ProfileEnvelope replacement) throws Exception {
        try {
            store.saveProfile(paths, replacement);
            fail("expected persistence write refusal");
        } catch (PersistenceException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("Refusing")
                    || expected.getMessage()
                        .contains("Atomic persistence write failed"));
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = HorizonwrightPersistenceStoreTest.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("missing test resource " + name);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            return output.toByteArray();
        }
    }

    private static final class InMemoryPersistenceFileSystem implements PersistenceFileSystem {

        private final Map<Path, byte[]> files = new LinkedHashMap<>();
        private final List<String> operations = new ArrayList<>();
        private Path failedAtomicTarget;

        @Override
        public boolean exists(Path path) {
            return files.containsKey(normalize(path));
        }

        @Override
        public byte[] readAllBytes(Path path) throws IOException {
            byte[] content = files.get(normalize(path));
            if (content == null) {
                throw new NoSuchFileException(path.toString());
            }
            return content.clone();
        }

        @Override
        public void createDirectories(Path directory) {
            operations.add("mkdir " + normalize(directory));
        }

        @Override
        public void writeAndSync(Path path, byte[] content) {
            Path normalized = normalize(path);
            operations.add("write-sync " + normalized);
            files.put(normalized, content.clone());
        }

        @Override
        public void atomicReplace(Path source, Path target) throws IOException {
            Path normalizedSource = normalize(source);
            Path normalizedTarget = normalize(target);
            operations.add("atomic-replace " + normalizedSource + " -> " + normalizedTarget);
            if (normalizedTarget.equals(failedAtomicTarget)) {
                throw new IOException("injected atomic replacement failure");
            }
            byte[] content = files.remove(normalizedSource);
            if (content == null) {
                throw new NoSuchFileException(normalizedSource.toString());
            }
            files.put(normalizedTarget, content);
        }

        @Override
        public void deleteIfExists(Path path) {
            Path normalized = normalize(path);
            operations.add("delete " + normalized);
            files.remove(normalized);
        }

        void put(Path path, byte[] content) {
            files.put(normalize(path), content.clone());
        }

        byte[] get(Path path) {
            byte[] content = files.get(normalize(path));
            if (content == null) {
                fail("missing fake file " + path);
            }
            return content.clone();
        }

        boolean contains(Path path) {
            return files.containsKey(normalize(path));
        }

        void failAtomicReplaceTo(Path target) {
            failedAtomicTarget = normalize(target);
        }

        List<String> getOperations() {
            return Collections.unmodifiableList(operations);
        }

        void clearOperations() {
            operations.clear();
        }

        private static Path normalize(Path path) {
            return path.toAbsolutePath()
                .normalize();
        }
    }
}
