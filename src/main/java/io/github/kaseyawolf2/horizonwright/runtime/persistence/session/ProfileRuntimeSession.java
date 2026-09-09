package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.RestoredTaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;

/**
 * Serialized lifecycle owner for one explicitly selected profile/world binding.
 *
 * <p>
 * A connection is admitted only after its durable envelope has loaded successfully and matches the bound profile,
 * server, and world fingerprint. The runtime is then created fresh and restored exactly once before becoming active.
 * Changed task checkpoints and control state are saved synchronously before another client tick can act.
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
    private Map<String, List<?>> lastSavedProgress;
    private UnresolvedDeathState lastSavedDeathState;

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
            lastSavedProgress = durableProgress(
                freshRuntime.getController()
                    .exportState());
            lastSavedDeathState = freshRuntime.snapshotUnresolvedDeathState();
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
            saveChangedProgress();
            return true;
        } catch (Exception tickFailure) {
            RuntimeException cause = tickFailure instanceof RuntimeException ? (RuntimeException) tickFailure
                : new RuntimeSessionException("Could not save task progress before the next action", tickFailure);
            finishActive(true, cause);
            throw failure;
        }
    }

    /** Synchronous commit: a later runner transition cannot execute until this checkpoint reaches durable storage. */
    private void saveChangedProgress()
        throws io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceException {
        TaskControllerState current = runtime.getController()
            .exportState();
        Map<String, List<?>> progress = durableProgress(current);
        UnresolvedDeathState death = runtime.snapshotUnresolvedDeathState();
        if (progress.equals(lastSavedProgress) && Objects.equals(death, lastSavedDeathState)) return;
        long now = clock.nowEpochMillis();
        if (now < 0L) throw new IllegalStateException("runtime session clock returned a negative timestamp");
        RuntimeEnvelope saved = persistence.save(now, activeConnection, runtime);
        if (saved == null || !hasSameDurableBinding(identity, saved))
            throw new IllegalStateException("saved task progress does not match the selected profile/world");
        Map<String, List<?>> savedProgress = durableProgress(saved.getTaskControllerState());
        if (!savedProgress.equals(progress))
            throw new IllegalStateException("saved task progress did not include the exact prepared checkpoint");
        lastSavedProgress = savedProgress;
        lastSavedDeathState = saved.getUnresolvedDeathState();
    }

    /** Excludes ticking clocks, remaining wait countdowns and progress prose; includes every gameplay checkpoint. */
    private static Map<String, List<?>> durableProgress(TaskControllerState state) {
        Map<String, List<?>> progress = new LinkedHashMap<>();
        progress.put("epoch", Arrays.asList(state.getLastActionEpoch()));
        for (RestoredTaskSnapshot task : state.getTasks()) {
            progress.put(
                "task:" + task.getSpec()
                    .getId(),
                Arrays.asList(
                    task.getSpec(),
                    task.getCheckpoint(),
                    task.getState(),
                    task.getRetryCount(),
                    task.getSuspensionReason(),
                    task.getBlockedReason(),
                    task.getQueuePosition(),
                    task.getSourceScheduleId()));
        }
        for (ScheduleSnapshot schedule : state.getScheduler()
            .getSchedules()) {
            progress.put(
                "schedule:" + schedule.getRule()
                    .getId(),
                Arrays.asList(
                    schedule.getRule(),
                    schedule.getState(),
                    schedule.getSequence(),
                    schedule.getNextConnectedDueMillis(),
                    schedule.getLastWorldOccurrence(),
                    schedule.isIdleLatched(),
                    schedule.getLastTaskId(),
                    schedule.getTotalRuns(),
                    schedule.getCatchUpRuns()));
        }
        return progress;
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

    /** Resolves the owned runtime only after restore completed and while this session remains active. */
    public synchronized Optional<RuntimeSessionRuntime> getActiveRuntime() {
        return state == RuntimeSessionState.ACTIVE ? Optional.of(runtime) : Optional.<RuntimeSessionRuntime>empty();
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
            retiringRuntime.prepareDisconnect();
        } catch (RuntimeException disconnectFailure) {
            terminalFailure = append(terminalFailure, "connection safety retirement failed", disconnectFailure);
        }

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
