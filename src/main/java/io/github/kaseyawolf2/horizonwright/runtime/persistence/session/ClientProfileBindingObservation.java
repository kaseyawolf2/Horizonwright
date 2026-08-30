package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingIndex;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingKey;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/**
 * Explicit world evidence supplied by the live client boundary.
 *
 * <p>
 * Multiplayer callers must supply the configured endpoint and an explicit opaque world fingerprint. The constructor
 * proves that those facts produce the supplied opaque key; no fingerprint or server identity is inferred.
 * </p>
 */
public final class ClientProfileBindingObservation {

    private static final String PROBE_PROFILE_ID = "binding-observation-probe";
    private static final String PROBE_CONFIRMATION_ID = "binding-observation-confirmation";

    private final ProfileBindingKey key;
    private final String displayName;
    private final String serverAddress;
    private final String worldFingerprint;

    public ClientProfileBindingObservation(ProfileBindingKey key, String displayName, String serverAddress,
        String explicitWorldFingerprint) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        WorldProfileIdentity probe = new WorldProfileIdentity(
            PROBE_PROFILE_ID,
            displayName,
            serverAddress,
            explicitWorldFingerprint,
            0L);
        ProfileBindingIndex.empty()
            .enroll(key, probe, PROBE_CONFIRMATION_ID, 0L, true);
        this.key = key;
        this.displayName = probe.getDisplayName();
        this.serverAddress = probe.getServerAddress();
        this.worldFingerprint = probe.getWorldFingerprint();
    }

    public ProfileBindingKey getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public String getWorldFingerprint() {
        return worldFingerprint;
    }

    WorldProfileIdentity identity(String profileId, long createdAtEpochMillis) {
        return new WorldProfileIdentity(profileId, displayName, serverAddress, worldFingerprint, createdAtEpochMillis);
    }
}
