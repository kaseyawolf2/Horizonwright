package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.nio.file.Path;

/** Fixed state-root paths for the global client-side profile binding index. */
public final class ProfileBindingIndexPaths {

    private final Path stateRoot;
    private final Path indexFile;

    public ProfileBindingIndexPaths(Path stateRoot) {
        if (stateRoot == null) {
            throw new IllegalArgumentException("stateRoot must not be null");
        }
        this.stateRoot = stateRoot.toAbsolutePath()
            .normalize();
        this.indexFile = this.stateRoot.resolve("profile-bindings.json")
            .normalize();
        if (!indexFile.startsWith(this.stateRoot)) {
            throw new IllegalArgumentException("profile binding index path escapes stateRoot");
        }
    }

    public Path getStateRoot() {
        return stateRoot;
    }

    public Path getIndexFile() {
        return indexFile;
    }

    public Path getBackupFile() {
        return ProfileStatePaths.backupOf(indexFile);
    }

    Path getTemporaryFile() {
        return ProfileStatePaths.temporaryOf(indexFile);
    }

    Path getBackupTemporaryFile() {
        return ProfileStatePaths.backupTemporaryOf(indexFile);
    }
}
