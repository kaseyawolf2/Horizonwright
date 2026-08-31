package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.base.SleepActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.SleepDecision;
import io.github.kaseyawolf2.horizonwright.core.base.SleepPlanner;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

/** Restart-safe runner for one conservative normal-bed sleep attempt. */
final class SleepTaskRunner implements TaskRunner {

    private final TaskSpec spec;
    private final SleepRuntimeAccess runtime;
    private final SleepPlanner planner = new SleepPlanner();
    private TaskCheckpoint checkpoint;
    private SleepBackend activeBackend;
    private SleepBackend.ActionHandle activeHandle;
    private ActionLease activeLease;
    private String activeRequestId;

    SleepTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, SleepRuntimeAccess runtime) {
        if (checkpoint == null || runtime == null)
            throw new IllegalArgumentException("checkpoint and runtime are required");
        SleepTask.bedLocationId(spec);
        if (checkpoint.getRevision() != 0L || !checkpoint.getValues()
            .isEmpty()) {
            throw new IllegalArgumentException("completed sleep task cannot be resumed");
        }
        this.spec = spec;
        this.checkpoint = checkpoint;
        this.runtime = runtime;
    }

    @Override
    public synchronized StepResult step(TaskStepContext context) {
        requireContext(context);
        if (context.isSuspensionRequested()) return suspend(context);
        if (!context.getActions()
            .isAuthoritative()) return failure(context, "Sleep task lost its action epoch", false);
        if (runtime.isDryRun()) {
            cancelActive();
            return blocked(context, "Dry-run mode prevents normal bed interaction", "live sleep execution");
        }
        SleepBackend backend = runtime.getSleepBackend();
        SleepBackend.Availability availability = availability(backend);
        if (backend == null || !availability.isAvailable()) {
            cancelActive();
            return blocked(context, availability.getDiagnostic(), "an installed, version-tested sleep backend");
        }
        if (activeHandle != null) return observeAction(context, backend);
        return observeAndPlan(context, backend);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) throw new IllegalArgumentException("interruption is required");
        cancelActive();
    }

    private StepResult observeAndPlan(TaskStepContext context, SleepBackend backend) {
        SleepBackend.ObservationRequest request = new SleepBackend.ObservationRequest(
            spec.getId(),
            SleepTask.bedLocationId(spec),
            context.getActionEpoch());
        try {
            SleepBackend.ObservationSnapshot snapshot = backend.observe(request);
            validate(request, snapshot);
            SleepDecision decision = planner.plan(snapshot.getObservation());
            if (decision.getAction() == SleepActionKind.SKIP_DAYTIME) {
                return completed(context, "Sleep no longer required; verified daytime");
            }
            if (!decision.requiresInteraction()) {
                return blocked(context, holdDetail(decision.getAction()), holdRequirement(decision.getAction()));
            }
            return submit(context, backend, decision);
        } catch (RuntimeException failure) {
            return StepResult
                .failed(context.getActionEpoch(), checkpoint, "Sleep observation failed: " + describe(failure), true);
        }
    }

    private StepResult submit(TaskStepContext context, SleepBackend backend, SleepDecision decision) {
        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(
                Collections.unmodifiableSet(
                    EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.USE)));
        if (!acquired.isPresent()) {
            return StepResult
                .waitFor(context.getActionEpoch(), checkpoint, 0L, "waiting for sleep action capabilities");
        }
        ActionLease lease = acquired.get();
        String requestId = spec.getId() + "-sleep-" + context.getActionEpoch();
        SleepBackend.ActionRequest request = new SleepBackend.ActionRequest(
            requestId,
            spec.getId(),
            SleepTask.bedLocationId(spec),
            context.getActionEpoch(),
            decision);
        try {
            if (!lease.isValid() || runtime.getSleepBackend() != backend)
                throw new IllegalStateException("sleep authority changed");
            SleepBackend.ActionHandle handle = backend.execute(request, lease);
            if (handle == null || !requestId.equals(handle.getRequestId())) {
                throw new IllegalStateException("sleep backend returned a mismatched action handle");
            }
            activeBackend = backend;
            activeHandle = handle;
            activeLease = lease;
            activeRequestId = requestId;
            return StepResult.progress(context.getActionEpoch(), checkpoint, "Submitted normal bed interaction");
        } catch (RuntimeException failure) {
            lease.close();
            return StepResult.failed(
                context.getActionEpoch(),
                checkpoint,
                "Sleep action submission failed: " + describe(failure),
                true);
        }
    }

    private StepResult observeAction(TaskStepContext context, SleepBackend backend) {
        if (activeBackend != backend || runtime.getSleepBackend() != backend
            || activeLease == null
            || !activeLease.isValid()
            || activeLease.getEpoch() != context.getActionEpoch()) {
            return failure(context, "Sleep action authority changed before confirmation", false);
        }
        try {
            SleepBackend.ActionProgress progress = activeHandle.progress();
            if (progress == null || !activeRequestId.equals(progress.getRequestId())) {
                throw new IllegalStateException("sleep progress belongs to another request");
            }
            if (progress.getState() == SleepBackend.ActionState.SUBMITTED
                || progress.getState() == SleepBackend.ActionState.EXECUTING) {
                return StepResult.waitFor(context.getActionEpoch(), checkpoint, 0L, progress.getDetail());
            }
            if (progress.getState() == SleepBackend.ActionState.CONFIRMED) {
                releaseActive();
                return completed(context, progress.getDetail());
            }
            return failure(context, progress.getDetail(), progress.getState() == SleepBackend.ActionState.FAILED);
        } catch (RuntimeException failure) {
            return failure(context, "Sleep confirmation failed: " + describe(failure), false);
        }
    }

    private StepResult completed(TaskStepContext context, String detail) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("bedLocationId", SleepTask.bedLocationId(spec));
        values.put("verified", "true");
        checkpoint = new TaskCheckpoint(1L, values);
        return StepResult.completed(context.getActionEpoch(), checkpoint, detail);
    }

    private StepResult blocked(TaskStepContext context, String detail, String requirement) {
        return StepResult.blocked(
            context.getActionEpoch(),
            checkpoint,
            BlockedReason.missingRequirement(
                detail,
                spec.getId(),
                requirement,
                "Resolve the requirement, then resume this sleep task."));
    }

    private StepResult suspend(TaskStepContext context) {
        cancelActive();
        return StepResult
            .safeSuspension(context.getActionEpoch(), checkpoint, "Sleep stopped before any completion was recorded");
    }

    private StepResult failure(TaskStepContext context, String detail, boolean retryable) {
        cancelActive();
        return StepResult.failed(context.getActionEpoch(), checkpoint, detail, retryable);
    }

    private void releaseActive() {
        ActionLease lease = activeLease;
        clearActive();
        if (lease != null) lease.close();
    }

    private void cancelActive() {
        SleepBackend.ActionHandle handle = activeHandle;
        ActionLease lease = activeLease;
        clearActive();
        if (handle != null) handle.cancel();
        if (lease != null) lease.close();
    }

    private void clearActive() {
        activeBackend = null;
        activeHandle = null;
        activeLease = null;
        activeRequestId = null;
    }

    private void requireContext(TaskStepContext context) {
        if (context == null || !spec.equals(context.getSpec()))
            throw new IllegalArgumentException("sleep task context mismatched");
        if (!checkpoint.equals(context.getCheckpoint())) throw new IllegalStateException("sleep checkpoint diverged");
    }

    private static SleepBackend.Availability availability(SleepBackend backend) {
        if (backend == null) return SleepBackend.Availability.unavailable("No sleep backend is configured");
        SleepBackend.Availability value = backend.availability();
        return value == null ? SleepBackend.Availability.unavailable("Sleep backend returned no availability") : value;
    }

    private static void validate(SleepBackend.ObservationRequest request, SleepBackend.ObservationSnapshot snapshot) {
        if (snapshot == null || !request.getTaskId()
            .equals(snapshot.getTaskId())
            || !request.getBedLocationId()
                .equals(snapshot.getBedLocationId())
            || request.getActionEpoch() != snapshot.getActionEpoch()) {
            throw new IllegalStateException("sleep backend returned stale or mismatched evidence");
        }
    }

    private static String holdDetail(SleepActionKind action) {
        switch (action) {
            case HOLD_INVALID_DIMENSION:
                return "Normal sleeping is invalid in the current dimension";
            case HOLD_DANGER:
                return "Danger is too close to sleep safely";
            case HOLD_WRONG_DIMENSION:
                return "The registered bed is in another dimension";
            case HOLD_PROVIDER_UNAVAILABLE:
                return "The registered bed is unavailable";
            case HOLD_UNLOADED_OR_UNREACHABLE:
                return "The registered bed is not loaded and reachable";
            default:
                return "Sleep cannot safely continue: " + action;
        }
    }

    private static String holdRequirement(SleepActionKind action) {
        switch (action) {
            case HOLD_DANGER:
                return "a danger-free sleep area";
            case HOLD_WRONG_DIMENSION:
                return "the registered bed dimension";
            case HOLD_PROVIDER_UNAVAILABLE:
                return "the exact registered bed block";
            case HOLD_UNLOADED_OR_UNREACHABLE:
                return "a loaded and reachable registered bed";
            default:
                return "a dimension permitting normal sleep";
        }
    }

    private static String describe(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }
}
