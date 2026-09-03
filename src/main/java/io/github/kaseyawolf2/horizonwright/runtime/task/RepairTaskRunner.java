package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairAssessment;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairPolicy;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairVerification;
import io.github.kaseyawolf2.horizonwright.core.repair.TinkersRepairVerifier;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

/** Durable exact-version Tinkers repair runner with no blind replay after restart. */
final class RepairTaskRunner implements TaskRunner {

    private static final Set<ActionCapability> REQUIRED_CAPABILITIES = Collections
        .unmodifiableSet(EnumSet.of(ActionCapability.CONTAINER));
    private final TaskSpec spec;
    private final RepairRuntimeAccess runtime;
    private final RepairPolicy policy;
    private TaskCheckpoint taskCheckpoint;
    private RepairTaskCheckpoint state;
    private boolean restoredUncertainPhase;
    private RepairBackend activeBackend;
    private RepairActionRequest activeRequest;
    private RepairActionHandle activeHandle;
    private ActionLease activeLease;

    RepairTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, RepairRuntimeAccess runtime) {
        this(spec, checkpoint, runtime, RepairPolicy.planDefaults());
    }

    RepairTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, RepairRuntimeAccess runtime, RepairPolicy policy) {
        if (spec == null || checkpoint == null || runtime == null || policy == null) {
            throw new IllegalArgumentException("repair runner dependencies are required");
        }
        RepairTask.stationId(spec);
        RepairTask.reservedInventorySlot(spec);
        RepairTask.predictedWorkDamage(spec);
        this.spec = spec;
        this.runtime = runtime;
        this.policy = policy;
        taskCheckpoint = checkpoint;
        state = RepairTaskCheckpointCodec.decode(spec, checkpoint);
        restoredUncertainPhase = state.getPhase() != RepairTaskCheckpoint.Phase.READY;
    }

    @Override
    public synchronized StepResult step(TaskStepContext context) {
        requireContext(context);
        if (context.isSuspensionRequested()) return suspend(context);
        if (!context.getActions()
            .isAuthoritative()) {
            return failed(context, "Repair runner no longer owns the authoritative action epoch", stopActive(), false);
        }
        if (runtime.isDryRun()) {
            return blocked(
                context,
                "Dry-run mode prevents repair container mutation.",
                "live container execution",
                "Disable dry-run, then resume this repair task.");
        }
        RepairBackend backend = runtime.getRepairBackend();
        RepairBackendAvailability availability = availability(backend);
        if (backend == null || !availability.isAvailable()) {
            if (backend != null && availability.isWaitingForOperator()) {
                return StepResult.waitFor(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    0L,
                    availability.getDiagnostic() + "; repair will continue automatically when it is open");
            }
            return blocked(
                context,
                availability.getDiagnostic(),
                "the pinned, compatible Tinkers repair station",
                "Resolve the repair-backend problem, then resume this task.");
        }
        if (activeHandle != null) {
            if (activeBackend != backend)
                return failed(context, "Repair backend changed during a transaction", stopActive(), false);
            return observeAction(context);
        }
        if (restoredUncertainPhase) return reconcileRestored(context, backend);
        if (state.getPhase() == RepairTaskCheckpoint.Phase.READY) return prepare(context, backend);
        if (state.getPhase() == RepairTaskCheckpoint.Phase.PREPARED) return executePrepared(context, backend);
        return failed(
            context,
            "Awaiting repair checkpoint has no live action and was not marked for reconciliation",
            null,
            false);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) throw new IllegalArgumentException("interruption must not be null");
        RuntimeException failure = stopActive();
        restoredUncertainPhase = state.getPhase() != RepairTaskCheckpoint.Phase.READY;
        if (failure != null) throw failure;
    }

    private StepResult prepare(TaskStepContext context, RepairBackend backend) {
        RepairObservationResult observed;
        try {
            observed = observe(context, backend);
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Repair observation failed: " + describe(failure),
                true);
        }
        RepairAssessment assessment = policy.assess(observed.getInputTool(), RepairTask.predictedWorkDamage(spec));
        if (!assessment.isRepairRequired()) {
            return StepResult.completed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Reserved tool has sufficient durability for the predicted work unit");
        }
        if (!observed.isRecognizedLayout()) {
            return blocked(
                context,
                "Open container is not the pinned Tool Station or Tool Forge layout.",
                "a recognized exact-version repair container",
                "Open the configured pinned repair station.");
        }
        if (observed.getTransaction() == null) {
            return blocked(
                context,
                "Repair is required but no exact eligible-material transaction is available.",
                "compatible repair material in an approved slot",
                "Add compatible repair material to the reserved loadout, then resume this task.");
        }
        try {
            RepairOperationValidator.validate(observed, RepairTask.reservedInventorySlot(spec));
        } catch (RuntimeException failure) {
            return blocked(
                context,
                "Pinned repair prediction was rejected: " + describe(failure),
                "a valid exact-version repair prediction",
                "Inspect the station, tool, and material slots.");
        }
        String fingerprint = RepairOperationFingerprint.fingerprint(observed);
        state = new RepairTaskCheckpoint(
            state.getRevision() + 1L,
            RepairTaskCheckpoint.Phase.PREPARED,
            state.getCompletedRepairs(),
            observed.getTransaction()
                .getTransactionId(),
            fingerprint);
        updateCheckpoint();
        return StepResult.progress(
            context.getActionEpoch(),
            taskCheckpoint,
            "Persisted exact repair operation before container execution");
    }

    private StepResult executePrepared(TaskStepContext context, RepairBackend backend) {
        RepairObservationResult observed;
        try {
            observed = observe(context, backend);
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Prepared repair revalidation failed: " + describe(failure),
                true);
        }
        RepairAssessment assessment = policy.assess(observed.getInputTool(), RepairTask.predictedWorkDamage(spec));
        if (!assessment.isRepairRequired() || !observed.isRecognizedLayout() || observed.getTransaction() == null) {
            clearToReady(false);
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Prepared repair is no longer applicable; reconciled without clicking");
        }
        try {
            RepairOperationValidator.validate(observed, RepairTask.reservedInventorySlot(spec));
        } catch (RuntimeException failure) {
            clearToReady(false);
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Repair evidence changed after preparation; discarded plan without clicking");
        }
        String fingerprint = RepairOperationFingerprint.fingerprint(observed);
        if (!state.getOperationFingerprint()
            .equals(fingerprint)
            || !state.getTransactionId()
                .equals(
                    observed.getTransaction()
                        .getTransactionId())) {
            clearToReady(false);
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Repair transaction changed after preparation; discarded plan without clicking");
        }
        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(REQUIRED_CAPABILITIES);
        if (!acquired.isPresent())
            return StepResult.waitFor(context.getActionEpoch(), taskCheckpoint, 0L, "waiting for CONTAINER capability");
        ActionLease lease = acquired.get();
        String requestId = state.getTransactionId() + "-request";
        RepairActionRequest request = new RepairActionRequest(
            requestId,
            state.getRevision(),
            context.getActionEpoch(),
            fingerprint,
            observed.getTransaction(),
            observed.getInputTool());
        RepairActionHandle handle = null;
        try {
            if (!lease.isValid() || lease.getEpoch() != context.getActionEpoch()) {
                throw new IllegalStateException("container authority changed before repair submission");
            }
            handle = backend.execute(request, lease);
            if (handle == null || !requestId.equals(handle.getRequestId())) {
                throw new IllegalStateException("repair backend returned a missing or mismatched handle");
            }
            activeBackend = backend;
            activeRequest = request;
            activeHandle = handle;
            activeLease = lease;
            state = new RepairTaskCheckpoint(
                state.getRevision() + 1L,
                RepairTaskCheckpoint.Phase.AWAITING_CONFIRMATION,
                state.getCompletedRepairs(),
                state.getTransactionId(),
                state.getOperationFingerprint());
            updateCheckpoint();
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Submitted one persisted, server-confirmed repair transaction");
        } catch (RuntimeException failure) {
            RuntimeException cleanup = cancelAndClose(handle, lease);
            if (cleanup != null) failure.addSuppressed(cleanup);
            restoredUncertainPhase = true;
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Repair submission failed: " + describe(failure),
                false);
        }
    }

    private StepResult observeAction(TaskStepContext context) {
        if (activeLease == null || !activeLease.isValid() || activeLease.getEpoch() != context.getActionEpoch()) {
            return failed(context, "Repair action lease is no longer authoritative", stopActive(), false);
        }
        RepairActionProgress progress;
        try {
            progress = activeHandle.progress();
            if (progress == null || !activeRequest.getRequestId()
                .equals(progress.getRequestId())) {
                throw new IllegalStateException("repair backend returned stale or missing progress");
            }
        } catch (RuntimeException failure) {
            return failed(context, "Repair progress failed: " + describe(failure), stopActive(), false);
        }
        switch (progress.getState()) {
            case SUBMITTED:
            case EXECUTING:
                return StepResult.waitFor(context.getActionEpoch(), taskCheckpoint, 0L, progress.getDetail());
            case CONFIRMED:
                return confirm(
                    context,
                    progress.getConfirmation()
                        .get());
            case REJECTED:
            case FAILED:
                RuntimeException stopFailure = stopActive();
                restoredUncertainPhase = true;
                return blocked(
                    context,
                    appendCleanup(
                        "Repair transaction " + progress.getState() + ": " + progress.getDetail(),
                        stopFailure),
                    "operator acknowledgement after a non-idempotent transaction failure",
                    "Inspect the tool and station, then resume to reconcile without replay.");
            default:
                return failed(context, "Unknown repair progress state", stopActive(), false);
        }
    }

    private StepResult confirm(TaskStepContext context, RepairActionConfirmation confirmation) {
        if (activeRequest.getTransaction()
            .getState() != ContainerTransactionState.COMPLETED
            || !activeRequest.getTransactionFingerprint()
                .equals(confirmation.getTransactionFingerprint())) {
            RuntimeException stopFailure = stopActive();
            restoredUncertainPhase = true;
            return blocked(
                context,
                appendCleanup("Repair confirmation did not match the completed prepared transaction", stopFailure),
                "operator acknowledgement of mismatched repair confirmation",
                "Inspect the tool and station, then resume to reconcile without replay.");
        }
        RepairVerification verification = TinkersRepairVerifier.verify(
            activeRequest.getInputTool(),
            confirmation.getOutputTool(),
            confirmation.getMaterialConsumed(),
            confirmation.isRecognizedLayout());
        if (!verification.isAccepted()) {
            RuntimeException stopFailure = stopActive();
            restoredUncertainPhase = true;
            return blocked(
                context,
                "Synchronized repair result was rejected: " + verification.getDiagnostic(),
                "verified tool identity, material consumption, durability, and reserved return slot",
                "Inspect the repaired tool and station before resuming reconciliation.");
        }
        RuntimeException closeFailure = releaseConfirmed();
        if (closeFailure != null) return failed(context, "Confirmed repair lease cleanup failed", closeFailure, false);
        clearToReady(true);
        return StepResult.progress(
            context.getActionEpoch(),
            taskCheckpoint,
            "Verified repair reduced InfiTool.Damage by " + verification.getRepairedDamage());
    }

    private StepResult reconcileRestored(TaskStepContext context, RepairBackend backend) {
        try {
            observe(context, backend);
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Uncertain repair reconciliation failed: " + describe(failure),
                true);
        }
        clearToReady(false);
        restoredUncertainPhase = false;
        return StepResult.progress(
            context.getActionEpoch(),
            taskCheckpoint,
            "Reconciled current server repair state without replaying the prior click");
    }

    private RepairObservationResult observe(TaskStepContext context, RepairBackend backend) {
        RepairObservationRequest request = new RepairObservationRequest(
            spec.getId(),
            state.getRevision(),
            context.getActionEpoch(),
            RepairTask.stationId(spec),
            RepairTask.reservedInventorySlot(spec));
        RepairObservationResult observed = backend.observe(request);
        if (observed == null || !request.getTaskId()
            .equals(observed.getTaskId())
            || request.getCheckpointRevision() != observed.getCheckpointRevision()
            || request.getActionEpoch() != observed.getActionEpoch()
            || !request.getStationId()
                .equals(observed.getStationId())
            || request.getReservedInventorySlot() != observed.getInputTool()
                .getReservedInventorySlot()) {
            throw new IllegalStateException("repair backend returned stale or mismatched evidence");
        }
        return observed;
    }

    private StepResult suspend(TaskStepContext context) {
        RuntimeException failure = stopActive();
        restoredUncertainPhase = state.getPhase() != RepairTaskCheckpoint.Phase.READY;
        if (failure != null) return StepResult.failed(
            context.getActionEpoch(),
            taskCheckpoint,
            "Repair could not stop safely: " + describe(failure),
            false);
        return StepResult.safeSuspension(
            context.getActionEpoch(),
            taskCheckpoint,
            "Repair stopped without replaying an outstanding click");
    }

    private void clearToReady(boolean completed) {
        state = new RepairTaskCheckpoint(
            state.getRevision() + 1L,
            RepairTaskCheckpoint.Phase.READY,
            state.getCompletedRepairs() + (completed ? 1 : 0),
            null,
            null);
        updateCheckpoint();
    }

    private void updateCheckpoint() {
        taskCheckpoint = RepairTaskCheckpointCodec.encode(spec, state);
    }

    private StepResult blocked(TaskStepContext context, String detail, String requirement, String action) {
        return StepResult.blocked(
            context.getActionEpoch(),
            taskCheckpoint,
            BlockedReason.missingRequirement(detail, RepairTask.stationId(spec), requirement, action));
    }

    private StepResult failed(TaskStepContext context, String detail, RuntimeException cleanup, boolean retryable) {
        return StepResult.failed(context.getActionEpoch(), taskCheckpoint, appendCleanup(detail, cleanup), retryable);
    }

    private RuntimeException releaseConfirmed() {
        ActionLease lease = activeLease;
        clearActive();
        try {
            if (lease != null) lease.close();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private RuntimeException stopActive() {
        RepairActionHandle handle = activeHandle;
        ActionLease lease = activeLease;
        clearActive();
        return cancelAndClose(handle, lease);
    }

    private void clearActive() {
        activeBackend = null;
        activeRequest = null;
        activeHandle = null;
        activeLease = null;
    }

    private static RuntimeException cancelAndClose(RepairActionHandle handle, ActionLease lease) {
        RuntimeException failure = null;
        try {
            if (handle != null) handle.cancel();
        } catch (RuntimeException current) {
            failure = current;
        }
        try {
            if (lease != null) lease.close();
        } catch (RuntimeException current) {
            if (failure == null) failure = current;
            else failure.addSuppressed(current);
        }
        return failure;
    }

    private void requireContext(TaskStepContext context) {
        if (context == null || !spec.equals(context.getSpec())) {
            throw new IllegalArgumentException("Repair runner received another task specification");
        }
        if (!taskCheckpoint.equals(context.getCheckpoint())) {
            throw new IllegalStateException("Repair checkpoint diverged from controller state");
        }
    }

    private static RepairBackendAvailability availability(RepairBackend backend) {
        if (backend == null) return RepairBackendAvailability.unavailable("No repair backend is configured");
        try {
            RepairBackendAvailability value = backend.availability();
            return value == null ? RepairBackendAvailability.unavailable("Repair backend returned no availability")
                : value;
        } catch (RuntimeException failure) {
            return RepairBackendAvailability.unavailable("Repair backend availability failed: " + describe(failure));
        }
    }

    private static String appendCleanup(String detail, RuntimeException cleanup) {
        return cleanup == null ? detail : detail + "; cleanup failed: " + describe(cleanup);
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass()
            .getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }
}
