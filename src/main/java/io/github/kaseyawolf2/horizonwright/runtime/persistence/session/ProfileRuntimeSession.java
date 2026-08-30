package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/**
 * Serialized lifecycle owner for one explicitly selected profile/world binding.
 *
 * <p>
 * A connection is admitted only after its durable envelope has loaded successfully and matches the bound profile,
 * server, and world fingerprint. The runtime is then created fresh and restored exactly once before becoming active.
 * Retiring an active connection attempts exactly one final save before closing it. Duplicate and stale connection
 * callbacks cannot create, tick, save, or close a runtime twice.
 * </p>
 */
public final class ProfileRuntimeSession implements AutoCloseable {

    private final RuntimeSessionRuntimeFactory runtimeFactory;
    private final RuntimeSessionClock clock;

    private RuntimeSessionState state = RuntimeSessionState.UNBOUND;
    private WorldProfileIdentity identity;
    private RuntimeSessionPersistence persistence;
    private RuntimeSessionConnection activeConnection;
    private RuntimeSessionConnection lastConnection;
    private RuntimeSessionRuntime runtime;
    private RuntimeSessionException failure;

    public ProfileRuntimeSession(RuntimeSessionRuntimeFactory runtimeFactory, RuntimeSessionClock clock) {
        if (runtimeFactory == null || clock == null) {
            throw new IllegalArgumentException("runtimeFactory and clock must not be null");
        }
        this.runtimeFactory = runtimeFactory;
        this.clock = clock;
    }

    public synchronized void bind(WorldProfileIdentity selectedIdentity,
        RuntimeSessionPersistence selectedPersistence) {
        if (selectedIdentity == null || selectedPersistence == null) {
            throw new IllegalArgumentException("selectedIdentity and selectedPersistence must not be null");
        }
        if (state != RuntimeSessionState.UNBOUND) {
            throw new IllegalStateException("runtime session is already bound or terminal");
        }
        if (!hasSameDurableBinding(selectedIdentity, selectedPersistence.getExpectedIdentity())) {
            throw fail("persistence boundary does not match the selected profile/world", null);
        }
        identity = selectedIdentity;
        persistence = selectedPersistence;
        state = RuntimeSessionState.WAITING_FOR_WORLD;
    }

    /**
     * Connects once for {@code token}; a duplicate callback returns the already active connection.
     */
    public synchronized RuntimeSessionConnection connect(RuntimeConnectionToken token) {
        if (token == null) {
            throw new IllegalArgumentException("token must not be null");
        }
        if (state == RuntimeSessionState.ACTIVE) {
            if (activeConnection.getToken()
                .equals(token)) {
                return activeConnection;
            }
            throw new IllegalStateException("a different connection is already active");
        }
        requireState(RuntimeSessionState.WAITING_FOR_WORLD, "connect");

        final RuntimeEnvelope loaded;
        try {
            loaded = persistence.load();
        } catch (Exception loadFailure) {
            throw fail("refused to load the bound profile runtime", loadFailure);
        }
        if (loaded == null) {
            throw fail("persistence boundary returned no runtime envelope", null);
        }
        if (!hasSameDurableBinding(identity, loaded)) {
            throw fail("loaded runtime does not match the selected profile/world", null);
        }

        final RuntimeSessionConnection connection;
        try {
            connection = new RuntimeSessionConnection(identity, token, loaded.minimumNextConnectionEpoch());
        } catch (RuntimeException epochFailure) {
            throw fail("could not allocate a fresh connection epoch", epochFailure);
        }

        RuntimeSessionRuntime freshRuntime = null;
        try {
            freshRuntime = runtimeFactory.create(connection);
            if (freshRuntime == null) {
                throw new IllegalStateException("runtime factory returned null");
            }
            freshRuntime.restore(loaded);
        } catch (RuntimeException restoreFailure) {
            throw failBeforeActivation("could not create and restore a fresh runtime", restoreFailure, freshRuntime);
        }

        runtime = freshRuntime;
        activeConnection = connection;
        lastConnection = connection;
        state = RuntimeSessionState.ACTIVE;
        return connection;
    }

    /** Returns false for stale tokens and for callbacks already consumed by a prior disconnect. */
    public synchronized boolean disconnect(RuntimeConnectionToken token) {
        if (token == null) {
            throw new IllegalArgumentException("token must not be null");
        }
        if (state != RuntimeSessionState.ACTIVE || !activeConnection.getToken()
            .equals(token)) {
            return false;
        }
        finishActive(false, null);
        return true;
    }

    /** Ticks only the exact active connection and only after its one-time restore completed. */
    public synchronized boolean clientTick(RuntimeConnectionToken token) {
        if (token == null) {
            throw new IllegalArgumentException("token must not be null");
        }
        if (state != RuntimeSessionState.ACTIVE || !activeConnection.getToken()
            .equals(token)) {
            return false;
        }
        try {
            runtime.clientTick();
            return true;
        } catch (RuntimeException tickFailure) {
            finishActive(true, tickFailure);
            throw failure;
        }
    }

    @Override
    public synchronized void close() {
        if (state == RuntimeSessionState.RETIRED || state == RuntimeSessionState.FAILED) {
            return;
        }
        if (state == RuntimeSessionState.ACTIVE) {
            finishActive(false, null);
            return;
        }
        state = RuntimeSessionState.RETIRED;
    }

    public synchronized RuntimeSessionState getState() {
        return state;
    }

    public synchronized Optional<WorldProfileIdentity> getIdentity() {
        return Optional.ofNullable(identity);
    }

    public synchronized Optional<RuntimeSessionConnection> getActiveConnection() {
        return Optional.ofNullable(activeConnection);
    }

    public synchronized Optional<RuntimeSessionConnection> getLastConnection() {
        return Optional.ofNullable(lastConnection);
    }

    public synchronized Optional<RuntimeSessionException> getFailure() {
        return Optional.ofNullable(failure);
    }

    private void finishActive(boolean forceFailure, RuntimeException initialFailure) {
        RuntimeSessionRuntime retiringRuntime = runtime;
        RuntimeSessionConnection retiringConnection = activeConnection;
        RuntimeSessionException terminalFailure = initialFailure == null ? null
            : new RuntimeSessionException("active runtime failed", initialFailure);

        try {
            long writtenAtEpochMillis = clock.nowEpochMillis();
            if (writtenAtEpochMillis < 0L) {
                throw new IllegalStateException("runtime session clock returned a negative timestamp");
            }
            RuntimeEnvelope saved = persistence.save(writtenAtEpochMillis, retiringConnection, retiringRuntime);
            if (saved == null || !hasSameDurableBinding(identity, saved)) {
                throw new IllegalStateException("saved runtime does not match the selected profile/world");
            }
        } catch (Exception saveFailure) {
            terminalFailure = append(terminalFailure, "final runtime save failed", saveFailure);
        }

        try {
            retiringRuntime.close();
        } catch (RuntimeException closeFailure) {
            terminalFailure = append(terminalFailure, "runtime close failed", closeFailure);
        } finally {
            runtime = null;
            activeConnection = null;
        }

        if (forceFailure || terminalFailure != null) {
            state = RuntimeSessionState.FAILED;
            failure = terminalFailure == null ? new RuntimeSessionException("active runtime failed") : terminalFailure;
            if (!forceFailure) {
                throw failure;
            }
        } else {
            state = RuntimeSessionState.RETIRED;
        }
    }

    private RuntimeSessionException failBeforeActivation(String message, RuntimeException cause,
        RuntimeSessionRuntime freshRuntime) {
        RuntimeSessionException result = new RuntimeSessionException(message, cause);
        if (freshRuntime != null) {
            try {
                freshRuntime.close();
            } catch (RuntimeException closeFailure) {
                result.addSuppressed(closeFailure);
            }
        }
        state = RuntimeSessionState.FAILED;
        failure = result;
        return result;
    }

    private RuntimeSessionException fail(String message, Throwable cause) {
        RuntimeSessionException result = cause == null ? new RuntimeSessionException(message)
            : new RuntimeSessionException(message, cause);
        state = RuntimeSessionState.FAILED;
        failure = result;
        return result;
    }

    private void requireState(RuntimeSessionState expected, String operation) {
        if (state != expected) {
            throw new IllegalStateException(operation + " is not available while runtime session is " + state);
        }
    }

    private static RuntimeSessionException append(RuntimeSessionException first, String message, Throwable next) {
        if (first == null) {
            return new RuntimeSessionException(message, next);
        }
        first.addSuppressed(next);
        return first;
    }

    static boolean hasSameDurableBinding(WorldProfileIdentity expected, WorldProfileIdentity actual) {
        return expected != null && actual != null
            && expected.getProfileId()
                .equals(actual.getProfileId())
            && expected.getServerAddress()
                .equals(actual.getServerAddress())
            && expected.getWorldFingerprint()
                .equals(actual.getWorldFingerprint());
    }

    static boolean hasSameDurableBinding(WorldProfileIdentity expected, RuntimeEnvelope actual) {
        return expected != null && actual != null
            && expected.getProfileId()
                .equals(actual.getProfileId())
            && expected.getServerAddress()
                .equals(actual.getServerAddress())
            && expected.getWorldFingerprint()
                .equals(actual.getWorldFingerprint());
    }
}
