package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

/** Opaque audit record for one explicitly confirmed profile binding change. */
public final class ProfileBindingReassociationRecord {

    private final String profileId;
    private final ProfileBindingKey previousKey;
    private final ProfileBindingKey newKey;
    private final String confirmationId;
    private final long confirmedAtEpochMillis;
    private final boolean userConfirmed;

    ProfileBindingReassociationRecord(String profileId, ProfileBindingKey previousKey, ProfileBindingKey newKey,
        String confirmationId, long confirmedAtEpochMillis, boolean userConfirmed) {
        this.profileId = PersistenceValidation.requireStableId(profileId, "profileId");
        this.previousKey = Objects.requireNonNull(previousKey, "previousKey");
        this.newKey = Objects.requireNonNull(newKey, "newKey");
        this.confirmationId = PersistenceValidation.requireStableId(confirmationId, "confirmationId");
        PersistenceValidation.requireNonNegative(confirmedAtEpochMillis, "confirmedAtEpochMillis");
        this.confirmedAtEpochMillis = confirmedAtEpochMillis;
        this.userConfirmed = userConfirmed;
        validate();
    }

    public String getProfileId() {
        return profileId;
    }

    public ProfileBindingKey getPreviousKey() {
        return previousKey;
    }

    public ProfileBindingKey getNewKey() {
        return newKey;
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
        PersistenceValidation.requireStableId(profileId, "binding reassociation profileId");
        previousKey.validate();
        newKey.validate();
        PersistenceValidation.requireStableId(confirmationId, "binding reassociation confirmationId");
        PersistenceValidation
            .requireNonNegative(confirmedAtEpochMillis, "binding reassociation confirmedAtEpochMillis");
        if (!userConfirmed) {
            throw new IllegalArgumentException("profile binding reassociation must be explicitly user-confirmed");
        }
        if (previousKey.equals(newKey)) {
            throw new IllegalArgumentException("profile binding reassociation must change the opaque world key");
        }
    }
}
