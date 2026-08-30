package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.io.IOException;
import java.nio.file.Path;

/** Atomic, no-implicit-fallback persistence for the global client-side profile binding index. */
public final class ProfileBindingIndexStore {

    private final ProfileBindingIndexPaths paths;
    private final PersistenceFileSystem fileSystem;
    private final ProfileBindingIndexJsonCodec codec;

    public ProfileBindingIndexStore(Path stateRoot) {
        this(stateRoot, new NioPersistenceFileSystem());
    }

    ProfileBindingIndexStore(Path stateRoot, PersistenceFileSystem fileSystem) {
        if (stateRoot == null || fileSystem == null) {
            throw new IllegalArgumentException("stateRoot and fileSystem must not be null");
        }
        this.paths = new ProfileBindingIndexPaths(stateRoot);
        this.fileSystem = fileSystem;
        this.codec = new ProfileBindingIndexJsonCodec();
    }

    public ProfileBindingIndexPaths getPaths() {
        return paths;
    }

    public PersistenceLoadResult<ProfileBindingIndex> load() {
        return load(paths.getIndexFile(), paths.getBackupFile());
    }

    public PersistenceLoadResult<ProfileBindingIndex> loadBackup() {
        return load(paths.getBackupFile(), null);
    }

    public void save(ProfileBindingIndex index) throws PersistenceException {
        if (index == null) {
            throw new IllegalArgumentException("index must not be null");
        }
        index.validate();
        byte[] previous = requireWritableExisting();
        atomicWrite(codec.encode(index), previous);
    }

    private PersistenceLoadResult<ProfileBindingIndex> load(Path target, Path backup) {
        final boolean backupAvailable;
        try {
            backupAvailable = backup != null && fileSystem.exists(backup);
            if (!fileSystem.exists(target)) {
                return PersistenceLoadResult.failure(
                    PersistenceLoadStatus.MISSING,
                    target,
                    "No profile binding index exists at " + target,
                    backupAvailable);
            }
            ProfileBindingIndexJsonCodec.DecodeResult decoded = codec.decode(fileSystem.readAllBytes(target));
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
                "Could not read profile binding index " + target + ": " + describe(failure),
                false);
        }
    }

    private byte[] requireWritableExisting() throws PersistenceException {
        Path target = paths.getIndexFile();
        try {
            if (!fileSystem.exists(target)) {
                if (fileSystem.exists(paths.getBackupFile())) {
                    throw new PersistenceException(
                        "Refusing to create a new profile binding index while an uninspected backup exists at "
                            + paths.getBackupFile());
                }
                return null;
            }
            byte[] content = fileSystem.readAllBytes(target);
            ProfileBindingIndexJsonCodec.DecodeResult decoded = codec.decode(content);
            if (decoded.getStatus() != PersistenceLoadStatus.LOADED) {
                throw new PersistenceException(
                    "Refusing to overwrite profile binding index because it is " + decoded.getStatus()
                        + ": "
                        + decoded.getDiagnostic());
            }
            return content;
        } catch (IOException failure) {
            throw new PersistenceException(
                "Refusing to overwrite unreadable profile binding index: " + describe(failure),
                failure);
        }
    }

    private void atomicWrite(byte[] replacement, byte[] previous) throws PersistenceException {
        Path target = paths.getIndexFile();
        Path temporary = paths.getTemporaryFile();
        Path backup = paths.getBackupFile();
        Path backupTemporary = paths.getBackupTemporaryFile();
        boolean temporaryCreated = false;
        boolean backupTemporaryCreated = false;
        try {
            fileSystem.createDirectories(target.getParent());
            if (fileSystem.exists(temporary) || fileSystem.exists(backupTemporary)) {
                throw new IOException("stale temporary profile binding file requires explicit inspection");
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
                "Atomic profile binding write failed; existing primary data was not replaced: " + describe(failure),
                failure);
        }
    }

    private void cleanupAfterFailure(Path temporary, boolean temporaryCreated, Path backupTemporary,
        boolean backupTemporaryCreated, IOException originalFailure) {
        if (temporaryCreated) {
            deleteSuppressing(temporary, originalFailure);
        }
        if (backupTemporaryCreated) {
            deleteSuppressing(backupTemporary, originalFailure);
        }
    }

    private void deleteSuppressing(Path path, IOException originalFailure) {
        try {
            fileSystem.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private static String describe(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.trim()
            .isEmpty() ? exception.getClass()
                .getSimpleName() : message;
    }
}
