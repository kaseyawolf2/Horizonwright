package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationGeometry;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationExecutionResult;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationIntent;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationPlan;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationPlanner;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationPlanningWindow;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationReducer;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationResultApplication;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationResultDisposition;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTarget;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetBatch;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetResult;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

/**
 * Resumable clean-volume excavation bridge.
 *
 * <p>
 * Each plan contains exactly one target. Submission never advances persisted state; only an exact, post-action
 * confirmation accepted by {@link ExcavationReducer} advances the frontier.
 */
final class ExcavationTaskRunner implements TaskRunner {

    private static final int TARGETS_PER_PLAN = 1;
    private static final long POLL_DELAY_MILLIS = 0L;
    private static final Set<ActionCapability> REQUIRED_CAPABILITIES = Collections
        .unmodifiableSet(EnumSet.of(ActionCapability.LOOK, ActionCapability.DIG));

    private final TaskSpec spec;
    private final CylinderExcavationSpec cylinder;
    private final ExcavationRuntimeAccess runtime;

    private TaskCheckpoint taskCheckpoint;
    private ExcavationCheckpoint excavationCheckpoint;
    private boolean restoredAuthorityNeedsRebind;
    private ExcavationBackend activeBackend;
    private ExcavationPlan activePlan;
    private ExcavationActionRequest activeRequest;
    private ExcavationActionHandle activeHandle;
    private ActionLease activeLease;

    ExcavationTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, ExcavationRuntimeAccess runtime) {
        if (checkpoint == null || runtime == null) {
            throw new IllegalArgumentException("checkpoint and runtime must not be null");
        }
        this.spec = spec;
        this.cylinder = ExcavationTask.parse(spec);
        this.runtime = runtime;
        this.taskCheckpoint = checkpoint;
        this.excavationCheckpoint = ExcavationTaskCheckpointCodec.decode(cylinder, checkpoint);
        if (excavationCheckpoint != null && excavationCheckpoint.isComplete()) {
            throw new IllegalArgumentException("completed excavation checkpoint cannot be resumed");
        }
        this.restoredAuthorityNeedsRebind = excavationCheckpoint != null;
    }

    @Override
    public synchronized StepResult step(TaskStepContext context) {
        requireContext(context);
        if (context.isSuspensionRequested()) {
            return suspend(context);
        }
        if (!context.getActions()
            .isAuthoritative()) {
            RuntimeException stopFailure = stopActive();
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                appendCleanup("Excavation runner no longer owns the authoritative action epoch", stopFailure),
                false);
        }
        if (runtime.isDryRun()) {
            RuntimeException stopFailure = stopActive();
            if (stopFailure != null) {
                return StepResult.failed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Excavation dry-run cleanup failed: " + describe(stopFailure),
                    false);
            }
            return StepResult.blocked(
                context.getActionEpoch(),
                taskCheckpoint,
                BlockedReason.missingRequirement(
                    "Dry-run mode prevents clean-volume excavation from acquiring gameplay capabilities.",
                    spec.getId(),
                    "live action execution",
                    "Disable dry-run, then resume this task."));
        }

        ExcavationBackend backend = runtime.getExcavationBackend();
        ExcavationBackendAvailability availability = availability(backend);
        if (backend == null || !availability.isAvailable()) {
            RuntimeException stopFailure = stopActive();
            return StepResult.blocked(
                context.getActionEpoch(),
                taskCheckpoint,
                BlockedReason.missingRequirement(
                    appendCleanup(availability.getDiagnostic(), stopFailure),
                    spec.getId(),
                    "an installed, version-tested excavation backend",
                    "Install or enable the tested excavation integration, then resume this task."));
        }
        if (activeHandle != null && activeBackend != backend) {
            RuntimeException stopFailure = stopActive();
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                appendCleanup("Excavation backend changed while an action was active", stopFailure),
                true);
        }
        if (needsAuthorityBinding(context)) {
            RuntimeException stopFailure = stopActive();
            if (stopFailure != null) {
                return StepResult.failed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Excavation authority rebind cleanup failed: " + describe(stopFailure),
                    false);
            }
            bindAuthority(context.getActionEpoch());
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Bound excavation checkpoint revision " + excavationCheckpoint.getTaskRevision()
                    + " to action epoch "
                    + context.getActionEpoch());
        }
        if (activeHandle != null) {
            return observeAction(context, backend);
        }
        return observeAndSubmit(context, backend);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) {
            throw new IllegalArgumentException("interruption must not be null");
        }
        RuntimeException failure = stopActive();
        restoredAuthorityNeedsRebind = true;
        if (failure != null) {
            throw failure;
        }
    }

    private StepResult suspend(TaskStepContext context) {
        RuntimeException stopFailure = stopActive();
        if (stopFailure != null) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation could not stop safely: " + describe(stopFailure),
                false);
        }
        if (excavationCheckpoint == null) {
            excavationCheckpoint = ExcavationCheckpoint.start(cylinder, nextRevision(), context.getActionEpoch());
            taskCheckpoint = ExcavationTaskCheckpointCodec.encode(cylinder, excavationCheckpoint);
        }
        excavationCheckpoint = copyWithAuthority(
            nextRevision(),
            context.getActionEpoch(),
            ExcavationSuspensionReason.PREEMPTED);
        taskCheckpoint = ExcavationTaskCheckpointCodec.encode(cylinder, excavationCheckpoint);
        restoredAuthorityNeedsRebind = true;
        return StepResult.safeSuspension(
            context.getActionEpoch(),
            taskCheckpoint,
            "Excavation stopped before advancing its current target");
    }

    private StepResult observeAndSubmit(TaskStepContext context, ExcavationBackend backend) {
        ExcavationTargetBatch batch = CylinderExcavationGeometry
            .nextBatch(cylinder, excavationCheckpoint.getFrontier(), TARGETS_PER_PLAN);
        if (batch.isEmpty()) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Non-complete excavation checkpoint produced no target",
                false);
        }
        ExcavationTarget target = batch.getTargets()
            .get(0);
        ExcavationObservationRequest observationRequest = new ExcavationObservationRequest(
            spec.getId(),
            excavationCheckpoint.getTaskRevision(),
            context.getActionEpoch(),
            cylinder.getGeometryKey(),
            excavationCheckpoint.getFrontier(),
            target.getPosition());
        ExcavationObservationResult observed;
        try {
            observed = backend.observe(observationRequest);
            validateObservation(observationRequest, observed);
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation observation failed: " + describe(failure),
                true);
        }
        if (!isLiveAuthority(context, backend)) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation authority changed while observing its next target",
                false);
        }

        ExcavationPlanningWindow window = new ExcavationPlanningWindow(
            excavationCheckpoint.getTaskRevision(),
            context.getActionEpoch(),
            batch,
            Collections.singletonList(observed.getObservation()));
        ExcavationPlan plan = ExcavationPlanner.calculate(cylinder, window, null);
        if (plan.getIntents()
            .size() != TARGETS_PER_PLAN
            || !plan.getManagedIntents()
                .isEmpty()) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Clean-volume excavation planner violated the single-target contract",
                false);
        }

        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(REQUIRED_CAPABILITIES);
        if (!acquired.isPresent()) {
            return StepResult.waitFor(
                context.getActionEpoch(),
                taskCheckpoint,
                POLL_DELAY_MILLIS,
                "waiting for LOOK and DIG capabilities");
        }
        ActionLease lease = acquired.get();
        ExcavationIntent intent = plan.getIntents()
            .get(0);
        String requestId = spec.getId() + "-excavate-" + plan.getTaskRevision();
        ExcavationActionRequest actionRequest = new ExcavationActionRequest(
            requestId,
            spec.getId(),
            plan.getTaskRevision(),
            plan.getActionEpoch(),
            plan.getGeometryKey(),
            plan.getStartFrontier(),
            intent);
        ExcavationActionHandle handle = null;
        try {
            if (!lease.isValid() || lease.getEpoch() != context.getActionEpoch()
                || !isLiveAuthority(context, backend)) {
                throw new IllegalStateException("excavation authority changed before action submission");
            }
            handle = backend.execute(actionRequest, lease);
            if (handle == null) {
                throw new IllegalStateException("excavation backend returned no action handle");
            }
            if (!requestId.equals(handle.getRequestId())) {
                throw new IllegalStateException("excavation backend returned a mismatched action handle");
            }
            if (!lease.isValid() || !isLiveAuthority(context, backend)) {
                throw new IllegalStateException("excavation authority changed after action submission");
            }
            activeBackend = backend;
            activePlan = plan;
            activeRequest = actionRequest;
            activeHandle = handle;
            activeLease = lease;
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Submitted one fingerprint-bound excavation action at " + intent.getPosition());
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = cancelAndClose(handle, lease);
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation action submission failed: " + describe(failure),
                true);
        }
    }

    private StepResult observeAction(TaskStepContext context, ExcavationBackend backend) {
        if (activeLease == null || activePlan == null
            || activeRequest == null
            || !activeLease.isValid()
            || activeLease.getEpoch() != context.getActionEpoch()
            || !isLiveAuthority(context, backend)) {
            RuntimeException stopFailure = stopActive();
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                appendCleanup("Excavation action lease is no longer authoritative", stopFailure),
                false);
        }
        ExcavationActionProgress progress;
        try {
            progress = activeHandle.progress();
            validateProgress(progress);
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = stopActive();
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation action progress failed: " + describe(failure),
                true);
        }
        switch (progress.getState()) {
            case SUBMITTED:
            case EXECUTING:
                return StepResult
                    .waitFor(context.getActionEpoch(), taskCheckpoint, POLL_DELAY_MILLIS, progress.getDetail());
            case CONFIRMED:
                return applyConfirmation(context, backend, progress);
            case CANCELLED:
            case FAILED:
                RuntimeException stopFailure = stopActive();
                return StepResult.failed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    appendCleanup(progress.getDetail(), stopFailure),
                    true);
            default:
                RuntimeException unknownFailure = stopActive();
                return StepResult.failed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    appendCleanup("Unknown excavation action state: " + progress.getState(), unknownFailure),
                    false);
        }
    }

    private StepResult applyConfirmation(TaskStepContext context, ExcavationBackend backend,
        ExcavationActionProgress progress) {
        ConfirmedExcavationTargetResult confirmation = progress.getConfirmation()
            .orElseThrow(() -> new IllegalStateException("confirmed action omitted its result"));
        try {
            validateConfirmation(confirmation);
            if (!isLiveAuthority(context, backend) || activeLease == null || !activeLease.isValid()) {
                throw new IllegalStateException("excavation authority changed before confirmation was applied");
            }
            ExcavationTargetResult targetResult = confirmation.getTargetResult();
            ExcavationExecutionResult execution = new ExcavationExecutionResult(
                activePlan,
                Collections.singletonList(targetResult),
                ExcavationSuspensionReason.NONE);
            ExcavationResultApplication application = ExcavationReducer.apply(excavationCheckpoint, execution);
            if (!application.wasApplied()) {
                throw new IllegalStateException(
                    "excavation confirmation was rejected as " + application.getDisposition());
            }
            if (application.getDisposition() != ExcavationResultDisposition.APPLIED) {
                throw new IllegalStateException("excavation reducer returned an inconsistent disposition");
            }
            RuntimeException releaseFailure = releaseConfirmed();
            if (releaseFailure != null) {
                throw releaseFailure;
            }
            excavationCheckpoint = application.getCheckpoint();
            taskCheckpoint = ExcavationTaskCheckpointCodec.encode(cylinder, excavationCheckpoint);
            if (excavationCheckpoint.isComplete()) {
                return StepResult.completed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Clean-volume excavation completed after confirmed target " + targetResult.getPosition());
            }
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Confirmed excavation target " + targetResult.getPosition()
                    + "; "
                    + excavationCheckpoint.getProgress()
                        .getRemaining()
                    + " blocks remain");
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = stopActive();
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation confirmation rejected: " + describe(failure),
                false);
        }
    }

    private boolean needsAuthorityBinding(TaskStepContext context) {
        return excavationCheckpoint == null || restoredAuthorityNeedsRebind
            || excavationCheckpoint.isSuspended()
            || excavationCheckpoint.getActionEpoch() != context.getActionEpoch();
    }

    private void bindAuthority(long actionEpoch) {
        if (excavationCheckpoint == null) {
            excavationCheckpoint = ExcavationCheckpoint.start(cylinder, nextRevision(), actionEpoch);
        } else {
            excavationCheckpoint = copyWithAuthority(nextRevision(), actionEpoch, ExcavationSuspensionReason.NONE);
        }
        taskCheckpoint = ExcavationTaskCheckpointCodec.encode(cylinder, excavationCheckpoint);
        restoredAuthorityNeedsRebind = false;
    }

    private ExcavationCheckpoint copyWithAuthority(long taskRevision, long actionEpoch,
        ExcavationSuspensionReason suspensionReason) {
        return ExcavationCheckpoint.restore(
            cylinder,
            taskRevision,
            actionEpoch,
            excavationCheckpoint.getFrontier(),
            excavationCheckpoint.getProgress(),
            suspensionReason);
    }

    private long nextRevision() {
        if (taskCheckpoint.getRevision() == Long.MAX_VALUE) {
            throw new IllegalStateException("excavation task checkpoint revision exhausted");
        }
        return taskCheckpoint.getRevision() + 1L;
    }

    private void validateObservation(ExcavationObservationRequest request, ExcavationObservationResult result) {
        if (result == null) {
            throw new IllegalStateException("excavation backend returned no observation");
        }
        ExcavationObservation observation = result.getObservation();
        if (result.getTaskRevision() != request.getTaskRevision() || result.getActionEpoch() != request.getActionEpoch()
            || !result.getGeometryKey()
                .equals(request.getGeometryKey())
            || !result.getStartFrontier()
                .equals(request.getStartFrontier())
            || !observation.getPosition()
                .equals(request.getPosition())) {
            throw new IllegalStateException("excavation backend returned a stale or mismatched observation");
        }
    }

    private void validateProgress(ExcavationActionProgress progress) {
        if (progress == null) {
            throw new IllegalStateException("excavation backend returned no action progress");
        }
        if (activeRequest == null || !activeRequest.getRequestId()
            .equals(progress.getRequestId())) {
            throw new IllegalStateException("excavation progress belongs to another request");
        }
    }

    private void validateConfirmation(ConfirmedExcavationTargetResult confirmation) {
        ExcavationIntent intent = activeRequest.getIntent();
        if (confirmation.getTaskRevision() != activeRequest.getTaskRevision()
            || confirmation.getActionEpoch() != activeRequest.getActionEpoch()
            || !confirmation.getGeometryKey()
                .equals(activeRequest.getGeometryKey())
            || !confirmation.getStartFrontier()
                .equals(activeRequest.getStartFrontier())
            || !confirmation.getObservedFingerprint()
                .equals(intent.getObservedFingerprint())
            || !confirmation.getTargetResult()
                .getPosition()
                .equals(intent.getPosition())) {
            throw new IllegalStateException("excavation backend returned a stale or mismatched confirmation");
        }
    }

    private boolean isLiveAuthority(TaskStepContext context, ExcavationBackend backend) {
        return context.getActions()
            .isAuthoritative() && context.getActionEpoch() == excavationCheckpoint.getActionEpoch()
            && runtime.getExcavationBackend() == backend
            && !runtime.isDryRun();
    }

    private RuntimeException releaseConfirmed() {
        ActionLease lease = activeLease;
        clearActiveReferences();
        if (lease == null) {
            return null;
        }
        try {
            lease.close();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private RuntimeException stopActive() {
        ExcavationActionHandle handle = activeHandle;
        ActionLease lease = activeLease;
        clearActiveReferences();
        return cancelAndClose(handle, lease);
    }

    private void clearActiveReferences() {
        activeBackend = null;
        activePlan = null;
        activeRequest = null;
        activeHandle = null;
        activeLease = null;
    }

    private static RuntimeException cancelAndClose(ExcavationActionHandle handle, ActionLease lease) {
        RuntimeException failure = null;
        if (handle != null) {
            try {
                handle.cancel();
            } catch (RuntimeException cancellationFailure) {
                failure = cancellationFailure;
            }
        }
        if (lease != null) {
            try {
                lease.close();
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

    private void requireContext(TaskStepContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!spec.equals(context.getSpec())) {
            throw new IllegalArgumentException("Excavation runner received another task specification");
        }
        if (!taskCheckpoint.equals(context.getCheckpoint())) {
            throw new IllegalStateException("Excavation checkpoint diverged from controller state");
        }
    }

    private static ExcavationBackendAvailability availability(ExcavationBackend backend) {
        if (backend == null) {
            return ExcavationBackendAvailability.unavailable("No excavation backend is configured");
        }
        try {
            ExcavationBackendAvailability availability = backend.availability();
            return availability == null
                ? ExcavationBackendAvailability.unavailable("Excavation backend returned no availability status")
                : availability;
        } catch (RuntimeException failure) {
            return ExcavationBackendAvailability
                .unavailable("Excavation backend availability failed: " + describe(failure));
        }
    }

    private static String appendCleanup(String detail, RuntimeException cleanupFailure) {
        return cleanupFailure == null ? detail : detail + "; cleanup failed: " + describe(cleanupFailure);
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass()
            .getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }
}
