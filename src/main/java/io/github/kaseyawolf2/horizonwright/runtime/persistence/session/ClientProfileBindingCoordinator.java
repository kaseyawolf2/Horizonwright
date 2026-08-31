package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRoute;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceException;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadResult;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadStatus;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingEntry;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingIndex;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingIndexStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingKey;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingReassociationRecord;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileReassociation;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileStatePaths;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/**
 * Explicit, production-neutral selection and confirmation boundary for client world profiles.
 *
 * <p>
 * Observation never enrolls or reassociates. Confirmed updates first persist a durable intent, then the opaque global
 * index, then {@code profile.json}. An interruption therefore leaves an inspectable transaction that must be explicitly
 * recovered; corrupt or newer documents are never overwritten.
 * </p>
 */
public final class ClientProfileBindingCoordinator {

    interface CommitHook {

        void afterIndexCommitted();
    }

    private static final CommitHook NOOP_HOOK = new CommitHook() {

        @Override
        public void afterIndexCommitted() {}
    };

    private final ProfileBindingIndexStore indexStore;
    private final HorizonwrightPersistenceStore profileStore;
    private final StableRandomIdSource profileIdSource;
    private final StableRandomIdSource confirmationIdSource;
    private final RuntimeSessionClock clock;
    private final ProfileBindingTransactionStore transactionStore;
    private final CommitHook commitHook;

    private ClientProfileBindingObservation observation;
    private ProfileBindingIndex observedIndex;
    private ProfileBindingTransaction pendingTransaction;
    private List<String> candidateProfileIds = Collections.emptyList();
    private ClientProfileBindingSnapshot snapshot = snapshot(
        ClientProfileBindingState.NO_WORLD,
        "No world is currently observed",
        null,
        Collections.<String>emptyList(),
        null,
        false);

    public ClientProfileBindingCoordinator(ProfileBindingIndexStore indexStore,
        HorizonwrightPersistenceStore profileStore, StableRandomIdSource profileIdSource,
        StableRandomIdSource confirmationIdSource, RuntimeSessionClock clock) {
        this(indexStore, profileStore, profileIdSource, confirmationIdSource, clock, NOOP_HOOK);
    }

    ClientProfileBindingCoordinator(ProfileBindingIndexStore indexStore, HorizonwrightPersistenceStore profileStore,
        StableRandomIdSource profileIdSource, StableRandomIdSource confirmationIdSource, RuntimeSessionClock clock,
        CommitHook commitHook) {
        if (indexStore == null || profileStore == null
            || profileIdSource == null
            || confirmationIdSource == null
            || clock == null
            || commitHook == null) {
            throw new IllegalArgumentException("binding stores, id sources, clock, and commitHook are required");
        }
        Path stateRoot = indexStore.getPaths()
            .getStateRoot();
        Path profileRoot = profileStore.pathsForProfile("binding-root-probe")
            .getStateRoot();
        if (!stateRoot.equals(profileRoot)) {
            throw new IllegalArgumentException("binding index and profile store must use the same state root");
        }
        this.indexStore = indexStore;
        this.profileStore = profileStore;
        this.profileIdSource = profileIdSource;
        this.confirmationIdSource = confirmationIdSource;
        this.clock = clock;
        this.transactionStore = new ProfileBindingTransactionStore(stateRoot);
        this.commitHook = commitHook;
    }

    public synchronized ClientProfileBindingSnapshot clearWorld() {
        observation = null;
        observedIndex = null;
        pendingTransaction = null;
        candidateProfileIds = Collections.emptyList();
        snapshot = snapshot(
            ClientProfileBindingState.NO_WORLD,
            "No world is currently observed",
            null,
            candidateProfileIds,
            null,
            false);
        return snapshot;
    }

    public synchronized ClientProfileBindingSnapshot observe(ClientProfileBindingObservation observedWorld) {
        if (observedWorld == null) {
            throw new IllegalArgumentException("observedWorld must not be null");
        }
        observation = observedWorld;
        observedIndex = null;
        pendingTransaction = null;
        candidateProfileIds = Collections.emptyList();

        ProfileBindingTransactionStore.LoadResult transaction = transactionStore.load();
        if (transaction.getStatus() == PersistenceLoadStatus.LOADED) {
            pendingTransaction = transaction.getTransaction();
            boolean recoverable = pendingTransaction.matches(observation);
            snapshot = snapshot(
                ClientProfileBindingState.FAILED,
                recoverable ? "An interrupted profile binding update requires explicit recovery"
                    : "An interrupted profile binding update belongs to another world observation",
                null,
                candidateProfileIds,
                null,
                recoverable);
            return snapshot;
        }
        if (transaction.getStatus() != PersistenceLoadStatus.MISSING) {
            return fail(transaction.getDiagnostic(), transaction.getStatus(), false);
        }

        ProfileBindingIndex index = loadIndex();
        if (index == null) {
            return snapshot;
        }
        observedIndex = index;
        Optional<WorldProfileIdentity> match;
        try {
            match = index.find(observation.getKey());
        } catch (RuntimeException failure) {
            return fail("Profile binding lookup failed: " + describe(failure), PersistenceLoadStatus.CORRUPT, false);
        }
        if (match.isPresent()) {
            return validateSelectedProfile(match.get());
        }

        candidateProfileIds = sameLocatorCandidates(index, observation.getKey());
        if (candidateProfileIds.isEmpty()) {
            snapshot = snapshot(
                ClientProfileBindingState.NEEDS_EXPLICIT_ENROLLMENT,
                "This explicit world key is not enrolled",
                null,
                candidateProfileIds,
                null,
                false);
        } else {
            snapshot = snapshot(
                ClientProfileBindingState.NEEDS_EXPLICIT_REASSOCIATION,
                "The locator is known, but this explicit world fingerprint requires confirmed reassociation",
                null,
                candidateProfileIds,
                null,
                false);
        }
        return snapshot;
    }

    /** Explicitly chooses an existing stable profile when a changed locator could not be inferred. */
    public synchronized ClientProfileBindingSnapshot requestReassociation(String profileId) {
        requireObservedState(
            ClientProfileBindingState.NEEDS_EXPLICIT_ENROLLMENT,
            ClientProfileBindingState.NEEDS_EXPLICIT_REASSOCIATION);
        ProfileBindingEntry entry = requireEntryByProfile(observedIndex, profileId);
        if (entry.getKey()
            .equals(observation.getKey())) {
            throw new IllegalArgumentException("profile is already bound to the observed key");
        }
        candidateProfileIds = Collections.singletonList(
            entry.getIdentity()
                .getProfileId());
        snapshot = snapshot(
            ClientProfileBindingState.NEEDS_EXPLICIT_REASSOCIATION,
            "Explicit reassociation target selected; confirmation is still required",
            null,
            candidateProfileIds,
            null,
            false);
        return snapshot;
    }

    public synchronized ClientProfileBindingSnapshot confirmEnrollment(boolean userConfirmed) {
        requireUserConfirmation(userConfirmed, "enrollment");
        requireObservedState(ClientProfileBindingState.NEEDS_EXPLICIT_ENROLLMENT);
        ProfileBindingIndex current = reloadExpectedIndex();
        if (current.find(observation.getKey())
            .isPresent()) {
            throw operationFailure("Binding index changed before enrollment; observe the world again", null, false);
        }

        long confirmedAt = readClock();
        String profileId = requireGeneratedId(profileIdSource.nextId(), "profileId");
        String confirmationId = requireGeneratedId(confirmationIdSource.nextId(), "confirmationId");
        WorldProfileIdentity identity = observation.identity(profileId, confirmedAt);
        ProfileStatePaths paths = profileStore.pathsForProfile(profileId);
        if (Files.exists(paths.getProfileDirectory())) {
            throw operationFailure(
                "Generated profileId already has a persistence partition and will not be reused: " + profileId,
                null,
                false);
        }
        PersistenceLoadResult<ProfileEnvelope> existingProfile = profileStore.loadProfile(paths);
        if (existingProfile.getStatus() != PersistenceLoadStatus.MISSING || existingProfile.isBackupAvailable()) {
            throw operationFailure(
                "Generated profileId already has durable state and will not be overwritten: "
                    + existingProfile.getDiagnostic(),
                null,
                false);
        }

        ProfileBindingIndex replacement = current
            .enroll(observation.getKey(), identity, confirmationId, confirmedAt, true);
        ProfileEnvelope profile = emptyProfile(confirmedAt, identity);
        ProfileBindingTransaction transaction = new ProfileBindingTransaction(
            ProfileBindingTransaction.Operation.ENROLL,
            current.getRevision(),
            observation.getKey(),
            null,
            identity,
            confirmationId,
            confirmedAt);
        return commit(transaction, replacement, profile);
    }

    public synchronized ClientProfileBindingSnapshot confirmReassociation(String profileId, boolean userConfirmed) {
        requireUserConfirmation(userConfirmed, "reassociation");
        requireObservedState(ClientProfileBindingState.NEEDS_EXPLICIT_REASSOCIATION);
        if (!candidateProfileIds.contains(profileId)) {
            throw new IllegalArgumentException("profileId is not an explicitly selected reassociation candidate");
        }
        ProfileBindingIndex current = reloadExpectedIndex();
        ProfileBindingEntry entry = requireEntryByProfile(current, profileId);
        ProfileEnvelope previousProfile = requireExactProfile(entry.getIdentity(), "before reassociation");
        long confirmedAt = readClock();
        if (confirmedAt < previousProfile.getWrittenAtEpochMillis()) {
            throw operationFailure("Reassociation timestamp would move profile.json backwards", null, false);
        }
        String confirmationId = requireGeneratedId(confirmationIdSource.nextId(), "confirmationId");
        WorldProfileIdentity previousIdentity = previousProfile.getIdentity();
        WorldProfileIdentity targetIdentity = new WorldProfileIdentity(
            previousIdentity.getProfileId(),
            previousIdentity.getDisplayName(),
            observation.getServerAddress(),
            observation.getWorldFingerprint(),
            previousIdentity.getCreatedAtEpochMillis());
        ProfileBindingIndex replacement = current
            .reassociate(entry.getKey(), observation.getKey(), targetIdentity, confirmationId, confirmedAt, true);
        ProfileEnvelope replacementProfile = reassociatedProfile(
            previousProfile,
            targetIdentity,
            confirmationId,
            confirmedAt);
        ProfileBindingTransaction transaction = new ProfileBindingTransaction(
            ProfileBindingTransaction.Operation.REASSOCIATE,
            current.getRevision(),
            observation.getKey(),
            previousIdentity,
            targetIdentity,
            confirmationId,
            confirmedAt);
        return commit(transaction, replacement, replacementProfile);
    }

    public synchronized ClientProfileBindingSnapshot recoverInterruptedUpdate() {
        if (observation == null || pendingTransaction == null
            || snapshot.getState() != ClientProfileBindingState.FAILED
            || !snapshot.isInterruptedUpdateRecoverable()
            || !pendingTransaction.matches(observation)) {
            throw new IllegalStateException("no interrupted update is recoverable for the current observation");
        }
        try {
            ProfileBindingIndex index = loadIndexOrThrow();
            ProfileBindingIndex completedIndex = completeIndex(pendingTransaction, index);
            completeProfile(pendingTransaction);
            transactionStore.complete();
            pendingTransaction = null;
            observedIndex = completedIndex;
            return observe(observation);
        } catch (Exception failure) {
            throw operationFailure("Interrupted profile binding recovery failed", failure, true);
        }
    }

    public synchronized ClientProfileBindingSnapshot getSnapshot() {
        return snapshot;
    }

    private ClientProfileBindingSnapshot commit(ProfileBindingTransaction transaction,
        ProfileBindingIndex replacementIndex, ProfileEnvelope replacementProfile) {
        boolean intentPersisted = false;
        try {
            transactionStore.begin(transaction);
            intentPersisted = true;
            indexStore.save(replacementIndex);
            commitHook.afterIndexCommitted();
            profileStore.saveProfile(
                profileStore.pathsForProfile(
                    replacementProfile.getIdentity()
                        .getProfileId()),
                replacementProfile);
            transactionStore.complete();
            pendingTransaction = null;
            return observe(observation);
        } catch (Exception failure) {
            pendingTransaction = intentPersisted ? transaction : null;
            throw operationFailure("Confirmed profile binding update was interrupted", failure, intentPersisted);
        }
    }

    private ProfileBindingIndex completeIndex(ProfileBindingTransaction transaction, ProfileBindingIndex index)
        throws PersistenceException {
        if (index.getRevision() == transaction.getBaseIndexRevision()) {
            ProfileBindingIndex replacement;
            if (transaction.getOperation() == ProfileBindingTransaction.Operation.ENROLL) {
                replacement = index.enroll(
                    observation.getKey(),
                    transaction.getTargetIdentity(),
                    transaction.getConfirmationId(),
                    transaction.getConfirmedAtEpochMillis(),
                    true);
            } else {
                ProfileBindingEntry previous = requireEntryByProfile(
                    index,
                    transaction.getTargetIdentity()
                        .getProfileId());
                if (!previous.getIdentity()
                    .equals(transaction.getPreviousIdentity())) {
                    throw new IllegalStateException("reassociation base identity no longer matches its transaction");
                }
                replacement = index.reassociate(
                    previous.getKey(),
                    observation.getKey(),
                    transaction.getTargetIdentity(),
                    transaction.getConfirmationId(),
                    transaction.getConfirmedAtEpochMillis(),
                    true);
            }
            indexStore.save(replacement);
            return replacement;
        }
        if (index.getRevision() != transaction.getBaseIndexRevision() + 1L || !transaction.getTargetIdentity()
            .equals(
                index.find(observation.getKey())
                    .orElse(null))
            || !containsConfirmation(index, transaction)) {
            throw new IllegalStateException("binding index diverged from the interrupted transaction");
        }
        return index;
    }

    private void completeProfile(ProfileBindingTransaction transaction) throws PersistenceException {
        WorldProfileIdentity target = transaction.getTargetIdentity();
        ProfileStatePaths paths = profileStore.pathsForProfile(target.getProfileId());
        PersistenceLoadResult<ProfileEnvelope> loaded = profileStore.loadProfile(paths);
        if (transaction.getOperation() == ProfileBindingTransaction.Operation.ENROLL) {
            if (loaded.isLoaded()) {
                if (!target.equals(
                    loaded.getValue()
                        .getIdentity())) {
                    throw new IllegalStateException("existing profile conflicts with interrupted enrollment");
                }
                return;
            }
            if (loaded.getStatus() != PersistenceLoadStatus.MISSING || loaded.isBackupAvailable()) {
                throw new IllegalStateException(
                    "interrupted enrollment will not overwrite profile state: " + loaded.getDiagnostic());
            }
            profileStore.saveProfile(paths, emptyProfile(transaction.getConfirmedAtEpochMillis(), target));
            return;
        }

        if (!loaded.isLoaded()) {
            throw new IllegalStateException(
                "interrupted reassociation requires its valid existing profile: " + loaded.getDiagnostic());
        }
        ProfileEnvelope profile = loaded.getValue();
        WorldProfileIdentity previous = transaction.getPreviousIdentity();
        if (target.equals(profile.getIdentity())) {
            if (!sameServerWorld(previous, target)
                && !containsProfileConfirmation(profile, transaction.getConfirmationId())) {
                throw new IllegalStateException("updated profile lacks the interrupted reassociation audit record");
            }
            return;
        }
        if (!previous.equals(profile.getIdentity())) {
            throw new IllegalStateException("existing profile diverged from the interrupted reassociation");
        }
        profileStore.saveProfile(
            paths,
            reassociatedProfile(
                profile,
                target,
                transaction.getConfirmationId(),
                transaction.getConfirmedAtEpochMillis()));
    }

    private ClientProfileBindingSnapshot validateSelectedProfile(WorldProfileIdentity identity) {
        PersistenceLoadResult<ProfileEnvelope> profile = profileStore
            .loadProfile(profileStore.pathsForProfile(identity.getProfileId()));
        if (!profile.isLoaded()) {
            return fail(
                "Binding index selected profile '" + identity.getProfileId()
                    + "', but profile.json is unavailable: "
                    + profile.getDiagnostic(),
                profile.getStatus(),
                false);
        }
        if (!identity.equals(
            profile.getValue()
                .getIdentity())) {
            return fail(
                "Binding index and profile.json disagree; an unjournaled partial update will not be inferred",
                PersistenceLoadStatus.PROFILE_MISMATCH,
                false);
        }
        snapshot = snapshot(
            ClientProfileBindingState.READY,
            "Profile binding and profile.json are validated",
            identity,
            Collections.<String>emptyList(),
            PersistenceLoadStatus.LOADED,
            false);
        return snapshot;
    }

    private ProfileBindingIndex loadIndex() {
        PersistenceLoadResult<ProfileBindingIndex> loaded = indexStore.load();
        if (loaded.isLoaded()) {
            return loaded.getValue();
        }
        if (loaded.getStatus() == PersistenceLoadStatus.MISSING && !loaded.isBackupAvailable()) {
            return ProfileBindingIndex.empty();
        }
        fail(
            "Profile binding index is unavailable and was preserved: " + loaded.getDiagnostic(),
            loaded.getStatus(),
            false);
        return null;
    }

    private ProfileBindingIndex loadIndexOrThrow() {
        PersistenceLoadResult<ProfileBindingIndex> loaded = indexStore.load();
        if (loaded.isLoaded()) {
            return loaded.getValue();
        }
        if (loaded.getStatus() == PersistenceLoadStatus.MISSING && !loaded.isBackupAvailable()) {
            return ProfileBindingIndex.empty();
        }
        throw new IllegalStateException("profile binding index is unavailable: " + loaded.getDiagnostic());
    }

    private ProfileBindingIndex reloadExpectedIndex() {
        ProfileBindingIndex current;
        try {
            current = loadIndexOrThrow();
        } catch (RuntimeException failure) {
            throw operationFailure("Could not revalidate profile binding index", failure, false);
        }
        if (observedIndex == null || current.getRevision() != observedIndex.getRevision()) {
            throw operationFailure("Profile binding index changed; observe the world again", null, false);
        }
        observedIndex = current;
        return current;
    }

    private ProfileEnvelope requireExactProfile(WorldProfileIdentity identity, String operation) {
        PersistenceLoadResult<ProfileEnvelope> loaded = profileStore
            .loadProfile(profileStore.pathsForProfile(identity.getProfileId()));
        if (!loaded.isLoaded() || !identity.equals(
            loaded.getValue()
                .getIdentity())) {
            throw operationFailure(
                "Valid matching profile.json is required " + operation + ": " + loaded.getDiagnostic(),
                null,
                false);
        }
        return loaded.getValue();
    }

    private static ProfileEnvelope emptyProfile(long writtenAt, WorldProfileIdentity identity) {
        return new ProfileEnvelope(
            writtenAt,
            identity,
            Collections.<ProfileReassociation>emptyList(),
            Collections.<NamedLocation>emptyList(),
            Collections.<NamedRoute>emptyList());
    }

    private static ProfileEnvelope reassociatedProfile(ProfileEnvelope previous, WorldProfileIdentity target,
        String confirmationId, long confirmedAt) {
        List<ProfileReassociation> history = new ArrayList<>(previous.getReassociations());
        if (!sameServerWorld(previous.getIdentity(), target)) {
            history.add(
                new ProfileReassociation(
                    previous.getIdentity()
                        .getServerAddress(),
                    previous.getIdentity()
                        .getWorldFingerprint(),
                    target.getServerAddress(),
                    target.getWorldFingerprint(),
                    confirmationId,
                    confirmedAt,
                    true));
        }
        return new ProfileEnvelope(
            confirmedAt,
            target,
            history,
            previous.getNamedLocations(),
            previous.getNamedRoutes(),
            previous.getNamedLoadouts(),
            previous.getNamedStorageEndpoints(),
            previous.getNamedRepairStations());
    }

    private boolean containsConfirmation(ProfileBindingIndex index, ProfileBindingTransaction transaction) {
        if (transaction.getOperation() == ProfileBindingTransaction.Operation.ENROLL) {
            ProfileBindingEntry entry = requireEntryByProfile(
                index,
                transaction.getTargetIdentity()
                    .getProfileId());
            return entry.getEnrollmentConfirmationId()
                .equals(transaction.getConfirmationId());
        }
        for (ProfileBindingReassociationRecord record : index.getReassociations()) {
            if (record.getProfileId()
                .equals(
                    transaction.getTargetIdentity()
                        .getProfileId())
                && record.getConfirmationId()
                    .equals(transaction.getConfirmationId())
                && record.getNewKey()
                    .equals(observation.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsProfileConfirmation(ProfileEnvelope profile, String confirmationId) {
        for (ProfileReassociation reassociation : profile.getReassociations()) {
            if (reassociation.getConfirmationId()
                .equals(confirmationId)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> sameLocatorCandidates(ProfileBindingIndex index, ProfileBindingKey key) {
        List<String> candidates = new ArrayList<>();
        for (ProfileBindingEntry entry : index.getBindings()) {
            ProfileBindingKey enrolled = entry.getKey();
            if (enrolled.getKind() == key.getKind() && enrolled.getLocatorHash()
                .equals(key.getLocatorHash())) {
                candidates.add(
                    entry.getIdentity()
                        .getProfileId());
            }
        }
        return Collections.unmodifiableList(candidates);
    }

    private static ProfileBindingEntry requireEntryByProfile(ProfileBindingIndex index, String profileId) {
        if (profileId == null || profileId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("profileId must not be blank");
        }
        for (ProfileBindingEntry entry : index.getBindings()) {
            if (entry.getIdentity()
                .getProfileId()
                .equals(profileId.trim())) {
                return entry;
            }
        }
        throw new IllegalArgumentException("unknown profileId: " + profileId.trim());
    }

    private static boolean sameServerWorld(WorldProfileIdentity left, WorldProfileIdentity right) {
        return left.getServerAddress()
            .equals(right.getServerAddress())
            && left.getWorldFingerprint()
                .equals(right.getWorldFingerprint());
    }

    private long readClock() {
        long now = clock.nowEpochMillis();
        if (now < 0L) {
            throw operationFailure("profile binding clock returned a negative timestamp", null, false);
        }
        return now;
    }

    private static String requireGeneratedId(String id, String purpose) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(purpose + " source returned an invalid persistence identifier");
        }
        return id;
    }

    private void requireObservedState(ClientProfileBindingState... allowed) {
        if (observation == null) {
            throw new IllegalStateException("no world is currently observed");
        }
        for (ClientProfileBindingState state : allowed) {
            if (snapshot.getState() == state) {
                return;
            }
        }
        throw new IllegalStateException("operation is unavailable while profile binding is " + snapshot.getState());
    }

    private static void requireUserConfirmation(boolean confirmed, String operation) {
        if (!confirmed) {
            throw new IllegalArgumentException(operation + " requires explicit user confirmation");
        }
    }

    private ClientProfileBindingException operationFailure(String message, Throwable cause, boolean recoverable) {
        ClientProfileBindingException failure = cause == null ? new ClientProfileBindingException(message)
            : new ClientProfileBindingException(message + ": " + describe(cause), cause);
        snapshot = snapshot(
            ClientProfileBindingState.FAILED,
            failure.getMessage(),
            null,
            candidateProfileIds,
            null,
            recoverable);
        return failure;
    }

    private ClientProfileBindingSnapshot fail(String diagnostic, PersistenceLoadStatus loadStatus,
        boolean recoverable) {
        snapshot = snapshot(
            ClientProfileBindingState.FAILED,
            diagnostic,
            null,
            candidateProfileIds,
            loadStatus,
            recoverable);
        return snapshot;
    }

    private static ClientProfileBindingSnapshot snapshot(ClientProfileBindingState state, String diagnostic,
        WorldProfileIdentity identity, List<String> candidates, PersistenceLoadStatus status, boolean recoverable) {
        return new ClientProfileBindingSnapshot(state, diagnostic, identity, candidates, status, recoverable);
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.trim()
            .isEmpty() ? failure.getClass()
                .getSimpleName() : message;
    }
}
