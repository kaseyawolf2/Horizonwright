package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;

/**
 * Owns the current per-connection runtime without exposing a process-wide singleton.
 *
 * <p>
 * Profile selection creates a waiting session. A matching world-ready edge admits one token and synchronously restores
 * its fresh runtime before consumers can resolve it. Disconnect and profile changes retire the old session before a new
 * waiting session is installed. Duplicate or stale lifecycle edges are inert.
 * </p>
 */
public final class ClientRuntimeSessionManager implements CurrentRuntimeProvider, AutoCloseable {

    private final HorizonwrightRuntimeSessionFactory runtimeFactory;
    private final RuntimeSessionPersistenceFactory persistenceFactory;
    private final RuntimeSessionClock clock;

    private WorldProfileIdentity selectedIdentity;
    private ProfileRuntimeSession currentSession;
    private final Set<RuntimeConnectionToken> retiredTokens = new LinkedHashSet<>();
    private RuntimeSessionException ownershipFailure;
    private boolean closed;

    public ClientRuntimeSessionManager(HorizonwrightRuntimeSessionFactory runtimeFactory,
        RuntimeSessionPersistenceFactory persistenceFactory, RuntimeSessionClock clock) {
        if (runtimeFactory == null || persistenceFactory == null || clock == null) {
            throw new IllegalArgumentException("runtimeFactory, persistenceFactory, and clock are required");
        }
        this.runtimeFactory = runtimeFactory;
        this.persistenceFactory = persistenceFactory;
        this.clock = clock;
    }

    /** Selects a profile; returns false when the same durable binding is already selected. */
    public synchronized boolean bindProfile(WorldProfileIdentity identity) {
        ensureOpen();
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        if (ProfileRuntimeSession.hasSameDurableBinding(selectedIdentity, identity)) {
            return false;
        }

        retireCurrent();
        selectedIdentity = identity;
        retiredTokens.clear();
        installWaitingSession();
        return true;
    }

    /**
     * Accepts a matching world connection. Same-token world/dimension refreshes are duplicates, while a genuinely new
     * token retires the old connection before restore begins.
     */
    public synchronized boolean worldReady(WorldProfileIdentity observedIdentity, RuntimeConnectionToken token) {
        ensureOpen();
        if (observedIdentity == null || token == null) {
            throw new IllegalArgumentException("observedIdentity and token must not be null");
        }
        if (!ProfileRuntimeSession.hasSameDurableBinding(selectedIdentity, observedIdentity)) {
            return false;
        }
        if (currentSession == null || currentSession.getState() == RuntimeSessionState.FAILED) {
            return false;
        }

        Optional<RuntimeSessionConnection> active = currentSession.getActiveConnection();
        if (active.isPresent()) {
            if (active.get()
                .getToken()
                .equals(token)) {
                return false;
            }
            RuntimeConnectionToken retiringToken = active.get()
                .getToken();
            currentSession.disconnect(retiringToken);
            retiredTokens.add(retiringToken);
            installWaitingSession();
        } else if (retiredTokens.contains(token)) {
            return false;
        }

        currentSession.connect(token);
        ownershipFailure = null;
        return true;
    }

    /** Consumes the exact active connection edge and installs a fresh waiting session for reconnect. */
    public synchronized boolean worldUnavailable(WorldProfileIdentity observedIdentity, RuntimeConnectionToken token) {
        ensureOpen();
        if (observedIdentity == null || token == null) {
            throw new IllegalArgumentException("observedIdentity and token must not be null");
        }
        if (!ProfileRuntimeSession.hasSameDurableBinding(selectedIdentity, observedIdentity)
            || currentSession == null) {
            return false;
        }
        Optional<RuntimeSessionConnection> active = currentSession.getActiveConnection();
        if (!active.isPresent() || !active.get()
            .getToken()
            .equals(token)) {
            return false;
        }

        currentSession.disconnect(token);
        retiredTokens.add(token);
        installWaitingSession();
        return true;
    }

    /** Delivers a client tick only to the exact active world connection. */
    public synchronized boolean clientTick(WorldProfileIdentity observedIdentity, RuntimeConnectionToken token) {
        ensureOpen();
        if (observedIdentity == null || token == null) {
            throw new IllegalArgumentException("observedIdentity and token must not be null");
        }
        if (!ProfileRuntimeSession.hasSameDurableBinding(selectedIdentity, observedIdentity)
            || currentSession == null) {
            return false;
        }
        return currentSession.clientTick(token);
    }

    /** Clears the selected profile only after its current session has retired safely. */
    public synchronized boolean unbindProfile() {
        ensureOpen();
        if (selectedIdentity == null) {
            return false;
        }
        retireCurrent();
        selectedIdentity = null;
        retiredTokens.clear();
        ownershipFailure = null;
        return true;
    }

    @Override
    public synchronized Optional<HorizonwrightRuntime> getCurrentRuntime() {
        Optional<RuntimeSessionRuntime> active = activeRuntime();
        if (!active.isPresent()) {
            return Optional.empty();
        }
        RuntimeSessionRuntime runtime = active.get();
        if (!(runtime instanceof HorizonwrightRuntimeSessionRuntime)) {
            throw new IllegalStateException("active runtime is not backed by HorizonwrightRuntime");
        }
        return Optional.of(((HorizonwrightRuntimeSessionRuntime) runtime).getHorizonwrightRuntime());
    }

    @Override
    public synchronized Optional<IHorizonwrightController> getCurrentController() {
        Optional<RuntimeSessionRuntime> active = activeRuntime();
        return active.isPresent() ? Optional.of(
            active.get()
                .getController())
            : Optional.<IHorizonwrightController>empty();
    }

    @Override
    public synchronized ClientRuntimeSessionDiagnostic getDiagnostic() {
        if (ownershipFailure != null) {
            return new ClientRuntimeSessionDiagnostic(
                RuntimeSessionState.FAILED,
                ownershipFailure.getMessage(),
                selectedIdentity,
                ownershipFailure);
        }
        if (currentSession == null) {
            RuntimeSessionState state = closed ? RuntimeSessionState.RETIRED : RuntimeSessionState.UNBOUND;
            String message = closed ? "Runtime session manager is retired" : "No profile is selected";
            return new ClientRuntimeSessionDiagnostic(state, message, selectedIdentity, null);
        }
        RuntimeSessionState state = currentSession.getState();
        if (state == RuntimeSessionState.FAILED) {
            RuntimeSessionException failure = currentSession.getFailure()
                .orElse(new RuntimeSessionException("Runtime session failed without a diagnostic"));
            return new ClientRuntimeSessionDiagnostic(state, failure.getMessage(), selectedIdentity, failure);
        }
        if (state == RuntimeSessionState.ACTIVE) {
            return new ClientRuntimeSessionDiagnostic(state, "Runtime session is active", selectedIdentity, null);
        }
        if (state == RuntimeSessionState.WAITING_FOR_WORLD) {
            return new ClientRuntimeSessionDiagnostic(
                state,
                "Waiting for a matching world connection",
                selectedIdentity,
                null);
        }
        return new ClientRuntimeSessionDiagnostic(state, "Runtime session is retired", selectedIdentity, null);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        retireCurrent();
        currentSession = null;
        selectedIdentity = null;
        retiredTokens.clear();
        closed = true;
    }

    private Optional<RuntimeSessionRuntime> activeRuntime() {
        if (currentSession == null || currentSession.getState() != RuntimeSessionState.ACTIVE) {
            return Optional.empty();
        }
        return currentSession.getActiveRuntime();
    }

    private void installWaitingSession() {
        ProfileRuntimeSession next = new ProfileRuntimeSession(runtimeFactory, clock);
        try {
            RuntimeSessionPersistence persistence = persistenceFactory.create(selectedIdentity);
            if (persistence == null) {
                throw new IllegalStateException("persistence factory returned null");
            }
            next.bind(selectedIdentity, persistence);
        } catch (RuntimeException failure) {
            ownershipFailure = failure instanceof RuntimeSessionException ? (RuntimeSessionException) failure
                : new RuntimeSessionException("Could not create a waiting runtime session", failure);
            currentSession = next.getState() == RuntimeSessionState.FAILED ? next : null;
            throw ownershipFailure;
        }
        currentSession = next;
        ownershipFailure = null;
    }

    private void retireCurrent() {
        if (currentSession == null) {
            return;
        }
        try {
            currentSession.close();
        } catch (RuntimeSessionException failure) {
            ownershipFailure = failure;
            throw failure;
        }
        currentSession = null;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("runtime session manager is closed");
        }
    }
}
