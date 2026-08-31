package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlan;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlanner;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadTransactionPlanner;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

/** Durable two-phase runner for reservation-safe, server-confirmed unloading. */
final class UnloadTaskRunner implements TaskRunner {

    private static final Set<ActionCapability> REQUIRED_CAPABILITIES = Collections
        .unmodifiableSet(EnumSet.of(ActionCapability.CONTAINER));

    private final TaskSpec spec;
    private final UnloadRuntimeAccess runtime;
    private TaskCheckpoint taskCheckpoint;
    private UnloadTaskCheckpoint state;
    private boolean restoredUncertainPhase;
    private UnloadBackend activeBackend;
    private UnloadActionRequest activeRequest;
    private UnloadActionHandle activeHandle;
    private ActionLease activeLease;

    UnloadTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, UnloadRuntimeAccess runtime) {
        if (spec == null || checkpoint == null || runtime == null) {
            throw new IllegalArgumentException("spec, checkpoint, and runtime are required");
        }
        UnloadTask.loadoutId(spec);
        UnloadTask.storageId(spec);
        this.spec = spec;
        this.runtime = runtime;
        taskCheckpoint = checkpoint;
        state = UnloadTaskCheckpointCodec.decode(spec, checkpoint);
        restoredUncertainPhase = state.getPhase() != UnloadTaskCheckpoint.Phase.READY;
    }

    @Override
    public synchronized StepResult step(TaskStepContext context) {
        requireContext(context);
        if (context.isSuspensionRequested()) {
            return suspend(context);
        }
        if (!context.getActions()
            .isAuthoritative()) {
            return failed(context, "Unload runner no longer owns the authoritative action epoch", stopActive(), false);
        }
        if (runtime.isDryRun()) {
            return blocked(
                context,
                "Dry-run mode prevents container mutation.",
                "live container execution",
                "Disable dry-run, then resume this unload task.");
        }
        UnloadBackend backend = runtime.getUnloadBackend();
        UnloadBackendAvailability availability = availability(backend);
        if (backend == null || !availability.isAvailable()) {
            return blocked(
                context,
                availability.getDiagnostic(),
                "an open, configured, version-tested storage container",
                "Open the configured storage container or repair its adapter, then resume this task.");
        }
        if (activeHandle != null) {
            if (activeBackend != backend) {
                return failed(context, "Unload backend changed during a transaction", stopActive(), false);
            }
            return observeAction(context);
        }
        if (restoredUncertainPhase) {
            return reconcileRestored(context, backend);
        }
        if (state.getPhase() == UnloadTaskCheckpoint.Phase.READY) {
            return prepare(context, backend);
        }
        if (state.getPhase() == UnloadTaskCheckpoint.Phase.PREPARED) {
            return executePrepared(context, backend);
        }
        return failed(
            context,
            "Awaiting unload checkpoint has no live action and was not marked for reconciliation",
            null,
            false);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) {
            throw new IllegalArgumentException("interruption must not be null");
        }
        RuntimeException failure = stopActive();
        restoredUncertainPhase = state.getPhase() != UnloadTaskCheckpoint.Phase.READY;
        if (failure != null) {
            throw failure;
        }
    }

    private StepResult prepare(TaskStepContext context, UnloadBackend backend) {
        BuiltObservation built;
        try {
            built = observe(context, backend, spec.getId() + "-unload-" + (state.getRevision() + 1L));
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Unload observation or planning failed: " + describe(failure),
                true);
        }
        if (!built.plan.mayStartTransaction()) {
            return blocked(
                context,
                "Named loadout is incomplete: " + built.plan.getMissingCounts(),
                "all reserved loadout items",
                "Restore the missing reserved items before unloading.");
        }
        if (built.plan.getUnloadableSlots()
            .isEmpty()) {
            return StepResult.completed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Unload complete; " + built.plan.getDeferredSlots()
                    .size() + " destination-filtered stacks retained");
        }
        String fingerprint = ContainerTransactionFingerprint.fingerprint(built.transaction);
        state = new UnloadTaskCheckpoint(
            state.getRevision() + 1L,
            UnloadTaskCheckpoint.Phase.PREPARED,
            state.getCompletedTransactions(),
            built.transaction.getTransactionId(),
            fingerprint);
        updateCheckpoint();
        return StepResult.progress(
            context.getActionEpoch(),
            taskCheckpoint,
            "Persisted exact unload transaction " + built.transaction.getTransactionId() + " before execution");
    }

    private StepResult executePrepared(TaskStepContext context, UnloadBackend backend) {
        BuiltObservation built;
        try {
            built = observe(context, backend, state.getTransactionId());
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Prepared unload revalidation failed: " + describe(failure),
                true);
        }
        if (built.transaction == null) {
            clearToReady(false);
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Prepared unload is no longer needed; reconciled current container state without clicking");
        }
        String fingerprint = ContainerTransactionFingerprint.fingerprint(built.transaction);
        if (!state.getTransactionFingerprint()
            .equals(fingerprint)) {
            clearToReady(false);
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Container changed after preparation; discarded the stale plan without clicking");
        }
        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(REQUIRED_CAPABILITIES);
        if (!acquired.isPresent()) {
            return StepResult.waitFor(context.getActionEpoch(), taskCheckpoint, 0L, "waiting for CONTAINER capability");
        }
        ActionLease lease = acquired.get();
        String requestId = state.getTransactionId() + "-request";
        UnloadActionRequest request = new UnloadActionRequest(
            requestId,
            state.getRevision(),
            context.getActionEpoch(),
            fingerprint,
            built.transaction);
        UnloadActionHandle handle = null;
        try {
            if (!lease.isValid() || lease.getEpoch() != context.getActionEpoch()) {
                throw new IllegalStateException("container authority changed before unload submission");
            }
            handle = backend.execute(request, lease);
            if (handle == null || !requestId.equals(handle.getRequestId())) {
                throw new IllegalStateException("unload backend returned a missing or mismatched handle");
            }
            activeBackend = backend;
            activeRequest = request;
            activeHandle = handle;
            activeLease = lease;
            state = new UnloadTaskCheckpoint(
                state.getRevision() + 1L,
                UnloadTaskCheckpoint.Phase.AWAITING_CONFIRMATION,
                state.getCompletedTransactions(),
                state.getTransactionId(),
                state.getTransactionFingerprint());
            updateCheckpoint();
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Submitted one persisted, server-confirmed unload transaction");
        } catch (RuntimeException failure) {
            RuntimeException cleanup = cancelAndClose(handle, lease);
            if (cleanup != null) failure.addSuppressed(cleanup);
            restoredUncertainPhase = true;
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Unload submission failed: " + describe(failure),
                false);
        }
    }

    private StepResult observeAction(TaskStepContext context) {
        if (activeLease == null || !activeLease.isValid() || activeLease.getEpoch() != context.getActionEpoch()) {
            return failed(context, "Unload action lease is no longer authoritative", stopActive(), false);
        }
        UnloadActionProgress progress;
        try {
            progress = activeHandle.progress();
            if (progress == null || !activeRequest.getRequestId()
                .equals(progress.getRequestId())) {
                throw new IllegalStateException("unload backend returned stale or missing progress");
            }
        } catch (RuntimeException failure) {
            return failed(context, "Unload progress failed: " + describe(failure), stopActive(), false);
        }
        switch (progress.getState()) {
            case SUBMITTED:
            case EXECUTING:
                return StepResult.waitFor(context.getActionEpoch(), taskCheckpoint, 0L, progress.getDetail());
            case CONFIRMED:
                if (activeRequest.getTransaction()
                    .getState() != ContainerTransactionState.COMPLETED) {
                    return failed(
                        context,
                        "Backend claimed confirmation before the exact transaction completed",
                        stopActive(),
                        false);
                }
                RuntimeException closeFailure = releaseConfirmed();
                if (closeFailure != null) {
                    return failed(context, "Confirmed unload lease cleanup failed", closeFailure, false);
                }
                clearToReady(true);
                return StepResult.progress(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Verified unload transaction completed; re-observing remaining stacks");
            case REJECTED:
            case FAILED:
                RuntimeException stopFailure = stopActive();
                restoredUncertainPhase = true;
                return blocked(
                    context,
                    appendCleanup(
                        "Unload transaction " + progress.getState() + ": " + progress.getDetail(),
                        stopFailure),
                    "operator acknowledgement after a non-idempotent transaction failure",
                    "Inspect the open container, then resume to reconcile server state without replay.");
            default:
                return failed(context, "Unknown unload progress state", stopActive(), false);
        }
    }

    private StepResult reconcileRestored(TaskStepContext context, UnloadBackend backend) {
        try {
            observe(context, backend, spec.getId() + "-reconcile-" + (state.getRevision() + 1L));
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Uncertain unload reconciliation failed: " + describe(failure),
                true);
        }
        clearToReady(false);
        restoredUncertainPhase = false;
        return StepResult.progress(
            context.getActionEpoch(),
            taskCheckpoint,
            "Reconciled current server container state without replaying the prior click");
    }

    private BuiltObservation observe(TaskStepContext context, UnloadBackend backend, String transactionId) {
        UnloadObservationRequest request = new UnloadObservationRequest(
            spec.getId(),
            state.getRevision(),
            context.getActionEpoch(),
            UnloadTask.loadoutId(spec),
            UnloadTask.storageId(spec));
        UnloadObservationResult observed = backend.observe(request);
        if (observed == null || !request.getTaskId()
            .equals(observed.getTaskId())
            || request.getCheckpointRevision() != observed.getCheckpointRevision()
            || request.getActionEpoch() != observed.getActionEpoch()
            || !request.getStorageId()
                .equals(observed.getStorageId())
            || !request.getLoadoutId()
                .equals(
                    observed.getLoadout()
                        .getId())) {
            throw new IllegalStateException("unload backend returned stale or mismatched evidence");
        }
        UnloadPlan plan = UnloadPlanner
            .plan(observed.getLoadout(), observed.getPlayerSlots(), observed.getDestinationFilter());
        ContainerTransaction transaction = null;
        if (plan.mayStartTransaction() && !plan.getUnloadableSlots()
            .isEmpty()) {
            transaction = UnloadTransactionPlanner.create(
                transactionId,
                context.getActionEpoch(),
                plan,
                observed.getPlayerSlots(),
                observed.getPredictions());
        }
        return new BuiltObservation(plan, transaction);
    }

    private StepResult suspend(TaskStepContext context) {
        RuntimeException failure = stopActive();
        restoredUncertainPhase = state.getPhase() != UnloadTaskCheckpoint.Phase.READY;
        if (failure != null) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Unload could not stop safely: " + describe(failure),
                false);
        }
        return StepResult.safeSuspension(
            context.getActionEpoch(),
            taskCheckpoint,
            "Unload stopped without replaying any outstanding click");
    }

    private void clearToReady(boolean incrementCompleted) {
        state = new UnloadTaskCheckpoint(
            state.getRevision() + 1L,
            UnloadTaskCheckpoint.Phase.READY,
            state.getCompletedTransactions() + (incrementCompleted ? 1 : 0),
            null,
            null);
        updateCheckpoint();
    }

    private void updateCheckpoint() {
        taskCheckpoint = UnloadTaskCheckpointCodec.encode(spec, state);
    }

    private StepResult blocked(TaskStepContext context, String detail, String requirement, String action) {
        return StepResult.blocked(
            context.getActionEpoch(),
            taskCheckpoint,
            BlockedReason.missingRequirement(detail, UnloadTask.storageId(spec), requirement, action));
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
        UnloadActionHandle handle = activeHandle;
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

    private static RuntimeException cancelAndClose(UnloadActionHandle handle, ActionLease lease) {
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
            throw new IllegalArgumentException("Unload runner received another task specification");
        }
        if (!taskCheckpoint.equals(context.getCheckpoint())) {
            throw new IllegalStateException("Unload checkpoint diverged from controller state");
        }
    }

    private static UnloadBackendAvailability availability(UnloadBackend backend) {
        if (backend == null) return UnloadBackendAvailability.unavailable("No unload backend is configured");
        try {
            UnloadBackendAvailability result = backend.availability();
            return result == null ? UnloadBackendAvailability.unavailable("Unload backend returned no availability")
                : result;
        } catch (RuntimeException failure) {
            return UnloadBackendAvailability.unavailable("Unload backend availability failed: " + describe(failure));
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

    private static final class BuiltObservation {

        private final UnloadPlan plan;
        private final ContainerTransaction transaction;

        private BuiltObservation(UnloadPlan plan, ContainerTransaction transaction) {
            this.plan = plan;
            this.transaction = transaction;
        }
    }
}
