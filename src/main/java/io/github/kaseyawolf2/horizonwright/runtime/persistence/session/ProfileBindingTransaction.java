package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.util.regex.Pattern;

import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingKey;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingKind;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Durable intent spanning the global binding index and one profile document. */
final class ProfileBindingTransaction {

    enum Operation {
        ENROLL,
        REASSOCIATE
    }

    private static final Pattern STABLE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final Operation operation;
    private final long baseIndexRevision;
    private final ProfileBindingKind targetKind;
    private final String targetLocatorHash;
    private final String targetWorldMarkerHash;
    private final WorldProfileIdentity previousIdentity;
    private final WorldProfileIdentity targetIdentity;
    private final String confirmationId;
    private final long confirmedAtEpochMillis;

    ProfileBindingTransaction(Operation operation, long baseIndexRevision, ProfileBindingKey targetKey,
        WorldProfileIdentity previousIdentity, WorldProfileIdentity targetIdentity, String confirmationId,
        long confirmedAtEpochMillis) {
        if (operation == null || targetKey == null || targetIdentity == null) {
            throw new IllegalArgumentException("operation, targetKey, and targetIdentity are required");
        }
        if (baseIndexRevision < 0L || confirmedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("transaction revision and timestamp must not be negative");
        }
        if (!STABLE_ID.matcher(confirmationId == null ? "" : confirmationId)
            .matches()) {
            throw new IllegalArgumentException("transaction confirmationId is not persistence-safe");
        }
        this.operation = operation;
        this.baseIndexRevision = baseIndexRevision;
        this.targetKind = targetKey.getKind();
        this.targetLocatorHash = targetKey.getLocatorHash();
        this.targetWorldMarkerHash = targetKey.getWorldMarkerHash();
        this.previousIdentity = previousIdentity;
        this.targetIdentity = targetIdentity;
        this.confirmationId = confirmationId;
        this.confirmedAtEpochMillis = confirmedAtEpochMillis;
        validate();
    }

    private ProfileBindingTransaction(Operation operation, long baseIndexRevision, ProfileBindingKind targetKind,
        String targetLocatorHash, String targetWorldMarkerHash, WorldProfileIdentity previousIdentity,
        WorldProfileIdentity targetIdentity, String confirmationId, long confirmedAtEpochMillis) {
        this.operation = operation;
        this.baseIndexRevision = baseIndexRevision;
        this.targetKind = targetKind;
        this.targetLocatorHash = targetLocatorHash;
        this.targetWorldMarkerHash = targetWorldMarkerHash;
        this.previousIdentity = previousIdentity;
        this.targetIdentity = targetIdentity;
        this.confirmationId = confirmationId;
        this.confirmedAtEpochMillis = confirmedAtEpochMillis;
        validate();
    }

    static ProfileBindingTransaction restore(Operation operation, long baseIndexRevision, ProfileBindingKind targetKind,
        String targetLocatorHash, String targetWorldMarkerHash, WorldProfileIdentity previousIdentity,
        WorldProfileIdentity targetIdentity, String confirmationId, long confirmedAtEpochMillis) {
        return new ProfileBindingTransaction(
            operation,
            baseIndexRevision,
            targetKind,
            targetLocatorHash,
            targetWorldMarkerHash,
            previousIdentity,
            targetIdentity,
            confirmationId,
            confirmedAtEpochMillis);
    }

    Operation getOperation() {
        return operation;
    }

    long getBaseIndexRevision() {
        return baseIndexRevision;
    }

    ProfileBindingKind getTargetKind() {
        return targetKind;
    }

    String getTargetLocatorHash() {
        return targetLocatorHash;
    }

    String getTargetWorldMarkerHash() {
        return targetWorldMarkerHash;
    }

    WorldProfileIdentity getTargetIdentity() {
        return targetIdentity;
    }

    WorldProfileIdentity getPreviousIdentity() {
        return previousIdentity;
    }

    String getConfirmationId() {
        return confirmationId;
    }

    long getConfirmedAtEpochMillis() {
        return confirmedAtEpochMillis;
    }

    boolean matches(ClientProfileBindingObservation observation) {
        ProfileBindingKey key = observation.getKey();
        return targetKind == key.getKind() && targetLocatorHash.equals(key.getLocatorHash())
            && targetWorldMarkerHash.equals(key.getWorldMarkerHash())
            && targetIdentityMatchesPersistedKey();
    }

    private boolean targetIdentityMatchesPersistedKey() {
        try {
            ProfileBindingKey identityKey;
            if (targetKind == ProfileBindingKind.MULTIPLAYER) {
                identityKey = ProfileBindingKey
                    .multiplayer(targetIdentity.getServerAddress(), targetIdentity.getWorldFingerprint());
                return targetLocatorHash.equals(identityKey.getLocatorHash())
                    && targetWorldMarkerHash.equals(identityKey.getWorldMarkerHash());
            }
            if (!"singleplayer".equalsIgnoreCase(targetIdentity.getServerAddress())) {
                return false;
            }
            identityKey = ProfileBindingKey
                .singleplayer("transaction-identity-check", targetIdentity.getWorldFingerprint());
            return targetWorldMarkerHash.equals(identityKey.getWorldMarkerHash());
        } catch (IllegalArgumentException invalidIdentity) {
            return false;
        }
    }

    private void validate() {
        if (operation == null || targetKind == null || targetIdentity == null) {
            throw new IllegalArgumentException("transaction operation, key kind, and identity are required");
        }
        if (baseIndexRevision < 0L || confirmedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("transaction revision and timestamp must not be negative");
        }
        if (!HASH.matcher(targetLocatorHash == null ? "" : targetLocatorHash)
            .matches()
            || !HASH.matcher(targetWorldMarkerHash == null ? "" : targetWorldMarkerHash)
                .matches()) {
            throw new IllegalArgumentException("transaction binding hashes must be lowercase SHA-256 values");
        }
        if (!STABLE_ID.matcher(confirmationId == null ? "" : confirmationId)
            .matches()) {
            throw new IllegalArgumentException("transaction confirmationId is not persistence-safe");
        }
        if (confirmedAtEpochMillis < targetIdentity.getCreatedAtEpochMillis()) {
            throw new IllegalArgumentException("transaction confirmation predates its target identity");
        }
        if ((operation == Operation.ENROLL) != (previousIdentity == null)) {
            throw new IllegalArgumentException("only a reassociation transaction carries a previous identity");
        }
        if (previousIdentity != null && (!previousIdentity.getProfileId()
            .equals(targetIdentity.getProfileId())
            || previousIdentity.getCreatedAtEpochMillis() != targetIdentity.getCreatedAtEpochMillis())) {
            throw new IllegalArgumentException("transaction reassociation must preserve stable profile identity");
        }
    }
}
