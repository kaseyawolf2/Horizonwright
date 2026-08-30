package io.github.kaseyawolf2.horizonwright.core.persistence;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ProfileBindingIndexStoreTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void atomicReplacementRetainsThePreviousConfirmedIndexAsAnExplicitBackup() throws Exception {
        Path root = temporaryFolder.newFolder("binding-store")
            .toPath();
        ProfileBindingIndexStore store = new ProfileBindingIndexStore(root);
        ProfileBindingKey firstKey = ProfileBindingKey
            .singleplayer("C:/Users/Operator/Instances/GTNH/saves/Private World", "opaque-local-world-1");
        WorldProfileIdentity firstIdentity = new WorldProfileIdentity(
            "local-profile",
            "Local profile",
            "singleplayer",
            "opaque-local-world-1",
            100L);
        ProfileBindingIndex first = ProfileBindingIndex.empty()
            .enroll(firstKey, firstIdentity, "enroll-local", 101L, true);
        ProfileBindingKey secondKey = ProfileBindingKey
            .singleplayer("C:/Users/Operator/Instances/GTNH/saves/Private World Reset", "opaque-local-world-2");
        WorldProfileIdentity secondIdentity = new WorldProfileIdentity(
            "local-profile",
            "Local profile reset",
            "singleplayer",
            "opaque-local-world-2",
            100L);
        ProfileBindingIndex second = first
            .reassociate(firstKey, secondKey, secondIdentity, "confirm-reset", 102L, true);

        assertEquals(
            PersistenceLoadStatus.MISSING,
            store.load()
                .getStatus());
        store.save(first);
        store.save(second);

        PersistenceLoadResult<ProfileBindingIndex> primary = store.load();
        PersistenceLoadResult<ProfileBindingIndex> backup = store.loadBackup();
        assertTrue(primary.isLoaded());
        assertTrue(backup.isLoaded());
        assertEquals(
            2L,
            primary.getValue()
                .getRevision());
        assertEquals(
            secondIdentity,
            primary.getValue()
                .find(secondKey)
                .get());
        assertEquals(
            1L,
            backup.getValue()
                .getRevision());
        assertEquals(
            firstIdentity,
            backup.getValue()
                .find(firstKey)
                .get());

        String persisted = new String(
            Files.readAllBytes(
                store.getPaths()
                    .getIndexFile()),
            StandardCharsets.UTF_8);
        assertFalse(persisted.contains("C:/Users/Operator"));
        assertFalse(persisted.contains("Private World"));
    }

    @Test
    public void corruptOrNewerPrimaryIsReportedAndNeverOverwritten() throws Exception {
        Path root = temporaryFolder.newFolder("refuse-overwrite")
            .toPath();
        ProfileBindingIndexStore store = new ProfileBindingIndexStore(root);
        ProfileBindingIndex replacement = singleplayerIndex("replacement", "marker-replacement", 20L);
        Files.createDirectories(
            store.getPaths()
                .getIndexFile()
                .getParent());

        byte[] corrupt = "{not-json".getBytes(StandardCharsets.UTF_8);
        Files.write(
            store.getPaths()
                .getIndexFile(),
            corrupt);
        assertEquals(
            PersistenceLoadStatus.CORRUPT,
            store.load()
                .getStatus());
        assertThrows(PersistenceException.class, () -> store.save(replacement));
        assertArrayEquals(
            corrupt,
            Files.readAllBytes(
                store.getPaths()
                    .getIndexFile()));

        Files.delete(
            store.getPaths()
                .getIndexFile());
        store.save(replacement);
        byte[] supported = Files.readAllBytes(
            store.getPaths()
                .getIndexFile());
        String newerText = new String(supported, StandardCharsets.UTF_8)
            .replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2");
        byte[] newer = newerText.getBytes(StandardCharsets.UTF_8);
        Files.write(
            store.getPaths()
                .getIndexFile(),
            newer);

        assertEquals(
            PersistenceLoadStatus.NEWER_SCHEMA,
            store.load()
                .getStatus());
        assertThrows(PersistenceException.class, () -> store.save(replacement));
        assertArrayEquals(
            newer,
            Files.readAllBytes(
                store.getPaths()
                    .getIndexFile()));
    }

    @Test
    public void ambiguousDuplicateBindingIsCorruptAndCannotBeSilentlyReplaced() throws Exception {
        Path root = temporaryFolder.newFolder("ambiguous")
            .toPath();
        ProfileBindingIndexStore store = new ProfileBindingIndexStore(root);
        ProfileBindingIndex twoProfiles = ProfileBindingIndex.empty()
            .enroll(
                ProfileBindingKey.multiplayer("one.example.net", "world-one"),
                new WorldProfileIdentity("profile-one", "One", "one.example.net", "world-one", 1L),
                "enroll-one",
                2L,
                true)
            .enroll(
                ProfileBindingKey.multiplayer("two.example.net", "world-two"),
                new WorldProfileIdentity("profile-two", "Two", "two.example.net", "world-two", 1L),
                "enroll-two",
                3L,
                true);
        store.save(twoProfiles);

        Path primary = store.getPaths()
            .getIndexFile();
        JsonObject rootJson = new JsonParser().parse(new String(Files.readAllBytes(primary), StandardCharsets.UTF_8))
            .getAsJsonObject();
        JsonArray bindings = rootJson.getAsJsonArray("bindings");
        JsonObject first = bindings.get(0)
            .getAsJsonObject();
        JsonObject second = bindings.get(1)
            .getAsJsonObject();
        second.add("enrollmentKey", cloneJson(first.get("enrollmentKey")));
        second.add("currentKey", cloneJson(first.get("currentKey")));
        second.getAsJsonObject("identity")
            .addProperty(
                "serverAddress",
                first.getAsJsonObject("identity")
                    .get("serverAddress")
                    .getAsString());
        second.getAsJsonObject("identity")
            .addProperty(
                "worldFingerprint",
                first.getAsJsonObject("identity")
                    .get("worldFingerprint")
                    .getAsString());
        byte[] ambiguous = (rootJson.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(primary, ambiguous);

        PersistenceLoadResult<ProfileBindingIndex> loaded = store.load();
        assertEquals(PersistenceLoadStatus.CORRUPT, loaded.getStatus());
        assertTrue(
            loaded.getDiagnostic()
                .contains("ambiguous duplicate world key"));
        assertThrows(
            PersistenceException.class,
            () -> store.save(singleplayerIndex("replacement", "marker-replacement", 10L)));
        assertArrayEquals(ambiguous, Files.readAllBytes(primary));
    }

    @Test
    public void staleTemporaryOrBackupWithoutPrimaryRequiresExplicitInspection() throws Exception {
        Path root = temporaryFolder.newFolder("orphaned-state")
            .toPath();
        ProfileBindingIndexStore store = new ProfileBindingIndexStore(root);
        ProfileBindingIndex index = singleplayerIndex("local", "marker-local", 10L);
        Files.createDirectories(
            store.getPaths()
                .getIndexFile()
                .getParent());
        byte[] sentinel = "inspect-me".getBytes(StandardCharsets.UTF_8);
        Files.write(
            store.getPaths()
                .getTemporaryFile(),
            sentinel);

        assertThrows(PersistenceException.class, () -> store.save(index));
        assertArrayEquals(
            sentinel,
            Files.readAllBytes(
                store.getPaths()
                    .getTemporaryFile()));
        assertFalse(
            Files.exists(
                store.getPaths()
                    .getIndexFile()));

        Files.delete(
            store.getPaths()
                .getTemporaryFile());
        Files.write(
            store.getPaths()
                .getBackupFile(),
            sentinel);
        assertThrows(PersistenceException.class, () -> store.save(index));
        assertArrayEquals(
            sentinel,
            Files.readAllBytes(
                store.getPaths()
                    .getBackupFile()));
        assertFalse(
            Files.exists(
                store.getPaths()
                    .getIndexFile()));
    }

    private static ProfileBindingIndex singleplayerIndex(String profileId, String marker, long createdAt) {
        return ProfileBindingIndex.empty()
            .enroll(
                ProfileBindingKey.singleplayer("instances/test/saves/" + profileId, marker),
                new WorldProfileIdentity(profileId, profileId, "singleplayer", marker, createdAt),
                "confirm-" + profileId,
                createdAt + 1L,
                true);
    }

    private static com.google.gson.JsonElement cloneJson(com.google.gson.JsonElement value) {
        return new JsonParser().parse(value.toString());
    }
}
