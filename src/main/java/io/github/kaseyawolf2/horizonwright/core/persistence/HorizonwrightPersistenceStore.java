package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.io.IOException;
import java.nio.file.Path;

import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceJsonCodec.DecodeResult;

/**
 * Versioned profile/runtime persistence with explicit failures, atomic replacement, and one rolling backup.
 *
 * <p>
 * Primary reads never fall back to a backup. Callers may inspect a backup explicitly after presenting the primary
 * failure to the user.
 * </p>
 */
public final class HorizonwrightPersistenceStore {

    private final Path stateRoot;
    private final PersistenceFileSystem fileSystem;
    private final PersistenceJsonCodec codec;

    public HorizonwrightPersistenceStore(Path stateRoot) {
        this(stateRoot, new NioPersistenceFileSystem());
    }

    HorizonwrightPersistenceStore(Path stateRoot, PersistenceFileSystem fileSystem) {
        if (stateRoot == null || fileSystem == null) {
            throw new IllegalArgumentException("stateRoot and fileSystem must not be null");
        }
        this.stateRoot = stateRoot.toAbsolutePath()
            .normalize();
        this.fileSystem = fileSystem;
        this.codec = new PersistenceJsonCodec();
    }

    public ProfileStatePaths pathsForProfile(String profileId) {
        return new ProfileStatePaths(stateRoot, profileId);
    }

    public PersistenceLoadResult<ProfileEnvelope> loadProfile(ProfileStatePaths paths) {
        requireOwnedPaths(paths);
        PersistenceLoadResult<ProfileEnvelope> result = load(
            paths.getProfileFile(),
            paths.getProfileBackupFile(),
            new Decoder<ProfileEnvelope>() {

                @Override
                public DecodeResult<ProfileEnvelope> decode(byte[] content) {
                    return codec.decodeProfile(content);
                }
            });
        return requireProfilePartition(
            paths,
            result,
            result.isLoaded() ? result.getValue()
                .getIdentity()
                .getProfileId() : null);
    }

    public PersistenceLoadResult<ProfileEnvelope> loadProfileBackup(ProfileStatePaths paths) {
        requireOwnedPaths(paths);
        PersistenceLoadResult<ProfileEnvelope> result = load(
            paths.getProfileBackupFile(),
            null,
            new Decoder<ProfileEnvelope>() {

                @Override
                public DecodeResult<ProfileEnvelope> decode(byte[] content) {
                    return codec.decodeProfile(content);
                }
            });
        return requireProfilePartition(
            paths,
            result,
            result.isLoaded() ? result.getValue()
                .getIdentity()
                .getProfileId() : null);
    }

    public PersistenceLoadResult<RuntimeEnvelope> loadRuntime(ProfileStatePaths paths) {
        requireOwnedPaths(paths);
        PersistenceLoadResult<RuntimeEnvelope> result = load(
            paths.getRuntimeFile(),
            paths.getRuntimeBackupFile(),
            new Decoder<RuntimeEnvelope>() {

                @Override
                public DecodeResult<RuntimeEnvelope> decode(byte[] content) {
                    return codec.decodeRuntime(content);
                }
            });
        return requireProfilePartition(
            paths,
            result,
            result.isLoaded() ? result.getValue()
                .getProfileId() : null);
    }

    public PersistenceLoadResult<RuntimeEnvelope> loadRuntimeBackup(ProfileStatePaths paths) {
        requireOwnedPaths(paths);
        PersistenceLoadResult<RuntimeEnvelope> result = load(
            paths.getRuntimeBackupFile(),
            null,
            new Decoder<RuntimeEnvelope>() {

                @Override
                public DecodeResult<RuntimeEnvelope> decode(byte[] content) {
                    return codec.decodeRuntime(content);
                }
            });
        return requireProfilePartition(
            paths,
            result,
            result.isLoaded() ? result.getValue()
                .getProfileId() : null);
    }

    public void saveProfile(ProfileStatePaths paths, ProfileEnvelope profile) throws PersistenceException {
        requireOwnedPaths(paths);
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        profile.validate();
        if (!paths.getProfileId()
            .equals(
                profile.getIdentity()
                    .getProfileId())) {
            throw new PersistenceException(
                "Refusing to write profile identity '" + profile.getIdentity()
                    .getProfileId() + "' into profile partition '" + paths.getProfileId() + "'");
        }

        byte[] previous = requireWritableExisting(
            paths.getProfileFile(),
            paths.getProfileId(),
            new Decoder<ProfileEnvelope>() {

                @Override
                public DecodeResult<ProfileEnvelope> decode(byte[] content) {
                    return codec.decodeProfile(content);
                }
            },
            new ProfileIdReader<ProfileEnvelope>() {

                @Override
                public String profileId(ProfileEnvelope value) {
                    return value.getIdentity()
                        .getProfileId();
                }
            });
        atomicWrite(paths.getProfileFile(), codec.encodeProfile(profile), previous);
    }

    public void saveRuntime(ProfileStatePaths paths, RuntimeEnvelope runtime) throws PersistenceException {
        requireOwnedPaths(paths);
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        runtime.validate();
        if (!paths.getProfileId()
            .equals(runtime.getProfileId())) {
            throw new PersistenceException(
                "Refusing to write runtime identity '" + runtime.getProfileId()
                    + "' into profile partition '"
                    + paths.getProfileId()
                    + "'");
        }

        byte[] previous = requireWritableExisting(
            paths.getRuntimeFile(),
            paths.getProfileId(),
            new Decoder<RuntimeEnvelope>() {

                @Override
                public DecodeResult<RuntimeEnvelope> decode(byte[] content) {
                    return codec.decodeRuntime(content);
                }
            },
            new ProfileIdReader<RuntimeEnvelope>() {

                @Override
                public String profileId(RuntimeEnvelope value) {
                    return value.getProfileId();
                }
            });
        atomicWrite(paths.getRuntimeFile(), codec.encodeRuntime(runtime), previous);
    }

    private <T> PersistenceLoadResult<T> load(Path target, Path backup, Decoder<T> decoder) {
        final boolean backupAvailable;
        try {
            backupAvailable = backup != null && fileSystem.exists(backup);
            if (!fileSystem.exists(target)) {
                return PersistenceLoadResult.failure(
                    PersistenceLoadStatus.MISSING,
                    target,
                    "No persistence document exists at " + target,
                    backupAvailable);
            }
            byte[] content = fileSystem.readAllBytes(target);
            DecodeResult<T> decoded = decoder.decode(content);
            if (decoded.getStatus() == PersistenceLoadStatus.LOADED) {
                return PersistenceLoadResult
                    .loaded(target, decoded.getValue(), decoded.getDiagnostic(), backupAvailable);
            }
            return PersistenceLoadResult.failure(
                decoded.getStatus(),
                target,
                decoded.getDiagnostic() + "; source was preserved at " + target,
                backupAvailable);
        } catch (IOException failure) {
            return PersistenceLoadResult.failure(
                PersistenceLoadStatus.IO_ERROR,
                target,
                "Could not read persistence document " + target + ": " + describe(failure),
                false);
        }
    }

    private static <T> PersistenceLoadResult<T> requireProfilePartition(ProfileStatePaths paths,
        PersistenceLoadResult<T> result, String actualProfileId) {
        if (!result.isLoaded() || paths.getProfileId()
            .equals(actualProfileId)) {
            return result;
        }
        return PersistenceLoadResult.failure(
            PersistenceLoadStatus.PROFILE_MISMATCH,
            result.getSource(),
            "Persistence document belongs to profile '" + actualProfileId
                + "', not partition '"
                + paths.getProfileId()
                + "'; source was preserved at "
                + result.getSource(),
            result.isBackupAvailable());
    }

    private <T> byte[] requireWritableExisting(Path target, String expectedProfileId, Decoder<T> decoder,
        ProfileIdReader<T> profileIdReader) throws PersistenceException {
        try {
            if (!fileSystem.exists(target)) {
                return null;
            }
            byte[] content = fileSystem.readAllBytes(target);
            DecodeResult<T> decoded = decoder.decode(content);
            if (decoded.getStatus() != PersistenceLoadStatus.LOADED) {
                throw new PersistenceException(
                    "Refusing to overwrite " + target
                        + " because it is "
                        + decoded.getStatus()
                        + ": "
                        + decoded.getDiagnostic());
            }
            String actualProfileId = profileIdReader.profileId(decoded.getValue());
            if (!expectedProfileId.equals(actualProfileId)) {
                throw new PersistenceException(
                    "Refusing to overwrite " + target
                        + " because it belongs to profile '"
                        + actualProfileId
                        + "', not '"
                        + expectedProfileId
                        + "'");
            }
            return content;
        } catch (IOException failure) {
            throw new PersistenceException(
                "Refusing to overwrite unreadable persistence document " + target + ": " + describe(failure),
                failure);
        }
    }

    private void atomicWrite(Path target, byte[] replacement, byte[] previous) throws PersistenceException {
        Path temporary = ProfileStatePaths.temporaryOf(target);
        Path backup = ProfileStatePaths.backupOf(target);
        Path backupTemporary = ProfileStatePaths.backupTemporaryOf(target);
        boolean temporaryCreated = false;
        boolean backupTemporaryCreated = false;

        try {
            fileSystem.createDirectories(target.getParent());
            if (fileSystem.exists(temporary) || fileSystem.exists(backupTemporary)) {
                throw new IOException(
                    "stale temporary persistence file requires explicit inspection before writing: " + temporary
                        + " or "
                        + backupTemporary);
            }

            fileSystem.writeAndSync(temporary, replacement);
            temporaryCreated = true;
            if (previous != null) {
                fileSystem.writeAndSync(backupTemporary, previous);
                backupTemporaryCreated = true;
                fileSystem.atomicReplace(backupTemporary, backup);
                backupTemporaryCreated = false;
            }
            fileSystem.atomicReplace(temporary, target);
            temporaryCreated = false;
        } catch (IOException failure) {
            cleanupAfterFailure(temporary, temporaryCreated, backupTemporary, backupTemporaryCreated, failure);
            throw new PersistenceException(
                "Atomic persistence write failed for " + target
                    + "; existing primary data was not replaced: "
                    + describe(failure),
                failure);
        }
    }

    private void cleanupAfterFailure(Path temporary, boolean temporaryCreated, Path backupTemporary,
        boolean backupTemporaryCreated, IOException originalFailure) {
        if (temporaryCreated) {
            try {
                fileSystem.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
        if (backupTemporaryCreated) {
            try {
                fileSystem.deleteIfExists(backupTemporary);
            } catch (IOException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void requireOwnedPaths(ProfileStatePaths paths) {
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null");
        }
        if (!stateRoot.equals(paths.getStateRoot())) {
            throw new IllegalArgumentException("profile paths belong to a different persistence state root");
        }
    }

    private static String describe(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.trim()
            .isEmpty() ? exception.getClass()
                .getSimpleName() : message;
    }

    private interface Decoder<T> {

        DecodeResult<T> decode(byte[] content);
    }

    private interface ProfileIdReader<T> {

        String profileId(T value);
    }
}
