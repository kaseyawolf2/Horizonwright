package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

/** Terminal refusal or lifecycle failure from a profile runtime session. */
public final class RuntimeSessionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    RuntimeSessionException(String message) {
        super(message);
    }

    RuntimeSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
