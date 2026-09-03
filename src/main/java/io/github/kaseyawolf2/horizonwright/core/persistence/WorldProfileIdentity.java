package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

/**
 * Stable profile identity plus the currently confirmed server-world binding.
 *
 * <p>
 * The opaque profile id survives address changes. The world fingerprint distinguishes a reset world at the same
 * address from the world the user previously approved.
 * </p>
 */
public final class WorldProfileIdentity {

    private final String profileId;
    private final String displayName;
    private final String serverAddress;
    private final String worldFingerprint;
    private final long createdAtEpochMillis;

    public WorldProfileIdentity(String profileId, String displayName, String serverAddress, String worldFingerprint,
        long createdAtEpochMillis) {
        this.profileId = PersistenceValidation.requireStableId(profileId, "profileId");
        this.displayName = PersistenceValidation.requireText(displayName, "displayName");
        this.serverAddress = PersistenceValidation.requireText(serverAddress, "serverAddress");
        this.worldFingerprint = PersistenceValidation.requireText(worldFingerprint, "worldFingerprint");
        PersistenceValidation.requireNonNegative(createdAtEpochMillis, "createdAtEpochMillis");
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public String getProfileId() {
        return profileId;
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

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    void validate() {
        PersistenceValidation.requireStableId(profileId, "profile identity profileId");
        PersistenceValidation.requireText(displayName, "profile identity displayName");
        PersistenceValidation.requireText(serverAddress, "profile identity serverAddress");
        PersistenceValidation.requireText(worldFingerprint, "profile identity worldFingerprint");
        PersistenceValidation.requireNonNegative(createdAtEpochMillis, "profile identity createdAtEpochMillis");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorldProfileIdentity)) {
            return false;
        }
        WorldProfileIdentity that = (WorldProfileIdentity) other;
        return createdAtEpochMillis == that.createdAtEpochMillis && Objects.equals(profileId, that.profileId)
            && Objects.equals(displayName, that.displayName)
            && Objects.equals(serverAddress, that.serverAddress)
            && Objects.equals(worldFingerprint, that.worldFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, displayName, serverAddress, worldFingerprint, createdAtEpochMillis);
    }

    @Override
    public String toString() {
        return "WorldProfileIdentity{" + profileId + ":" + displayName + ":" + worldFingerprint + '}';
    }
}
