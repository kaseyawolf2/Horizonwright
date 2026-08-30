package io.github.kaseyawolf2.horizonwright.runtime.persistence;

import java.nio.file.Path;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadStatus;

/** Checked refusal to restore or replace a profile partition whose durable state is not trustworthy. */
public final class TaskControllerPersistenceException extends Exception {

    private static final long serialVersionUID = 1L;

    private final PersistenceLoadStatus loadStatus;
    private final Path source;

    TaskControllerPersistenceException(PersistenceLoadStatus loadStatus, Path source, String message) {
        super(message);
        this.loadStatus = loadStatus;
        this.source = source;
    }

    TaskControllerPersistenceException(String message, Throwable cause) {
        super(message, cause);
        this.loadStatus = null;
        this.source = null;
    }

    public Optional<PersistenceLoadStatus> getLoadStatus() {
        return Optional.ofNullable(loadStatus);
    }

    public Optional<Path> getSource() {
        return Optional.ofNullable(source);
    }
}
