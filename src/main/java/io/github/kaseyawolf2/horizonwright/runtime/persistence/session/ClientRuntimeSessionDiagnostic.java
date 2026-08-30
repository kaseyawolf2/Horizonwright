package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Immutable typed availability diagnostic for a live-session consumer. */
public final class ClientRuntimeSessionDiagnostic {

    private final RuntimeSessionState state;
    private final String message;
    private final WorldProfileIdentity identity;
    private final RuntimeSessionException failure;

    ClientRuntimeSessionDiagnostic(RuntimeSessionState state, String message, WorldProfileIdentity identity,
        RuntimeSessionException failure) {
        if (state == null || message == null
            || message.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("state and message are required");
        }
        this.state = state;
        this.message = message.trim();
        this.identity = identity;
        this.failure = failure;
    }

    public RuntimeSessionState getState() {
        return state;
    }

    public String getMessage() {
        return message;
    }

    public Optional<WorldProfileIdentity> getIdentity() {
        return Optional.ofNullable(identity);
    }

    public Optional<RuntimeSessionException> getFailure() {
        return Optional.ofNullable(failure);
    }
}
