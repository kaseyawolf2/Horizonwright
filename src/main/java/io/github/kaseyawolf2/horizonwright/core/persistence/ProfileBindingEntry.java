package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

/** One explicitly enrolled profile and its current opaque world lookup key. */
public final class ProfileBindingEntry {

    private final ProfileBindingKey enrollmentKey;
    private final ProfileBindingKey currentKey;
    private final WorldProfileIdentity identity;
    private final String enrollmentConfirmationId;
    private final long enrolledAtEpochMillis;
    private final boolean userConfirmed;

    ProfileBindingEntry(ProfileBindingKey enrollmentKey, ProfileBindingKey currentKey, WorldProfileIdentity identity,
        String enrollmentConfirmationId, long enrolledAtEpochMillis, boolean userConfirmed) {
        this.enrollmentKey = Objects.requireNonNull(enrollmentKey, "enrollmentKey");
        this.currentKey = Objects.requireNonNull(currentKey, "currentKey");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.enrollmentConfirmationId = PersistenceValidation
            .requireStableId(enrollmentConfirmationId, "enrollmentConfirmationId");
        PersistenceValidation.requireNonNegative(enrolledAtEpochMillis, "enrolledAtEpochMillis");
        this.enrolledAtEpochMillis = enrolledAtEpochMillis;
        this.userConfirmed = userConfirmed;
        validateBase();
    }

    public ProfileBindingKey getKey() {
        return currentKey;
    }

    public WorldProfileIdentity getIdentity() {
        return identity;
    }

    public String getEnrollmentConfirmationId() {
        return enrollmentConfirmationId;
    }

    public long getEnrolledAtEpochMillis() {
        return enrolledAtEpochMillis;
    }

    public boolean isUserConfirmed() {
        return userConfirmed;
    }

    ProfileBindingKey getEnrollmentKey() {
        return enrollmentKey;
    }

    ProfileBindingEntry reassociated(ProfileBindingKey replacementKey, WorldProfileIdentity replacementIdentity) {
        return new ProfileBindingEntry(
            enrollmentKey,
            replacementKey,
            replacementIdentity,
            enrollmentConfirmationId,
            enrolledAtEpochMillis,
            userConfirmed);
    }

    void validateBase() {
        enrollmentKey.validate();
        currentKey.validate();
        identity.validate();
        PersistenceValidation.requireStableId(enrollmentConfirmationId, "binding enrollmentConfirmationId");
        PersistenceValidation.requireNonNegative(enrolledAtEpochMillis, "binding enrolledAtEpochMillis");
        if (!userConfirmed) {
            throw new IllegalArgumentException("profile binding enrollment must be explicitly user-confirmed");
        }
        if (enrolledAtEpochMillis < identity.getCreatedAtEpochMillis()) {
            throw new IllegalArgumentException("profile binding enrollment predates identity creation");
        }
        if (!currentKey.matches(identity)) {
            throw new IllegalArgumentException("profile identity does not match its current opaque binding key");
        }
    }
}
