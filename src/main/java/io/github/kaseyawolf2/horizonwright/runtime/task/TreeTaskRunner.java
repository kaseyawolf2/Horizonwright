package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.SaplingReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.base.TreeActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.TreeDecision;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservation;
import io.github.kaseyawolf2.horizonwright.core.base.TreePlanner;
import io.github.kaseyawolf2.horizonwright.core.base.TreeWorkCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeBackend.ActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeBackend.ActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeBackend.ActionRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeBackend.ActionState;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeTaskCheckpointCodec.State;

/** Restart-safe one-tree-at-a-time runner with separately durable fell and replant frontiers. */
final class TreeTaskRunner implements TaskRunner {

    private final TaskSpec spec;
    private final FarmRuntimeAccess runtime;
    private final TreePlanner planner = new TreePlanner();
    private TaskCheckpoint checkpoint;
    private State pass;
    private TreeBackend activeBackend;
    private ActionHandle activeHandle;
    private ActionLease activeLease;
    private TreeObservation activeBefore;
    private SaplingReserveEvidence activeReserve;
    private TreeDecision activeDecision;

    TreeTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, FarmRuntimeAccess runtime) {
        if (checkpoint == null || runtime == null)
            throw new IllegalArgumentException("checkpoint and runtime are required");
        TreeTask.areaId(spec);
        TreeTask.minimumSaplingReserve(spec);
        this.spec = spec;
        this.runtime = runtime;
        this.checkpoint = checkpoint;
        this.pass = TreeTaskCheckpointCodec.decode(spec, checkpoint);
        if (pass != null && pass.isComplete())
            throw new IllegalArgumentException("completed tree pass cannot be resumed");
    }

    @Override
    public synchronized StepResult step(TaskStepContext context) {
        requireContext(context);
        if (context.isSuspensionRequested()) return suspend(context);
        if (!context.getActions()
            .isAuthoritative()) return failure(context, "Tree runner lost authoritative action epoch", false);
        if (runtime.isDryRun()) {
            cancelActive();
            return StepResult.blocked(
                context.getActionEpoch(),
                checkpoint,
                BlockedReason.missingRequirement(
                    "Dry-run mode prevents a tree pass from acquiring gameplay capabilities.",
                    spec.getId(),
                    "live tree execution",
                    "Disable dry-run, then resume this task."));
        }
        TreeBackend backend = runtime.getTreeBackend();
        FarmBackend.Availability availability = backend == null
            ? FarmBackend.Availability.unavailable("No ordinary-tree backend is configured")
            : backend.availability();
        if (backend == null || availability == null || !availability.isAvailable()) {
            cancelActive();
            return StepResult.blocked(
                context.getActionEpoch(),
                checkpoint,
                BlockedReason.missingRequirement(
                    availability == null ? "Tree backend returned no availability" : availability.getDiagnostic(),
                    spec.getId(),
                    "an installed, version-tested ordinary-tree backend",
                    "Enable the tree integration, then resume this task."));
        }
        if (activeHandle != null && activeBackend != backend)
            return failure(context, "Tree backend changed during an action", true);
        if (pass == null) return freeze(context, backend);
        if (activeHandle != null) return observeAction(context, backend);
        return plan(context, backend);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) throw new IllegalArgumentException("interruption is required");
        cancelActive();
    }

    private StepResult freeze(TaskStepContext context, TreeBackend backend) {
        TreeBackend.ScanRequest request = new TreeBackend.ScanRequest(
            spec.getId(),
            TreeTask.areaId(spec),
            context.getActionEpoch());
        try {
            TreeBackend.PassSnapshot snapshot = backend.scan(request);
            validateScan(request, snapshot);
            long revision = nextRevision();
            pass = new State(snapshot.getArea(), revision, snapshot.getObservations(), 0, 0, null);
            checkpoint = TreeTaskCheckpointCodec.encode(spec, pass, revision);
            return pass.isComplete() ? StepResult
                .completed(context.getActionEpoch(), checkpoint, "Tree pass completed; no standing trees were found")
                : StepResult.progress(
                    context.getActionEpoch(),
                    checkpoint,
                    "Frozen " + pass.trees.size() + " bounded tree observation(s) for this pass");
        } catch (RuntimeException failure) {
            return StepResult
                .failed(context.getActionEpoch(), checkpoint, "Tree-farm scan failed: " + describe(failure), true);
        }
    }

    private StepResult plan(TaskStepContext context, TreeBackend backend) {
        TreeObservation frozen = pass.trees.get(pass.nextIndex);
        TreeWorkCheckpoint work = pass.work;
        if (work == null) {
            work = TreeWorkCheckpoint.start(pass.area, pass.passRevision, frozen);
            pass = pass.withWork(work);
            checkpoint = TreeTaskCheckpointCodec.encode(spec, pass, nextRevision());
        }
        TreeBackend.TargetRequest request = new TreeBackend.TargetRequest(
            spec.getId(),
            pass.passRevision,
            context.getActionEpoch(),
            pass.nextIndex,
            work,
            TreeTask.minimumSaplingReserve(spec));
        try {
            TreeBackend.TargetSnapshot snapshot = backend.observe(request);
            validateTarget(request, snapshot);
            TreeObservation current = snapshot.getObservation();
            TreeDecision decision = planner.plan(pass.area, work, current, snapshot.getReserveEvidence());
            if (decision.getAction() == TreeActionKind.HOLD_SAPLING_RESERVE) {
                return StepResult.blocked(
                    context.getActionEpoch(),
                    checkpoint,
                    BlockedReason.missingRequirement(
                        decision.getDetail(),
                        spec.getId(),
                        "a verified matching sapling above the configured reserve",
                        "Add the required sapling, then resume this task."));
            }
            if (!decision.requiresMutation()) {
                pass = pass.advance(false);
                return persist(context, "Verified non-mutating tree decision " + decision.getAction());
            }
            return submit(context, backend, snapshot, decision);
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                checkpoint,
                "Tree target observation failed: " + describe(failure),
                false);
        }
    }

    private StepResult submit(TaskStepContext context, TreeBackend backend, TreeBackend.TargetSnapshot snapshot,
        TreeDecision decision) {
        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(capabilities(decision.getAction()));
        if (!acquired.isPresent())
            return StepResult.waitFor(context.getActionEpoch(), checkpoint, 0L, "waiting for tree action capabilities");
        ActionLease lease = acquired.get();
        ActionRequest request = new ActionRequest(
            spec.getId() + "-tree-"
                + pass.passRevision
                + "-"
                + pass.nextIndex
                + "-"
                + decision.getWorkStage()
                    .name()
                    .toLowerCase(),
            spec.getId(),
            context.getActionEpoch(),
            decision);
        try {
            if (!lease.isValid() || runtime.getTreeBackend() != backend)
                throw new IllegalStateException("tree authority changed");
            ActionHandle handle = backend.execute(request, lease);
            if (handle == null || !request.getRequestId()
                .equals(handle.getRequestId())) {
                throw new IllegalStateException("tree backend returned a mismatched action handle");
            }
            activeBackend = backend;
            activeHandle = handle;
            activeLease = lease;
            activeBefore = snapshot.getObservation();
            activeReserve = snapshot.getReserveEvidence();
            activeDecision = decision;
            return StepResult.progress(context.getActionEpoch(), checkpoint, "Submitted " + decision.getAction());
        } catch (RuntimeException failure) {
            lease.close();
            return StepResult.failed(
                context.getActionEpoch(),
                checkpoint,
                "Tree action submission failed: " + describe(failure),
                true);
        }
    }

    private StepResult observeAction(TaskStepContext context, TreeBackend backend) {
        if (activeLease == null || !activeLease.isValid()
            || activeLease.getEpoch() != context.getActionEpoch()
            || activeBackend != backend
            || runtime.getTreeBackend() != backend) {
            return failure(context, "Tree action authority changed before confirmation", false);
        }
        try {
            ActionProgress progress = activeHandle.progress();
            if (progress == null || !activeHandle.getRequestId()
                .equals(progress.getRequestId())) {
                throw new IllegalStateException("tree progress belongs to another request");
            }
            if (progress.getState() == ActionState.SUBMITTED || progress.getState() == ActionState.EXECUTING) {
                return StepResult.waitFor(context.getActionEpoch(), checkpoint, 0L, progress.getDetail());
            }
            if (progress.getState() == ActionState.CONFIRMED) {
                TreeObservation after = progress.getConfirmedAfter()
                    .orElseThrow(() -> new IllegalStateException("confirmed tree action omitted its observation"));
                TreeWorkCheckpoint advanced = pass.work.advance(activeDecision, activeBefore, after, activeReserve);
                boolean completedTree = advanced.isComplete();
                pass = completedTree ? pass.advance(true) : pass.withWork(advanced);
                releaseActive();
                return persist(context, progress.getDetail());
            }
            return failure(context, progress.getDetail(), progress.getState() == ActionState.FAILED);
        } catch (RuntimeException failure) {
            return failure(context, "Tree action confirmation failed: " + describe(failure), false);
        }
    }

    private StepResult persist(TaskStepContext context, String detail) {
        checkpoint = TreeTaskCheckpointCodec.encode(spec, pass, nextRevision());
        return pass.isComplete()
            ? StepResult.completed(
                context.getActionEpoch(),
                checkpoint,
                "Tree pass completed with " + pass.verifiedTrees + " verified fell-and-replant cycle(s)")
            : StepResult.progress(context.getActionEpoch(), checkpoint, detail);
    }

    private StepResult suspend(TaskStepContext context) {
        cancelActive();
        return StepResult.safeSuspension(
            context.getActionEpoch(),
            checkpoint,
            "Tree pass stopped before advancing its current verified frontier");
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
        ActionHandle handle = activeHandle;
        ActionLease lease = activeLease;
        clearActive();
        if (handle != null) handle.cancel();
        if (lease != null) lease.close();
    }

    private void clearActive() {
        activeBackend = null;
        activeHandle = null;
        activeLease = null;
        activeBefore = null;
        activeReserve = null;
        activeDecision = null;
    }

    private void requireContext(TaskStepContext context) {
        if (context == null || !spec.equals(context.getSpec()))
            throw new IllegalArgumentException("tree task context mismatched");
        if (!checkpoint.equals(context.getCheckpoint())) throw new IllegalStateException("tree checkpoint diverged");
    }

    private long nextRevision() {
        if (checkpoint.getRevision() == Long.MAX_VALUE) throw new IllegalStateException("tree checkpoint exhausted");
        return checkpoint.getRevision() + 1L;
    }

    private static void validateScan(TreeBackend.ScanRequest request, TreeBackend.PassSnapshot snapshot) {
        if (snapshot == null || !request.getTaskId()
            .equals(snapshot.getTaskId())
            || request.getActionEpoch() != snapshot.getActionEpoch()
            || !request.getAreaId()
                .equals(
                    snapshot.getArea()
                        .getId())) {
            throw new IllegalStateException("tree scan returned stale or mismatched evidence");
        }
        Set<String> ids = new HashSet<>();
        for (TreeObservation tree : snapshot.getObservations()) {
            if (!ids.add(tree.getTreeId()) || !snapshot.getArea()
                .contains(tree.getReplantPosition())) {
                throw new IllegalStateException("tree scan contains a duplicate or outside root");
            }
            for (BasePosition block : tree.getTreeBlocks()) {
                if (!snapshot.getArea()
                    .contains(block)) throw new IllegalStateException("tree scan crossed the named boundary");
            }
        }
    }

    private static void validateTarget(TreeBackend.TargetRequest request, TreeBackend.TargetSnapshot snapshot) {
        if (snapshot == null || !request.getTaskId()
            .equals(snapshot.getTaskId())
            || request.getPassRevision() != snapshot.getPassRevision()
            || request.getActionEpoch() != snapshot.getActionEpoch()
            || request.getIndex() != snapshot.getIndex()
            || !request.getWork()
                .getTreeId()
                .equals(
                    snapshot.getObservation()
                        .getTreeId())) {
            throw new IllegalStateException("tree target returned stale or mismatched evidence");
        }
    }

    private static Set<ActionCapability> capabilities(TreeActionKind action) {
        if (action == TreeActionKind.FELL_CAPTURED_BLOCKS) {
            return Collections.unmodifiableSet(
                EnumSet.of(
                    ActionCapability.MOVEMENT,
                    ActionCapability.LOOK,
                    ActionCapability.DIG,
                    ActionCapability.PLACE,
                    ActionCapability.HELD_USE));
        }
        if (action == TreeActionKind.PLANT_SAPLING) {
            return Collections.unmodifiableSet(
                EnumSet.of(
                    ActionCapability.MOVEMENT,
                    ActionCapability.LOOK,
                    ActionCapability.PLACE,
                    ActionCapability.HELD_USE));
        }
        throw new IllegalArgumentException("tree decision does not require an action: " + action);
    }

    private static String describe(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }
}
