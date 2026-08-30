package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

/** Explicit profile binding or durable two-document update failure. */
public final class ClientProfileBindingException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    ClientProfileBindingException(String message) {
        super(message);
    }

    ClientProfileBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
