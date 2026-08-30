package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Immutable identity and monotonic epoch of one accepted world connection. */
public final class RuntimeSessionConnection {

    private final WorldProfileIdentity identity;
    private final RuntimeConnectionToken token;
    private final long connectionEpoch;

    RuntimeSessionConnection(WorldProfileIdentity identity, RuntimeConnectionToken token, long connectionEpoch) {
        if (identity == null || token == null) {
            throw new IllegalArgumentException("identity and token must not be null");
        }
        if (connectionEpoch <= 0L) {
            throw new IllegalArgumentException("connectionEpoch must be positive");
        }
        this.identity = identity;
        this.token = token;
        this.connectionEpoch = connectionEpoch;
    }

    public WorldProfileIdentity getIdentity() {
        return identity;
    }

    public RuntimeConnectionToken getToken() {
        return token;
    }

    public long getConnectionEpoch() {
        return connectionEpoch;
    }
}
