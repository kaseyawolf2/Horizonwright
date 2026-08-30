package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.nio.file.Path;

public final class PersistenceLoadResult<T> {

    private final PersistenceLoadStatus status;
    private final Path source;
    private final T value;
    private final String diagnostic;
    private final boolean backupAvailable;

    private PersistenceLoadResult(PersistenceLoadStatus status, Path source, T value, String diagnostic,
        boolean backupAvailable) {
        if (status == null || source == null) {
            throw new IllegalArgumentException("status and source must not be null");
        }
        this.status = status;
        this.source = source;
        this.value = value;
        this.diagnostic = PersistenceValidation.requireText(diagnostic, "diagnostic");
        this.backupAvailable = backupAvailable;
        if ((status == PersistenceLoadStatus.LOADED) != (value != null)) {
            throw new IllegalArgumentException("only a loaded result may carry a value");
        }
    }

    static <T> PersistenceLoadResult<T> loaded(Path source, T value, String diagnostic, boolean backupAvailable) {
        return new PersistenceLoadResult<>(PersistenceLoadStatus.LOADED, source, value, diagnostic, backupAvailable);
    }

    static <T> PersistenceLoadResult<T> failure(PersistenceLoadStatus status, Path source, String diagnostic,
        boolean backupAvailable) {
        if (status == PersistenceLoadStatus.LOADED) {
            throw new IllegalArgumentException("failure status must not be LOADED");
        }
        return new PersistenceLoadResult<>(status, source, null, diagnostic, backupAvailable);
    }

    public PersistenceLoadStatus getStatus() {
        return status;
    }

    public Path getSource() {
        return source;
    }

    public boolean isLoaded() {
        return status == PersistenceLoadStatus.LOADED;
    }

    public T getValue() {
        if (!isLoaded()) {
            throw new IllegalStateException("persistence value is unavailable: " + diagnostic);
        }
        return value;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    /** Indicates that an explicit backup inspection is possible; loading never falls back to it automatically. */
    public boolean isBackupAvailable() {
        return backupAvailable;
    }
}
