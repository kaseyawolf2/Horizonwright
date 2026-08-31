package io.github.kaseyawolf2.horizonwright.runtime.persistence.profile;

import java.util.ArrayList;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceException;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadResult;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileStatePaths;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Identity-bound atomic editor for named assets in one active world profile. */
public final class ProfileAssetEditor {

    public interface Clock {

        long nowEpochMillis();
    }

    private final HorizonwrightPersistenceStore store;
    private final WorldProfileIdentity identity;
    private final ProfileStatePaths paths;
    private final Clock clock;

    public ProfileAssetEditor(HorizonwrightPersistenceStore store, WorldProfileIdentity identity, Clock clock) {
        if (store == null || identity == null || clock == null) {
            throw new IllegalArgumentException("store, identity, and clock are required");
        }
        this.store = store;
        this.identity = identity;
        this.paths = store.pathsForProfile(identity.getProfileId());
        this.clock = clock;
    }

    public synchronized ProfileEnvelope load() {
        return requireExactProfile();
    }

    /** Loads, merges, validates, and atomically saves one update without exposing a partially valid profile. */
    public synchronized ProfileEnvelope apply(ProfileAssetUpdate update) {
        if (update == null) throw new IllegalArgumentException("update must not be null");
        ProfileEnvelope previous = requireExactProfile();
        long now = clock.nowEpochMillis();
        if (now < 0L) throw new ProfileAssetEditingException("profile editor clock returned a negative timestamp");
        long writtenAt = Math.max(previous.getWrittenAtEpochMillis(), now);
        ProfileEnvelope replacement = new ProfileEnvelope(
            writtenAt,
            previous.getIdentity(),
            previous.getReassociations(),
            mergeLocations(previous.getNamedLocations(), update.getLocations()),
            previous.getNamedRoutes(),
            mergeLoadouts(previous.getNamedLoadouts(), update.getLoadouts()),
            mergeStorage(previous.getNamedStorageEndpoints(), update.getStorageEndpoints()),
            mergeStations(previous.getNamedRepairStations(), update.getRepairStations()));
        try {
            store.saveProfile(paths, replacement);
        } catch (PersistenceException failure) {
            throw new ProfileAssetEditingException("could not atomically save the profile asset update", failure);
        }
        return replacement;
    }

    private ProfileEnvelope requireExactProfile() {
        PersistenceLoadResult<ProfileEnvelope> loaded = store.loadProfile(paths);
        if (!loaded.isLoaded()) {
            throw new ProfileAssetEditingException("profile is unavailable: " + loaded.getDiagnostic());
        }
        ProfileEnvelope profile = loaded.getValue();
        if (!sameIdentity(identity, profile.getIdentity())) {
            throw new ProfileAssetEditingException("profile identity changed; reopen the editor for this world");
        }
        return profile;
    }

    private static List<NamedLocation> mergeLocations(List<NamedLocation> existing, List<NamedLocation> updates) {
        List<NamedLocation> merged = new ArrayList<>(existing);
        for (NamedLocation update : updates) {
            replaceLocation(merged, update);
        }
        return merged;
    }

    private static List<NamedLoadout> mergeLoadouts(List<NamedLoadout> existing, List<NamedLoadout> updates) {
        List<NamedLoadout> merged = new ArrayList<>(existing);
        for (NamedLoadout update : updates) {
            replaceLoadout(merged, update);
        }
        return merged;
    }

    private static List<NamedStorageEndpoint> mergeStorage(List<NamedStorageEndpoint> existing,
        List<NamedStorageEndpoint> updates) {
        List<NamedStorageEndpoint> merged = new ArrayList<>(existing);
        for (NamedStorageEndpoint update : updates) {
            replaceStorage(merged, update);
        }
        return merged;
    }

    private static List<NamedRepairStation> mergeStations(List<NamedRepairStation> existing,
        List<NamedRepairStation> updates) {
        List<NamedRepairStation> merged = new ArrayList<>(existing);
        for (NamedRepairStation update : updates) {
            replaceStation(merged, update);
        }
        return merged;
    }

    private static void replaceLocation(List<NamedLocation> values, NamedLocation replacement) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index)
                .getId()
                .equals(replacement.getId())) {
                values.set(index, replacement);
                return;
            }
        }
        values.add(replacement);
    }

    private static void replaceLoadout(List<NamedLoadout> values, NamedLoadout replacement) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index)
                .getId()
                .equals(replacement.getId())) {
                values.set(index, replacement);
                return;
            }
        }
        values.add(replacement);
    }

    private static void replaceStorage(List<NamedStorageEndpoint> values, NamedStorageEndpoint replacement) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index)
                .getId()
                .equals(replacement.getId())) {
                values.set(index, replacement);
                return;
            }
        }
        values.add(replacement);
    }

    private static void replaceStation(List<NamedRepairStation> values, NamedRepairStation replacement) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index)
                .getId()
                .equals(replacement.getId())) {
                values.set(index, replacement);
                return;
            }
        }
        values.add(replacement);
    }

    private static boolean sameIdentity(WorldProfileIdentity expected, WorldProfileIdentity actual) {
        return expected.getProfileId()
            .equals(actual.getProfileId())
            && expected.getServerAddress()
                .equals(actual.getServerAddress())
            && expected.getWorldFingerprint()
                .equals(actual.getWorldFingerprint());
    }
}
