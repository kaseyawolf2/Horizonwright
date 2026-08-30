package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

/** An explicit, auditable user confirmation that a stable profile now belongs to a different server-world identity. */
public final class ProfileReassociation {

    private final String previousServerAddress;
    private final String previousWorldFingerprint;
    private final String newServerAddress;
    private final String newWorldFingerprint;
    private final String confirmationId;
    private final long confirmedAtEpochMillis;
    private final boolean userConfirmed;

    public ProfileReassociation(String previousServerAddress, String previousWorldFingerprint, String newServerAddress,
        String newWorldFingerprint, String confirmationId, long confirmedAtEpochMillis, boolean userConfirmed) {
        this.previousServerAddress = PersistenceValidation.requireText(previousServerAddress, "previousServerAddress");
        this.previousWorldFingerprint = PersistenceValidation
            .requireText(previousWorldFingerprint, "previousWorldFingerprint");
        this.newServerAddress = PersistenceValidation.requireText(newServerAddress, "newServerAddress");
        this.newWorldFingerprint = PersistenceValidation.requireText(newWorldFingerprint, "newWorldFingerprint");
        this.confirmationId = PersistenceValidation.requireStableId(confirmationId, "confirmationId");
        PersistenceValidation.requireNonNegative(confirmedAtEpochMillis, "confirmedAtEpochMillis");
        this.confirmedAtEpochMillis = confirmedAtEpochMillis;
        this.userConfirmed = userConfirmed;
        validate();
    }

    public String getPreviousServerAddress() {
        return previousServerAddress;
    }

    public String getPreviousWorldFingerprint() {
        return previousWorldFingerprint;
    }

    public String getNewServerAddress() {
        return newServerAddress;
    }

    public String getNewWorldFingerprint() {
        return newWorldFingerprint;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public long getConfirmedAtEpochMillis() {
        return confirmedAtEpochMillis;
    }

    public boolean isUserConfirmed() {
        return userConfirmed;
    }

    void validate() {
        PersistenceValidation.requireText(previousServerAddress, "reassociation previousServerAddress");
        PersistenceValidation.requireText(previousWorldFingerprint, "reassociation previousWorldFingerprint");
        PersistenceValidation.requireText(newServerAddress, "reassociation newServerAddress");
        PersistenceValidation.requireText(newWorldFingerprint, "reassociation newWorldFingerprint");
        PersistenceValidation.requireStableId(confirmationId, "reassociation confirmationId");
        PersistenceValidation.requireNonNegative(confirmedAtEpochMillis, "reassociation confirmedAtEpochMillis");
        if (!userConfirmed) {
            throw new IllegalArgumentException("profile reassociation must be explicitly user-confirmed");
        }
        if (previousServerAddress.equals(newServerAddress) && previousWorldFingerprint.equals(newWorldFingerprint)) {
            throw new IllegalArgumentException("profile reassociation must change the server or world fingerprint");
        }
    }

    boolean startsAt(String serverAddress, String worldFingerprint) {
        return previousServerAddress.equals(serverAddress) && previousWorldFingerprint.equals(worldFingerprint);
    }

    boolean endsAt(String serverAddress, String worldFingerprint) {
        return newServerAddress.equals(serverAddress) && newWorldFingerprint.equals(worldFingerprint);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProfileReassociation)) {
            return false;
        }
        ProfileReassociation that = (ProfileReassociation) other;
        return confirmedAtEpochMillis == that.confirmedAtEpochMillis && userConfirmed == that.userConfirmed
            && Objects.equals(previousServerAddress, that.previousServerAddress)
            && Objects.equals(previousWorldFingerprint, that.previousWorldFingerprint)
            && Objects.equals(newServerAddress, that.newServerAddress)
            && Objects.equals(newWorldFingerprint, that.newWorldFingerprint)
            && Objects.equals(confirmationId, that.confirmationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            previousServerAddress,
            previousWorldFingerprint,
            newServerAddress,
            newWorldFingerprint,
            confirmationId,
            confirmedAtEpochMillis,
            userConfirmed);
    }
}
