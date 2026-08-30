package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.nio.file.Path;

public final class ProfileStatePaths {

    private final Path stateRoot;
    private final String profileId;
    private final Path profileDirectory;

    public ProfileStatePaths(Path stateRoot, String profileId) {
        if (stateRoot == null) {
            throw new IllegalArgumentException("stateRoot must not be null");
        }
        this.stateRoot = stateRoot.toAbsolutePath()
            .normalize();
        this.profileId = PersistenceValidation.requireStableId(profileId, "profileId");
        this.profileDirectory = this.stateRoot.resolve("profiles")
            .resolve(this.profileId)
            .normalize();
        if (!profileDirectory.startsWith(this.stateRoot)) {
            throw new IllegalArgumentException("profile directory escapes stateRoot");
        }
    }

    public Path getStateRoot() {
        return stateRoot;
    }

    public String getProfileId() {
        return profileId;
    }

    public Path getProfileDirectory() {
        return profileDirectory;
    }

    public Path getProfileFile() {
        return profileDirectory.resolve("profile.json");
    }

    public Path getProfileBackupFile() {
        return backupOf(getProfileFile());
    }

    public Path getRuntimeFile() {
        return profileDirectory.resolve("runtime.json");
    }

    public Path getRuntimeBackupFile() {
        return backupOf(getRuntimeFile());
    }

    static Path temporaryOf(Path target) {
        return target.resolveSibling(target.getFileName() + ".tmp");
    }

    static Path backupOf(Path target) {
        return target.resolveSibling(target.getFileName() + ".bak");
    }

    static Path backupTemporaryOf(Path target) {
        return target.resolveSibling(target.getFileName() + ".bak.tmp");
    }
}
