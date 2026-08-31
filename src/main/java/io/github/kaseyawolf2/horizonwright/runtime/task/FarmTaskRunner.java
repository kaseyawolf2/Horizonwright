package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.FarmDecision;
import io.github.kaseyawolf2.horizonwright.core.base.FarmPassCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.base.FarmPlanner;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend.ActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend.ActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend.ActionRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend.ActionState;

/** Restart-safe one-target-at-a-time finite farm pass runner. */
final class FarmTaskRunner implements TaskRunner {

    private final TaskSpec spec;
    private final FarmRuntimeAccess runtime;
    private final FarmPlanner planner = new FarmPlanner();
    private TaskCheckpoint taskCheckpoint;
    private FarmPassCheckpoint pass;
    private List<CropObservation> frozenObservations;
    private FarmBackend activeBackend;
    private ActionRequest activeRequest;
    private ActionHandle activeHandle;
    private ActionLease activeLease;
    private CropObservation activeBefore;
    private SeedReserveEvidence activeReserve;
    private FarmDecision activeDecision;

    FarmTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, FarmRuntimeAccess runtime) {
        if (checkpoint == null || runtime == null)
            throw new IllegalArgumentException("checkpoint and runtime are required");
        FarmTask.plotId(spec);
        FarmTask.minimumSeedReserve(spec);
        this.spec = spec;
        this.runtime = runtime;
        this.taskCheckpoint = checkpoint;
        this.pass = FarmTaskCheckpointCodec.decode(spec, checkpoint);
        this.frozenObservations = FarmTaskCheckpointCodec.observations(checkpoint);
        if (pass != null && pass.isComplete())
            throw new IllegalArgumentException("completed farm pass cannot be resumed");
    }

    @Override
    public synchronized StepResult step(TaskStepContext context) {
        requireContext(context);
        if (context.isSuspensionRequested()) return suspend(context);
        if (!context.getActions()
            .isAuthoritative()) {
            return failure(context, "Farm runner no longer owns the authoritative action epoch", false);
        }
        if (runtime.isDryRun()) {
            cancelActive();
            return StepResult.blocked(
                context.getActionEpoch(),
                taskCheckpoint,
                BlockedReason.missingRequirement(
                    "Dry-run mode prevents a farm pass from acquiring gameplay capabilities.",
                    spec.getId(),
                    "live farm execution",
                    "Disable dry-run, then resume this task."));
        }
        FarmBackend backend = runtime.getFarmBackend();
        FarmBackend.Availability availability = availability(backend);
        if (backend == null || !availability.isAvailable()) {
            cancelActive();
            return StepResult.blocked(
                context.getActionEpoch(),
                taskCheckpoint,
                BlockedReason.missingRequirement(
                    availability.getDiagnostic(),
                    spec.getId(),
                    "an installed, version-tested farm backend",
                    "Enable the farm integration, then resume this task."));
        }
        if (activeHandle != null && activeBackend != backend) {
            return failure(context, "Farm backend changed while an action was active", true);
        }
        if (pass == null) return freezePass(context, backend);
        if (activeHandle != null) return observeAction(context, backend);
        return planTarget(context, backend);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) throw new IllegalArgumentException("interruption is required");
        cancelActive();
    }

    private StepResult freezePass(TaskStepContext context, FarmBackend backend) {
        FarmBackend.ScanRequest request = new FarmBackend.ScanRequest(
            spec.getId(),
            FarmTask.plotId(spec),
            context.getActionEpoch());
        try {
            FarmBackend.PassSnapshot snapshot = backend.scan(request);
            validateScan(request, snapshot);
            long revision = nextRevision();
            pass = FarmPassCheckpoint.start(snapshot.getPlot(), revision, snapshot.getObservations());
            frozenObservations = snapshot.getObservations();
            taskCheckpoint = FarmTaskCheckpointCodec.encode(spec, pass, frozenObservations, revision);
            return pass.isComplete()
                ? StepResult
                    .completed(context.getActionEpoch(), taskCheckpoint, "Farm pass completed; the plot was empty")
                : StepResult.progress(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Frozen " + pass.getObservationCount() + " crop observation(s) for this finite pass");
        } catch (RuntimeException failure) {
            return StepResult
                .failed(context.getActionEpoch(), taskCheckpoint, "Farm plot scan failed: " + describe(failure), true);
        }
    }

    private StepResult planTarget(TaskStepContext context, FarmBackend backend) {
        BasePosition expected = pass.getObservationTargets()
            .get(pass.getNextObservationIndex());
        FarmBackend.TargetRequest request = new FarmBackend.TargetRequest(
            spec.getId(),
            pass.getPassRevision(),
            context.getActionEpoch(),
            pass.getNextObservationIndex(),
            expected,
            FarmTask.minimumSeedReserve(spec));
        try {
            FarmBackend.TargetSnapshot snapshot = backend.observe(request);
            validateTarget(request, snapshot);
            FarmDecision decision = planner
                .plan(pass.getPlot(), pass, snapshot.getObservation(), snapshot.getReserveEvidence());
            if (decision.getAction() == FarmActionKind.HOLD_FOR_ADAPTER
                || decision.getAction() == FarmActionKind.HOLD_REPLANT_RESERVE) {
                return StepResult.blocked(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    BlockedReason.missingRequirement(
                        decision.getDetail(),
                        spec.getId(),
                        decision.getAction() == FarmActionKind.HOLD_FOR_ADAPTER ? "a tested crop adapter"
                            : "verified seed inventory above the configured reserve",
                        "Resolve the requirement, then submit a fresh farm pass."));
            }
            if (!decision.requiresMutation()) {
                pass = pass.advance(
                    decision,
                    snapshot.getObservation(),
                    snapshot.getObservation(),
                    snapshot.getReserveEvidence());
                return persistAdvance(context, "Verified non-mutating farm decision " + decision.getAction());
            }
            return submitAction(context, backend, snapshot, decision);
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Farm target observation failed: " + describe(failure),
                false);
        }
    }

    private StepResult submitAction(TaskStepContext context, FarmBackend backend, FarmBackend.TargetSnapshot snapshot,
        FarmDecision decision) {
        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(capabilities(decision.getAction()));
        if (!acquired.isPresent()) {
            return StepResult
                .waitFor(context.getActionEpoch(), taskCheckpoint, 0L, "waiting for farm action capabilities");
        }
        ActionLease lease = acquired.get();
        ActionRequest request = new ActionRequest(
            spec.getId() + "-farm-" + pass.getPassRevision() + "-" + pass.getNextObservationIndex(),
            spec.getId(),
            pass.getPassRevision(),
            context.getActionEpoch(),
            pass.getNextObservationIndex(),
            decision);
        try {
            if (!lease.isValid() || runtime.getFarmBackend() != backend)
                throw new IllegalStateException("farm authority changed");
            ActionHandle handle = backend.execute(request, lease);
            if (handle == null || !request.getRequestId()
                .equals(handle.getRequestId())) {
                throw new IllegalStateException("farm backend returned a mismatched action handle");
            }
            activeBackend = backend;
            activeRequest = request;
            activeHandle = handle;
            activeLease = lease;
            activeBefore = snapshot.getObservation();
            activeReserve = snapshot.getReserveEvidence();
            activeDecision = decision;
            return StepResult.progress(context.getActionEpoch(), taskCheckpoint, "Submitted " + decision.getAction());
        } catch (RuntimeException failure) {
            try {
                lease.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Farm action submission failed: " + describe(failure),
                true);
        }
    }

    private StepResult observeAction(TaskStepContext context, FarmBackend backend) {
        if (activeLease == null || !activeLease.isValid()
            || activeLease.getEpoch() != context.getActionEpoch()
            || activeBackend != backend
            || runtime.getFarmBackend() != backend) {
            return failure(context, "Farm action authority changed before confirmation", false);
        }
        try {
            ActionProgress progress = activeHandle.progress();
            if (progress == null || !activeRequest.getRequestId()
                .equals(progress.getRequestId())) {
                throw new IllegalStateException("farm action progress belongs to another request");
            }
            if (progress.getState() == ActionState.SUBMITTED || progress.getState() == ActionState.EXECUTING) {
                return StepResult.waitFor(context.getActionEpoch(), taskCheckpoint, 0L, progress.getDetail());
            }
            if (progress.getState() == ActionState.CONFIRMED) {
                CropObservation after = progress.getConfirmedAfter()
                    .orElseThrow(() -> new IllegalStateException("confirmed farm action omitted its observation"));
                pass = pass.advance(activeDecision, activeBefore, after, activeReserve);
                releaseActive();
                return persistAdvance(context, progress.getDetail());
            }
            return failure(context, progress.getDetail(), progress.getState() == ActionState.FAILED);
        } catch (RuntimeException failure) {
            return failure(context, "Farm action confirmation failed: " + describe(failure), false);
        }
    }

    private StepResult persistAdvance(TaskStepContext context, String detail) {
        taskCheckpoint = FarmTaskCheckpointCodec.encode(spec, pass, frozenObservations, nextRevision());
        return pass.isComplete()
            ? StepResult.completed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Farm pass completed with " + pass.getVerifiedMutations() + " verified mutation(s)")
            : StepResult.progress(context.getActionEpoch(), taskCheckpoint, detail);
    }

    private StepResult suspend(TaskStepContext context) {
        cancelActive();
        return StepResult.safeSuspension(
            context.getActionEpoch(),
            taskCheckpoint,
            "Farm pass stopped before advancing its current crop");
    }

    private StepResult failure(TaskStepContext context, String detail, boolean retryable) {
        cancelActive();
        return StepResult.failed(context.getActionEpoch(), taskCheckpoint, detail, retryable);
    }

    private void releaseActive() {
        ActionLease lease = activeLease;
        clearActive();
        if (lease != null) lease.close();
    }

    private void cancelActive() {
        ActionHandle handle = activeHandle;
        ActionLease lease = activeLease;
        clearActive();
        if (handle != null) handle.cancel();
        if (lease != null) lease.close();
    }

    private void clearActive() {
        activeBackend = null;
        activeRequest = null;
        activeHandle = null;
        activeLease = null;
        activeBefore = null;
        activeReserve = null;
        activeDecision = null;
    }

    private void requireContext(TaskStepContext context) {
        if (context == null || !spec.equals(context.getSpec()))
            throw new IllegalArgumentException("farm task context mismatched");
        if (!taskCheckpoint.equals(context.getCheckpoint()))
            throw new IllegalStateException("farm checkpoint diverged");
    }

    private long nextRevision() {
        if (taskCheckpoint.getRevision() == Long.MAX_VALUE)
            throw new IllegalStateException("farm checkpoint exhausted");
        return taskCheckpoint.getRevision() + 1L;
    }

    private static FarmBackend.Availability availability(FarmBackend backend) {
        if (backend == null) return FarmBackend.Availability.unavailable("No farm backend is configured");
        FarmBackend.Availability value = backend.availability();
        return value == null ? FarmBackend.Availability.unavailable("Farm backend returned no availability") : value;
    }

    private static void validateScan(FarmBackend.ScanRequest request, FarmBackend.PassSnapshot snapshot) {
        if (snapshot == null || !request.getTaskId()
            .equals(snapshot.getTaskId())
            || request.getActionEpoch() != snapshot.getActionEpoch()
            || !request.getPlotId()
                .equals(
                    snapshot.getPlot()
                        .getId())) {
            throw new IllegalStateException("farm scan returned stale or mismatched evidence");
        }
        Set<BasePosition> positions = new HashSet<>();
        for (CropObservation observation : snapshot.getObservations()) {
            if (!snapshot.getPlot()
                .contains(observation.getPosition()) || !positions.add(observation.getPosition())) {
                throw new IllegalStateException("farm scan contains an outside or duplicate crop position");
            }
        }
    }

    private static void validateTarget(FarmBackend.TargetRequest request, FarmBackend.TargetSnapshot snapshot) {
        if (snapshot == null || !request.getTaskId()
            .equals(snapshot.getTaskId())
            || request.getPassRevision() != snapshot.getPassRevision()
            || request.getActionEpoch() != snapshot.getActionEpoch()
            || request.getObservationIndex() != snapshot.getObservationIndex()
            || !request.getPosition()
                .equals(
                    snapshot.getObservation()
                        .getPosition())) {
            throw new IllegalStateException("farm target returned stale or mismatched evidence");
        }
    }

    private static Set<ActionCapability> capabilities(FarmActionKind action) {
        if (action == FarmActionKind.BREAK_AND_REPLANT) {
            return Collections.unmodifiableSet(
                EnumSet.of(
                    ActionCapability.MOVEMENT,
                    ActionCapability.LOOK,
                    ActionCapability.DIG,
                    ActionCapability.PLACE));
        }
        if (action == FarmActionKind.RIGHT_CLICK_HARVEST) {
            return Collections
                .unmodifiableSet(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.USE));
        }
        throw new IllegalArgumentException("farm decision does not require an action: " + action);
    }

    private static String describe(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }
}
