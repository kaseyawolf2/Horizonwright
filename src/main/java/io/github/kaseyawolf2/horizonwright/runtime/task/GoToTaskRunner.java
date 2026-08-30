package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

/** Resumable, lease-gated GoTo state machine. */
final class GoToTaskRunner implements TaskRunner {

    private static final String PHASE = "phase";
    private static final String ATTEMPT = "attempt";
    private static final String REQUEST_ID = "requestId";
    private static final String READY = "ready";
    private static final String NAVIGATING = "navigating";
    private static final String SUSPENDED = "suspended";
    private static final String COMPLETED = "completed";
    private static final long POLL_DELAY_MILLIS = 0L;
    private static final Set<ActionCapability> REQUIRED_CAPABILITIES = Collections
        .unmodifiableSet(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK));

    private final TaskSpec spec;
    private final GoToTask.Target target;
    private final NavigationRuntimeAccess navigation;

    private TaskCheckpoint checkpoint;
    private int attempt;
    private NavigationBackend submittedBackend;
    private NavigationHandle handle;
    private ActionLease lease;

    GoToTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, NavigationRuntimeAccess navigation) {
        if (checkpoint == null || navigation == null) {
            throw new IllegalArgumentException("checkpoint and navigation must not be null");
        }
        this.spec = spec;
        this.target = GoToTask.parse(spec);
        this.navigation = navigation;
        this.checkpoint = checkpoint;
        this.attempt = parseCheckpoint(checkpoint);
    }

    @Override
    public synchronized StepResult step(TaskStepContext context) {
        requireContext(context);
        if (context.isSuspensionRequested()) {
            RuntimeException stopFailure = stopActive("navigation suspended");
            checkpoint = advanceCheckpoint(SUSPENDED, "");
            return stopFailure == null
                ? StepResult.safeSuspension(context.getActionEpoch(), checkpoint, "navigation stopped at a safe point")
                : StepResult.failed(
                    context.getActionEpoch(),
                    checkpoint,
                    "navigation could not stop safely: " + describe(stopFailure),
                    false);
        }
        if (!context.getActions()
            .isAuthoritative()) {
            RuntimeException stopFailure = stopActive("stale task action epoch");
            String detail = "GoTo runner no longer owns the authoritative action epoch";
            if (stopFailure != null) {
                detail += "; cleanup failed: " + describe(stopFailure);
            }
            return StepResult.failed(context.getActionEpoch(), checkpoint, detail, false);
        }
        if (navigation.isDryRun()) {
            RuntimeException stopFailure = stopActive("dry-run enabled");
            if (stopFailure != null) {
                return StepResult.failed(
                    context.getActionEpoch(),
                    checkpoint,
                    "dry-run cleanup failed: " + describe(stopFailure),
                    false);
            }
            return StepResult.blocked(
                context.getActionEpoch(),
                checkpoint,
                BlockedReason.missingRequirement(
                    "Dry-run mode prevents GoTo from acquiring gameplay capabilities.",
                    spec.getId(),
                    "live action execution",
                    "Disable dry-run, then resume this task."));
        }

        NavigationBackend currentBackend = navigation.getNavigationBackend();
        BackendAvailability availability = availability(currentBackend);
        if (currentBackend == null || !availability.isAvailable()) {
            RuntimeException stopFailure = stopActive("navigation backend unavailable");
            String detail = availability.getDiagnostic();
            if (stopFailure != null) {
                detail += "; cleanup failed: " + describe(stopFailure);
            }
            return StepResult.failed(context.getActionEpoch(), checkpoint, detail, true);
        }
        if (handle == null) {
            return submit(context, currentBackend);
        }
        if (submittedBackend != currentBackend) {
            RuntimeException stopFailure = stopActive("navigation backend replaced");
            String detail = "Navigation backend changed while GoTo was active";
            if (stopFailure != null) {
                detail += "; cleanup failed: " + describe(stopFailure);
            }
            return StepResult.failed(context.getActionEpoch(), checkpoint, detail, true);
        }
        if (lease == null || lease.getEpoch() != context.getActionEpoch() || !lease.isValid()) {
            RuntimeException stopFailure = stopActive("navigation lease is no longer valid");
            String detail = "GoTo movement/look lease is no longer authoritative";
            if (stopFailure != null) {
                detail += "; cleanup failed: " + describe(stopFailure);
            }
            return StepResult.failed(context.getActionEpoch(), checkpoint, detail, false);
        }
        return observe(context);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) {
            throw new IllegalArgumentException("interruption must not be null");
        }
        RuntimeException failure = stopActive("task interrupted: " + interruption.getReason());
        if (failure != null) {
            throw failure;
        }
    }

    private StepResult submit(TaskStepContext context, NavigationBackend backend) {
        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(REQUIRED_CAPABILITIES);
        if (!acquired.isPresent()) {
            return StepResult.waitFor(
                context.getActionEpoch(),
                checkpoint,
                POLL_DELAY_MILLIS,
                "waiting for MOVEMENT and LOOK capabilities");
        }

        ActionLease candidateLease = acquired.get();
        NavigationHandle candidateHandle = null;
        try {
            if (!candidateLease.isValid() || candidateLease.getEpoch() != context.getActionEpoch()
                || !context.getActions()
                    .isAuthoritative()
                || navigation.isDryRun()) {
                throw new IllegalStateException("action authority changed during GoTo submission");
            }
            int nextAttempt = nextAttempt();
            String requestId = spec.getId() + "-nav-" + nextAttempt;
            NavigationRequest request = new NavigationRequest(
                requestId,
                context.getActionEpoch(),
                target.dimensionId,
                target.x,
                target.y,
                target.z,
                target.tolerance);
            candidateHandle = backend.submit(request, candidateLease);
            if (candidateHandle == null) {
                throw new IllegalStateException("navigation backend returned no handle");
            }
            if (!requestId.equals(candidateHandle.getRequestId())) {
                throw new IllegalStateException("navigation backend returned a mismatched request handle");
            }
            if (!candidateLease.isValid() || !context.getActions()
                .isAuthoritative() || navigation.isDryRun()) {
                throw new IllegalStateException("action authority changed after GoTo submission");
            }

            attempt = nextAttempt;
            submittedBackend = backend;
            lease = candidateLease;
            handle = candidateHandle;
            checkpoint = advanceCheckpoint(NAVIGATING, requestId);
            NavigationProgress submitted = new NavigationProgress(
                requestId,
                context.getActionEpoch(),
                NavigationState.SUBMITTED,
                "Submitted by the Horizonwright task controller");
            navigation.publishNavigationProgress(submitted);
            return StepResult.progress(context.getActionEpoch(), checkpoint, submitted.getDetail());
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = cancelAndClose(candidateHandle, candidateLease);
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            return StepResult
                .failed(context.getActionEpoch(), checkpoint, "GoTo submission failed: " + describe(failure), true);
        }
    }

    private StepResult observe(TaskStepContext context) {
        NavigationProgress progress;
        try {
            progress = handle.progress();
            validateProgress(progress, context.getActionEpoch());
            navigation.publishNavigationProgress(progress);
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = stopActive("navigation progress failed");
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            return StepResult.failed(
                context.getActionEpoch(),
                checkpoint,
                "Navigation progress could not be verified: " + describe(failure),
                true);
        }

        switch (progress.getState()) {
            case SUBMITTED:
            case MOVING:
                return StepResult
                    .waitFor(context.getActionEpoch(), checkpoint, POLL_DELAY_MILLIS, progress.getDetail());
            case COMPLETED:
                RuntimeException releaseFailure = releaseCompleted();
                checkpoint = advanceCheckpoint(COMPLETED, progress.getRequestId());
                return releaseFailure == null
                    ? StepResult.completed(context.getActionEpoch(), checkpoint, progress.getDetail())
                    : StepResult.failed(
                        context.getActionEpoch(),
                        checkpoint,
                        "Navigation completed but its lease could not be released: " + describe(releaseFailure),
                        false);
            case CANCELLED:
            case FAILED:
                RuntimeException stopFailure = stopActive("navigation ended: " + progress.getState());
                String detail = progress.getDetail();
                if (stopFailure != null) {
                    detail += "; cleanup failed: " + describe(stopFailure);
                }
                return StepResult.failed(context.getActionEpoch(), checkpoint, detail, true);
            default:
                RuntimeException unknownFailure = stopActive("unknown navigation state");
                String unknownDetail = "Unknown navigation state: " + progress.getState();
                if (unknownFailure != null) {
                    unknownDetail += "; cleanup failed: " + describe(unknownFailure);
                }
                return StepResult.failed(context.getActionEpoch(), checkpoint, unknownDetail, false);
        }
    }

    private RuntimeException releaseCompleted() {
        ActionLease completedLease = lease;
        handle = null;
        lease = null;
        submittedBackend = null;
        if (completedLease == null) {
            return null;
        }
        try {
            completedLease.close();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private RuntimeException stopActive(String reason) {
        NavigationHandle stoppingHandle = handle;
        ActionLease stoppingLease = lease;
        handle = null;
        lease = null;
        submittedBackend = null;
        RuntimeException failure = cancelAndClose(stoppingHandle, stoppingLease);
        if (stoppingHandle != null) {
            try {
                NavigationProgress progress = stoppingHandle.progress();
                navigation.publishNavigationProgress(progress);
            } catch (RuntimeException progressFailure) {
                if (failure == null) {
                    failure = progressFailure;
                } else {
                    failure.addSuppressed(progressFailure);
                }
            }
        }
        return failure;
    }

    private static RuntimeException cancelAndClose(NavigationHandle stoppingHandle, ActionLease stoppingLease) {
        RuntimeException failure = null;
        if (stoppingHandle != null) {
            try {
                stoppingHandle.cancel();
            } catch (RuntimeException cancellationFailure) {
                failure = cancellationFailure;
            }
        }
        if (stoppingLease != null) {
            try {
                stoppingLease.close();
            } catch (RuntimeException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        return failure;
    }

    private void validateProgress(NavigationProgress progress, long actionEpoch) {
        if (progress == null) {
            throw new IllegalStateException("navigation backend returned no progress");
        }
        if (progress.getActionEpoch() != actionEpoch) {
            throw new IllegalStateException("navigation progress belongs to a stale action epoch");
        }
        if (handle == null || !handle.getRequestId()
            .equals(progress.getRequestId())) {
            throw new IllegalStateException("navigation progress belongs to another request");
        }
    }

    private void requireContext(TaskStepContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!spec.equals(context.getSpec())) {
            throw new IllegalArgumentException("GoTo runner received another task specification");
        }
        if (!checkpoint.equals(context.getCheckpoint())) {
            throw new IllegalStateException("GoTo checkpoint diverged from controller state");
        }
    }

    private int parseCheckpoint(TaskCheckpoint restored) {
        Map<String, String> values = restored.getValues();
        if (restored.getRevision() == 0L && values.isEmpty()) {
            return 0;
        }
        String phase = values.get(PHASE);
        if (!READY.equals(phase) && !NAVIGATING.equals(phase) && !SUSPENDED.equals(phase) && !COMPLETED.equals(phase)) {
            throw new IllegalArgumentException("unsupported GoTo checkpoint phase: " + phase);
        }
        if (COMPLETED.equals(phase)) {
            throw new IllegalArgumentException("completed GoTo checkpoint cannot be resumed");
        }
        String attemptValue = values.get(ATTEMPT);
        try {
            int restoredAttempt = Integer.parseInt(attemptValue);
            if (restoredAttempt < 0) {
                throw new IllegalArgumentException("GoTo checkpoint attempt must not be negative");
            }
            return restoredAttempt;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid GoTo checkpoint attempt: " + attemptValue, failure);
        }
    }

    private TaskCheckpoint advanceCheckpoint(String phase, String requestId) {
        if (checkpoint.getRevision() == Long.MAX_VALUE) {
            throw new IllegalStateException("GoTo checkpoint revision exhausted");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(PHASE, phase);
        values.put(ATTEMPT, Integer.toString(attempt));
        values.put(REQUEST_ID, requestId == null ? "" : requestId);
        return new TaskCheckpoint(checkpoint.getRevision() + 1L, values);
    }

    private int nextAttempt() {
        if (attempt == Integer.MAX_VALUE) {
            throw new IllegalStateException("GoTo navigation attempt counter exhausted");
        }
        return attempt + 1;
    }

    private static BackendAvailability availability(NavigationBackend backend) {
        if (backend == null) {
            return BackendAvailability.unavailable("No navigation backend is configured");
        }
        try {
            BackendAvailability availability = backend.availability();
            return availability == null ? BackendAvailability.unavailable("Navigation backend returned no status")
                : availability;
        } catch (RuntimeException failure) {
            return BackendAvailability.unavailable("Navigation backend status failed: " + describe(failure));
        }
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass()
            .getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }
}
