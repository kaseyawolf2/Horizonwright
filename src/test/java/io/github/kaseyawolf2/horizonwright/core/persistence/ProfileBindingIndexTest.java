package io.github.kaseyawolf2.horizonwright.core.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileBindingIndexTest {

    @Test
    public void locatorsNormalizeToOpaqueHashesWithoutRetainingRawValues() {
        ProfileBindingKey windows = ProfileBindingKey
            .singleplayer(" D:\\Instances\\GTNH\\saves\\.\\Smoke World\\ ", "world-marker-A");
        ProfileBindingKey normalized = ProfileBindingKey
            .singleplayer("d:/instances/gtnh/saves/smoke world", "world-marker-A");
        ProfileBindingKey endpointDefault = ProfileBindingKey.multiplayer("EXAMPLE.com.", "world-fingerprint-A");
        ProfileBindingKey endpointExplicit = ProfileBindingKey.multiplayer("example.com:25565", "world-fingerprint-A");
        ProfileBindingKey ipv6 = ProfileBindingKey.multiplayer("[2001:DB8::1]", "world-fingerprint-A");

        assertEquals(windows, normalized);
        assertEquals(endpointDefault, endpointExplicit);
        assertEquals(ipv6, ProfileBindingKey.multiplayer("[2001:db8::1]:25565", "world-fingerprint-A"));
        assertEquals(
            64,
            windows.getLocatorHash()
                .length());
        assertEquals(
            64,
            windows.getWorldMarkerHash()
                .length());
        assertFalse(
            windows.getLocatorHash()
                .contains("instances"));
        assertFalse(
            windows.getWorldMarkerHash()
                .contains("marker"));
        assertNotEquals(endpointDefault, ProfileBindingKey.multiplayer("example.com:25566", "world-fingerprint-A"));
        assertNotEquals(endpointDefault, ProfileBindingKey.multiplayer("example.com", "world-fingerprint-B"));

        assertThrows(IllegalArgumentException.class, () -> ProfileBindingKey.multiplayer("example.com", " "));
        assertThrows(
            IllegalArgumentException.class,
            () -> ProfileBindingKey.multiplayer("user:secret@example.com", "world-fingerprint-A"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ProfileBindingKey.multiplayer("example.com/path", "world-fingerprint-A"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ProfileBindingKey.singleplayer("saves/../other-world", "world-marker-A"));
    }

    @Test
    public void enrollmentRequiresAnExplicitMatchingIdentityAndNeverInfersMultiplayerWorlds() {
        ProfileBindingIndex empty = ProfileBindingIndex.empty();
        ProfileBindingKey key = ProfileBindingKey.multiplayer("play.example.net", "opaque-world-1");
        WorldProfileIdentity identity = multiplayerIdentity(
            "main-profile",
            "Main world",
            "PLAY.EXAMPLE.NET:25565",
            "opaque-world-1",
            100L);

        assertThrows(IllegalArgumentException.class, () -> empty.enroll(key, identity, "enroll-main", 101L, false));
        assertThrows(
            IllegalArgumentException.class,
            () -> empty.enroll(
                key,
                multiplayerIdentity("main-profile", "Wrong endpoint", "other.example.net", "opaque-world-1", 100L),
                "enroll-main",
                101L,
                true));
        assertThrows(
            IllegalArgumentException.class,
            () -> empty.enroll(
                key,
                multiplayerIdentity("main-profile", "Wrong fingerprint", "play.example.net", "opaque-world-2", 100L),
                "enroll-main",
                101L,
                true));

        ProfileBindingIndex enrolled = empty.enroll(key, identity, "enroll-main", 101L, true);

        assertEquals(1L, enrolled.getRevision());
        assertEquals(
            identity,
            enrolled.find(key)
                .get());
        assertThrows(IllegalArgumentException.class, () -> enrolled.enroll(key, identity, "duplicate-key", 102L, true));
        assertThrows(
            IllegalArgumentException.class,
            () -> enrolled.enroll(
                ProfileBindingKey.multiplayer("play.example.net", "opaque-world-2"),
                multiplayerIdentity("main-profile", "Same profile", "play.example.net", "opaque-world-2", 100L),
                "implicit-rebind",
                102L,
                true));
    }

    @Test
    public void reassociationAtomicallyReplacesTheActiveKeyAndPreservesAnOpaqueAuditChain() {
        ProfileBindingKey oldKey = ProfileBindingKey.multiplayer("old.example.net", "opaque-world-1");
        ProfileBindingKey newKey = ProfileBindingKey.multiplayer("new.example.net:25570", "opaque-world-2");
        WorldProfileIdentity oldIdentity = multiplayerIdentity(
            "stable-profile",
            "Before",
            "old.example.net",
            "opaque-world-1",
            10L);
        WorldProfileIdentity newIdentity = multiplayerIdentity(
            "stable-profile",
            "After",
            "new.example.net:25570",
            "opaque-world-2",
            10L);
        ProfileBindingIndex enrolled = ProfileBindingIndex.empty()
            .enroll(oldKey, oldIdentity, "enroll-stable", 11L, true);

        assertThrows(
            IllegalArgumentException.class,
            () -> enrolled.reassociate(oldKey, newKey, newIdentity, "move-stable", 12L, false));
        ProfileBindingIndex moved = enrolled.reassociate(oldKey, newKey, newIdentity, "move-stable", 12L, true);

        assertFalse(
            moved.find(oldKey)
                .isPresent());
        assertEquals(
            newIdentity,
            moved.find(newKey)
                .get());
        assertEquals(2L, moved.getRevision());
        assertEquals(
            1,
            moved.getReassociations()
                .size());
        ProfileBindingReassociationRecord record = moved.getReassociations()
            .get(0);
        assertEquals(oldKey, record.getPreviousKey());
        assertEquals(newKey, record.getNewKey());
        assertEquals("move-stable", record.getConfirmationId());
        assertTrue(record.isUserConfirmed());

        assertThrows(
            IllegalArgumentException.class,
            () -> moved.reassociate(
                newKey,
                ProfileBindingKey.multiplayer("third.example.net", "opaque-world-3"),
                multiplayerIdentity("another-profile", "Wrong profile", "third.example.net", "opaque-world-3", 10L),
                "wrong-profile",
                13L,
                true));
        assertThrows(
            IllegalArgumentException.class,
            () -> moved.reassociate(
                newKey,
                ProfileBindingKey.multiplayer("third.example.net", "opaque-world-3"),
                multiplayerIdentity("stable-profile", "Wrong creation", "third.example.net", "opaque-world-3", 9L),
                "wrong-creation",
                13L,
                true));
    }

    @Test
    public void singleplayerBindingUsesTheLocatorOnlyForSelectionAndStoresAnOpaqueIdentity() {
        ProfileBindingKey key = ProfileBindingKey.singleplayer("instances/pack/saves/world", "world-marker-1");
        WorldProfileIdentity identity = new WorldProfileIdentity(
            "local-profile",
            "Local world",
            "singleplayer",
            "world-marker-1",
            5L);

        ProfileBindingIndex index = ProfileBindingIndex.empty()
            .enroll(key, identity, "enroll-local", 6L, true);

        assertEquals(
            identity,
            index.find(key)
                .get());
        assertThrows(
            IllegalArgumentException.class,
            () -> ProfileBindingIndex.empty()
                .enroll(
                    key,
                    new WorldProfileIdentity(
                        "local-profile",
                        "Leaky identity",
                        "instances/pack/saves/world",
                        "world-marker-1",
                        5L),
                    "enroll-local",
                    6L,
                    true));
    }

    private static WorldProfileIdentity multiplayerIdentity(String profileId, String displayName, String endpoint,
        String fingerprint, long createdAt) {
        return new WorldProfileIdentity(profileId, displayName, endpoint, fingerprint, createdAt);
    }
}
