package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, explicitly confirmed lookup index from opaque client world keys to stable profile identities. */
public final class ProfileBindingIndex {

    static final int SCHEMA_VERSION = 1;
    static final String DOCUMENT_KIND = "profile-binding-index";

    private final int schemaVersion;
    private final String documentKind;
    private final long writtenAtEpochMillis;
    private final long revision;
    private final List<ProfileBindingEntry> bindings;
    private final List<ProfileBindingReassociationRecord> reassociations;

    private ProfileBindingIndex(int schemaVersion, String documentKind, long writtenAtEpochMillis, long revision,
        List<ProfileBindingEntry> bindings, List<ProfileBindingReassociationRecord> reassociations) {
        this.schemaVersion = schemaVersion;
        this.documentKind = documentKind;
        this.writtenAtEpochMillis = writtenAtEpochMillis;
        this.revision = revision;
        this.bindings = immutableCopy(bindings, "bindings");
        this.reassociations = immutableCopy(reassociations, "reassociations");
        validate();
    }

    public static ProfileBindingIndex empty() {
        return new ProfileBindingIndex(
            SCHEMA_VERSION,
            DOCUMENT_KIND,
            0L,
            0L,
            Collections.<ProfileBindingEntry>emptyList(),
            Collections.<ProfileBindingReassociationRecord>emptyList());
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getDocumentKind() {
        return documentKind;
    }

    public long getWrittenAtEpochMillis() {
        return writtenAtEpochMillis;
    }

    public long getRevision() {
        return revision;
    }

    public List<ProfileBindingEntry> getBindings() {
        return Collections.unmodifiableList(bindings);
    }

    public List<ProfileBindingReassociationRecord> getReassociations() {
        return Collections.unmodifiableList(reassociations);
    }

    public Optional<WorldProfileIdentity> find(ProfileBindingKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        key.validate();
        WorldProfileIdentity match = null;
        for (ProfileBindingEntry binding : bindings) {
            if (binding.getKey()
                .equals(key)) {
                if (match != null) {
                    throw new IllegalStateException("profile binding index is ambiguous for the supplied opaque key");
                }
                match = binding.getIdentity();
            }
        }
        return Optional.ofNullable(match);
    }

    public ProfileBindingIndex enroll(ProfileBindingKey key, WorldProfileIdentity identity, String confirmationId,
        long confirmedAtEpochMillis, boolean userConfirmed) {
        if (key == null || identity == null) {
            throw new IllegalArgumentException("key and identity must not be null");
        }
        requireNextConfirmation(confirmationId, confirmedAtEpochMillis, userConfirmed, "enrollment");
        if (find(key).isPresent()) {
            throw new IllegalArgumentException("opaque world key is already enrolled");
        }
        for (ProfileBindingEntry binding : bindings) {
            if (binding.getIdentity()
                .getProfileId()
                .equals(identity.getProfileId())) {
                throw new IllegalArgumentException("profile is already enrolled; explicit reassociation is required");
            }
        }
        ProfileBindingEntry enrolled = new ProfileBindingEntry(
            key,
            key,
            identity,
            confirmationId,
            confirmedAtEpochMillis,
            userConfirmed);
        List<ProfileBindingEntry> nextBindings = new ArrayList<>(bindings);
        nextBindings.add(enrolled);
        return next(confirmedAtEpochMillis, nextBindings, reassociations);
    }

    public ProfileBindingIndex reassociate(ProfileBindingKey previousKey, ProfileBindingKey newKey,
        WorldProfileIdentity replacementIdentity, String confirmationId, long confirmedAtEpochMillis,
        boolean userConfirmed) {
        if (previousKey == null || newKey == null || replacementIdentity == null) {
            throw new IllegalArgumentException("previousKey, newKey, and replacementIdentity must not be null");
        }
        requireNextConfirmation(confirmationId, confirmedAtEpochMillis, userConfirmed, "reassociation");
        if (previousKey.equals(newKey)) {
            throw new IllegalArgumentException("reassociation must change the opaque world key");
        }
        if (find(newKey).isPresent()) {
            throw new IllegalArgumentException("replacement opaque world key is already enrolled");
        }

        int bindingIndex = -1;
        ProfileBindingEntry existing = null;
        for (int index = 0; index < bindings.size(); index++) {
            ProfileBindingEntry candidate = bindings.get(index);
            if (candidate.getKey()
                .equals(previousKey)) {
                bindingIndex = index;
                existing = candidate;
                break;
            }
        }
        if (existing == null) {
            throw new IllegalArgumentException("previous opaque world key is not enrolled");
        }
        WorldProfileIdentity previousIdentity = existing.getIdentity();
        if (!previousIdentity.getProfileId()
            .equals(replacementIdentity.getProfileId())) {
            throw new IllegalArgumentException("reassociation must preserve the stable profileId");
        }
        if (previousIdentity.getCreatedAtEpochMillis() != replacementIdentity.getCreatedAtEpochMillis()) {
            throw new IllegalArgumentException("reassociation must preserve identity creation time");
        }
        ProfileBindingEntry replacement = existing.reassociated(newKey, replacementIdentity);
        ProfileBindingReassociationRecord record = new ProfileBindingReassociationRecord(
            previousIdentity.getProfileId(),
            previousKey,
            newKey,
            confirmationId,
            confirmedAtEpochMillis,
            userConfirmed);
        List<ProfileBindingEntry> nextBindings = new ArrayList<>(bindings);
        nextBindings.set(bindingIndex, replacement);
        List<ProfileBindingReassociationRecord> nextReassociations = new ArrayList<>(reassociations);
        nextReassociations.add(record);
        return next(confirmedAtEpochMillis, nextBindings, nextReassociations);
    }

    void validate() {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("profile binding schemaVersion must be " + SCHEMA_VERSION);
        }
        if (!DOCUMENT_KIND.equals(documentKind)) {
            throw new IllegalArgumentException("profile binding documentKind must be '" + DOCUMENT_KIND + "'");
        }
        PersistenceValidation.requireNonNegative(writtenAtEpochMillis, "binding index writtenAtEpochMillis");
        PersistenceValidation.requireNonNegative(revision, "binding index revision");
        PersistenceValidation.requireList(bindings, "profile bindings");
        PersistenceValidation.requireList(reassociations, "profile binding reassociations");
        long expectedRevision;
        try {
            expectedRevision = Math.addExact((long) bindings.size(), (long) reassociations.size());
        } catch (ArithmeticException impossibleSize) {
            throw new IllegalArgumentException("profile binding index is too large", impossibleSize);
        }
        if (revision != expectedRevision) {
            throw new IllegalArgumentException("profile binding revision does not match its confirmed history");
        }

        Set<ProfileBindingKey> activeKeys = new HashSet<>();
        Set<String> profileIds = new HashSet<>();
        Set<String> confirmationIds = new HashSet<>();
        Map<String, ProfileBindingEntry> entriesByProfile = new HashMap<>();
        for (ProfileBindingEntry binding : bindings) {
            binding.validateBase();
            String profileId = binding.getIdentity()
                .getProfileId();
            if (!activeKeys.add(binding.getKey())) {
                throw new IllegalArgumentException("profile binding index contains an ambiguous duplicate world key");
            }
            if (!profileIds.add(profileId)) {
                throw new IllegalArgumentException("profile binding index contains duplicate active profileId");
            }
            if (!confirmationIds.add(binding.getEnrollmentConfirmationId())) {
                throw new IllegalArgumentException("profile binding confirmationId is duplicated");
            }
            if (binding.getEnrolledAtEpochMillis() > writtenAtEpochMillis) {
                throw new IllegalArgumentException("profile binding enrollment occurs after writtenAtEpochMillis");
            }
            entriesByProfile.put(profileId, binding);
        }

        Map<String, ProfileBindingKey> chainCursor = new HashMap<>();
        Map<String, Long> lastConfirmation = new HashMap<>();
        for (ProfileBindingEntry binding : bindings) {
            String profileId = binding.getIdentity()
                .getProfileId();
            chainCursor.put(profileId, binding.getEnrollmentKey());
            lastConfirmation.put(profileId, binding.getEnrolledAtEpochMillis());
        }
        for (ProfileBindingReassociationRecord reassociation : reassociations) {
            reassociation.validate();
            ProfileBindingEntry entry = entriesByProfile.get(reassociation.getProfileId());
            if (entry == null) {
                throw new IllegalArgumentException("profile binding reassociation refers to an unknown profileId");
            }
            ProfileBindingKey expectedPrevious = chainCursor.get(reassociation.getProfileId());
            if (!reassociation.getPreviousKey()
                .equals(expectedPrevious)) {
                throw new IllegalArgumentException("profile binding reassociations do not form one continuous chain");
            }
            long previousTime = lastConfirmation.get(reassociation.getProfileId());
            if (reassociation.getConfirmedAtEpochMillis() < previousTime) {
                throw new IllegalArgumentException("profile binding reassociations must be chronological per profile");
            }
            if (reassociation.getConfirmedAtEpochMillis() > writtenAtEpochMillis) {
                throw new IllegalArgumentException("profile binding reassociation occurs after writtenAtEpochMillis");
            }
            if (!confirmationIds.add(reassociation.getConfirmationId())) {
                throw new IllegalArgumentException("profile binding confirmationId is duplicated");
            }
            chainCursor.put(reassociation.getProfileId(), reassociation.getNewKey());
            lastConfirmation.put(reassociation.getProfileId(), reassociation.getConfirmedAtEpochMillis());
        }
        for (ProfileBindingEntry binding : bindings) {
            String profileId = binding.getIdentity()
                .getProfileId();
            if (!binding.getKey()
                .equals(chainCursor.get(profileId))) {
                throw new IllegalArgumentException("profile binding history does not end at its active world key");
            }
        }
    }

    private void requireNextConfirmation(String confirmationId, long confirmedAtEpochMillis, boolean userConfirmed,
        String operation) {
        PersistenceValidation.requireStableId(confirmationId, operation + " confirmationId");
        PersistenceValidation.requireNonNegative(confirmedAtEpochMillis, operation + " confirmedAtEpochMillis");
        if (!userConfirmed) {
            throw new IllegalArgumentException("profile binding " + operation + " must be explicitly user-confirmed");
        }
        if (confirmedAtEpochMillis < writtenAtEpochMillis) {
            throw new IllegalArgumentException("profile binding confirmations must not move backwards in time");
        }
        for (ProfileBindingEntry binding : bindings) {
            if (binding.getEnrollmentConfirmationId()
                .equals(confirmationId)) {
                throw new IllegalArgumentException("profile binding confirmationId was already used");
            }
        }
        for (ProfileBindingReassociationRecord record : reassociations) {
            if (record.getConfirmationId()
                .equals(confirmationId)) {
                throw new IllegalArgumentException("profile binding confirmationId was already used");
            }
        }
    }

    private ProfileBindingIndex next(long writtenAt, List<ProfileBindingEntry> nextBindings,
        List<ProfileBindingReassociationRecord> nextReassociations) {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("profile binding index revision exhausted");
        }
        return new ProfileBindingIndex(
            SCHEMA_VERSION,
            DOCUMENT_KIND,
            writtenAt,
            revision + 1L,
            nextBindings,
            nextReassociations);
    }

    private static <T> List<T> immutableCopy(List<T> source, String field) {
        if (source == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        List<T> copy = new ArrayList<>(source.size());
        for (T value : source) {
            copy.add(Objects.requireNonNull(value, field + " value"));
        }
        return Collections.unmodifiableList(copy);
    }
}
