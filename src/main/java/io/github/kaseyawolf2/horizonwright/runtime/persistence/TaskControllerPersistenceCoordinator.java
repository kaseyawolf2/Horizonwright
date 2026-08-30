package io.github.kaseyawolf2.horizonwright.runtime.persistence;

import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceException;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadResult;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadStatus;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileStatePaths;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;

/**
 * Durable runtime boundary for one explicitly bound server/world profile.
 *
 * <p>
 * A missing runtime document is a valid first-run state. Every other load failure is a hard refusal, and an existing
 * corrupt, newer, wrong-kind, or mismatched document is never replaced. Writes delegate to the store's temporary-file,
 * fsync, atomic-replace, and rolling-backup protocol.
 * </p>
 */
public final class TaskControllerPersistenceCoordinator {

    private final HorizonwrightPersistenceStore store;
    private final ProfileStatePaths paths;
    private final WorldProfileIdentity expectedIdentity;

    public TaskControllerPersistenceCoordinator(HorizonwrightPersistenceStore store,
        WorldProfileIdentity expectedIdentity) {
        if (store == null || expectedIdentity == null) {
            throw new IllegalArgumentException("store and expectedIdentity must not be null");
        }
        this.store = store;
        this.expectedIdentity = expectedIdentity;
        this.paths = store.pathsForProfile(expectedIdentity.getProfileId());
    }

    public ProfileStatePaths getPaths() {
        return paths;
    }

    /** Loads validated state without mutating a controller. */
    public RuntimeEnvelope load() throws TaskControllerPersistenceException {
        requireExpectedProfile();
        PersistenceLoadResult<RuntimeEnvelope> runtime = store.loadRuntime(paths);
        if (runtime.getStatus() == PersistenceLoadStatus.MISSING) {
            return emptyRuntime();
        }
        RuntimeEnvelope loaded = requireLoaded(runtime, "runtime");
        requireRuntimeBinding(loaded);
        return loaded;
    }

    /**
     * Loads once, restores through the supplied fresh runtime boundary, and returns the same
     * envelope for death/connection restore.
     *
     * <p>
     * The callback deliberately targets the runtime composition root instead of a bare task
     * controller so runtime-owned task identifier sequences are reseeded by the same restore.
     */
    public RuntimeEnvelope restoreFresh(RuntimeStateRestoreBoundary freshRuntime)
        throws TaskControllerPersistenceException {
        if (freshRuntime == null) {
            throw new IllegalArgumentException("freshRuntime must not be null");
        }
        RuntimeEnvelope loaded = load();
        freshRuntime.restoreControllerState(loaded.getTaskControllerState());
        return loaded;
    }

    /** Atomically replaces runtime.json after revalidating the durable profile partition and epoch floor. */
    public RuntimeEnvelope save(long writtenAtEpochMillis, long lastConnectionEpoch,
        UnresolvedDeathState unresolvedDeathState, IHorizonwrightController controller)
        throws TaskControllerPersistenceException {
        if (controller == null) {
            throw new IllegalArgumentException("controller must not be null");
        }
        if (lastConnectionEpoch < 0L || lastConnectionEpoch == Long.MAX_VALUE) {
            throw new IllegalArgumentException("lastConnectionEpoch must be non-negative and advanceable");
        }
        requireExpectedProfile();

        long existingConnectionFloor = 0L;
        PersistenceLoadResult<RuntimeEnvelope> existing = store.loadRuntime(paths);
        if (existing.isLoaded()) {
            RuntimeEnvelope existingRuntime = existing.getValue();
            requireRuntimeBinding(existingRuntime);
            existingConnectionFloor = connectionFloor(existingRuntime);
        } else if (existing.getStatus() != PersistenceLoadStatus.MISSING) {
            throw refusal(existing, "runtime");
        }

        long effectiveConnectionEpoch = lastConnectionEpoch;
        if (unresolvedDeathState != null) {
            effectiveConnectionEpoch = Math
                .max(effectiveConnectionEpoch, unresolvedDeathState.getLastObservedConnectionEpoch());
        }
        if (effectiveConnectionEpoch < existingConnectionFloor) {
            throw new IllegalArgumentException(
                "lastConnectionEpoch must not move backwards from persisted floor " + existingConnectionFloor);
        }

        TaskControllerState controllerState = controller.exportState();
        RuntimeEnvelope replacement = new RuntimeEnvelope(
            writtenAtEpochMillis,
            expectedIdentity.getProfileId(),
            expectedIdentity.getServerAddress(),
            expectedIdentity.getWorldFingerprint(),
            effectiveConnectionEpoch,
            unresolvedDeathState,
            controllerState);
        try {
            store.saveRuntime(paths, replacement);
        } catch (PersistenceException failure) {
            throw new TaskControllerPersistenceException(
                "Could not atomically save controller runtime for profile '" + expectedIdentity.getProfileId() + "'",
                failure);
        }
        return replacement;
    }

    private ProfileEnvelope requireExpectedProfile() throws TaskControllerPersistenceException {
        ProfileEnvelope profile = requireLoaded(store.loadProfile(paths), "profile");
        WorldProfileIdentity actual = profile.getIdentity();
        if (!expectedIdentity.getProfileId()
            .equals(actual.getProfileId())
            || !expectedIdentity.getServerAddress()
                .equals(actual.getServerAddress())
            || !expectedIdentity.getWorldFingerprint()
                .equals(actual.getWorldFingerprint())) {
            throw new TaskControllerPersistenceException(
                PersistenceLoadStatus.PROFILE_MISMATCH,
                paths.getProfileFile(),
                "Refusing profile partition '" + paths.getProfileId()
                    + "': expected server/world binding "
                    + expectedIdentity.getServerAddress()
                    + " / "
                    + expectedIdentity.getWorldFingerprint()
                    + " but profile.json contains "
                    + actual.getServerAddress()
                    + " / "
                    + actual.getWorldFingerprint());
        }
        return profile;
    }

    private void requireRuntimeBinding(RuntimeEnvelope runtime) throws TaskControllerPersistenceException {
        if (!expectedIdentity.getServerAddress()
            .equals(runtime.getServerAddress())
            || !expectedIdentity.getWorldFingerprint()
                .equals(runtime.getWorldFingerprint())) {
            throw new TaskControllerPersistenceException(
                PersistenceLoadStatus.PROFILE_MISMATCH,
                paths.getRuntimeFile(),
                "Refusing runtime.json because its server/world binding does not match profile.json");
        }
    }

    private RuntimeEnvelope emptyRuntime() {
        return new RuntimeEnvelope(
            0L,
            expectedIdentity.getProfileId(),
            expectedIdentity.getServerAddress(),
            expectedIdentity.getWorldFingerprint(),
            0L,
            null,
            TaskControllerState.empty());
    }

    private static long connectionFloor(RuntimeEnvelope runtime) {
        long floor = runtime.getLastConnectionEpoch();
        if (runtime.getUnresolvedDeathState() != null) {
            floor = Math.max(
                floor,
                runtime.getUnresolvedDeathState()
                    .getLastObservedConnectionEpoch());
        }
        return floor;
    }

    private static <T> T requireLoaded(PersistenceLoadResult<T> result, String document)
        throws TaskControllerPersistenceException {
        if (!result.isLoaded()) {
            throw refusal(result, document);
        }
        return result.getValue();
    }

    private static TaskControllerPersistenceException refusal(PersistenceLoadResult<?> result, String document) {
        return new TaskControllerPersistenceException(
            result.getStatus(),
            result.getSource(),
            "Refusing to use " + document + " persistence: " + result.getDiagnostic());
    }
}
