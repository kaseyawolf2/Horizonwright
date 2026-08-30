package io.github.kaseyawolf2.horizonwright.core.persistence;

public final class PersistenceException extends Exception {

    private static final long serialVersionUID = 1L;

    PersistenceException(String message) {
        super(message);
    }

    PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
