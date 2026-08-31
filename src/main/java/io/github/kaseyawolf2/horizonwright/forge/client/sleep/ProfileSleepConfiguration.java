package io.github.kaseyawolf2.horizonwright.forge.client.sleep;

import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadResult;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Resolves registered beds from the exact active profile partition. */
public final class ProfileSleepConfiguration {

    private final HorizonwrightPersistenceStore store;
    private final WorldProfileIdentity identity;

    public ProfileSleepConfiguration(HorizonwrightPersistenceStore store, WorldProfileIdentity identity) {
        if (store == null || identity == null) throw new IllegalArgumentException("store and identity are required");
        this.store = store;
        this.identity = identity;
    }

    public NamedLocation resolveBed(String locationId) {
        ProfileEnvelope profile = requireProfile();
        for (NamedLocation location : profile.getNamedLocations()) {
            if (location.getId()
                .equals(locationId)) return location;
        }
        throw new IllegalStateException("profile has no registered bed location '" + locationId + "'");
    }

    private ProfileEnvelope requireProfile() {
        PersistenceLoadResult<ProfileEnvelope> loaded = store
            .loadProfile(store.pathsForProfile(identity.getProfileId()));
        if (!loaded.isLoaded())
            throw new IllegalStateException("active profile cannot be read: " + loaded.getDiagnostic());
        ProfileEnvelope profile = loaded.getValue();
        if (!identity.equals(profile.getIdentity())) throw new IllegalStateException("active profile identity changed");
        return profile;
    }
}
