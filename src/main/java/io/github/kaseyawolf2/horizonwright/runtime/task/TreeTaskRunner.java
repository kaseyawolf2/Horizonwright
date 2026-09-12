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

/** Restart-safe pass: all bounded felling, live drop collection, then deferred planting. */
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
    private TreeBackend.CollectionHandle collection;

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
    public synchronized boolean isInventoryPreparationSafe() {
        return activeHandle == null && activeLease == null && collection == null;
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
        if (TreeTask.plantSpecies(spec.getParameters()) < 0) {
            cancelActive();
            return StepResult.blocked(
                context.getActionEpoch(),
                checkpoint,
                BlockedReason.missingRequirement(
                    "This older tree task has no selected planting species for the grid.",
                    spec.getId(),
                    "configured sapling species and grid spacing",
                    "Choose species and spacing in Tree Farm setup and create a new pass."));
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
        if (pass.collecting()) return collect(context, backend);
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
            context.getActionEpoch(),
            TreeTask.plantSpecies(spec.getParameters()),
            TreeTask.plantSpacing(spec.getParameters()));
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
            work = pass.planting ? pass.pending.get(pass.nextIndex)
                : TreeWorkCheckpoint.start(pass.area, pass.passRevision, frozen);
            pass = pass.withWork(work);
            checkpoint = TreeTaskCheckpointCodec.encode(spec, pass, nextRevision());
        }
        if (!pass.planting
            && work.getStage() == io.github.kaseyawolf2.horizonwright.core.base.TreeWorkStage.READY_TO_REPLANT) {
            pass = pass.defer(work);
            return persist(context, "Deferred planting until all trees are felled and drops collected");
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
                pass = completedTree ? pass.advance(true) : pass.defer(advanced);
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

    private StepResult collect(TaskStepContext context, TreeBackend backend) {
        try {
            if (collection == null) {
                Optional<ActionLease> acquired = context.getActions()
                    .tryAcquire(
                        EnumSet.of(
                            ActionCapability.MOVEMENT,
                            ActionCapability.LOOK,
                            ActionCapability.DIG,
                            ActionCapability.HELD_USE));
                if (!acquired.isPresent()) return StepResult
                    .waitFor(context.getActionEpoch(), checkpoint, 0L, "Waiting for tree drop collection authority");
                activeLease = acquired.get();
                activeBackend = backend;
                collection = backend.collectDrops(spec.getId(), pass.area, activeLease);
            }
            if (activeBackend != backend || activeLease == null || !activeLease.isValid())
                return failure(context, "Tree collection authority changed", true);
            if (!collection.poll())
                return StepResult.waitFor(context.getActionEpoch(), checkpoint, 0L, collection.detail());
            collection.cancel();
            collection = null;
            releaseActive();
            int species = TreeTask.plantSpecies(spec.getParameters());
            if (species < 0) throw new IllegalStateException(
                "Choose the planting species and spacing in Tree Farm setup before running a grid replant pass");
            TreeBackend.ScanRequest gridRequest = new TreeBackend.ScanRequest(
                spec.getId(),
                TreeTask.areaId(spec),
                context.getActionEpoch(),
                species,
                TreeTask.plantSpacing(spec.getParameters()));
            TreeBackend.PassSnapshot grid = backend.plantingGrid(gridRequest);
            validateScan(gridRequest, grid);
            if (!sameTreeBounds(pass.area, grid.getArea()))
                throw new IllegalStateException("Tree area changed before grid planting");
            pass = pass.beginGridPlanting(grid.getObservations());
            return persist(context, "Tree drops collected; beginning deferred sapling planting");
        } catch (RuntimeException failure) {
            return failure(context, "Tree drop collection failed: " + describe(failure), true);
        }
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
        if (collection != null) {
            collection.cancel();
            collection = null;
        }
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
            if (!ids.add(tree.getTreeId()) || !io.github.kaseyawolf2.horizonwright.core.base.TreeHarvestBoundary
                .rootSelected(snapshot.getArea(), tree.getReplantPosition(), tree.getTreeId())) {
                throw new IllegalStateException("tree scan contains a duplicate or outside root");
            }
            for (BasePosition block : tree.getTreeBlocks()) {
                if (!io.github.kaseyawolf2.horizonwright.core.base.TreeHarvestBoundary
                    .logWithinReach(tree.getReplantPosition(), block))
                    throw new IllegalStateException("tree scan exceeded connected-tree discovery reach");
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
                    ActionCapability.HELD_USE,
                    ActionCapability.CONTAINER));
        }
        if (action == TreeActionKind.PLANT_SAPLING) {
            return Collections.unmodifiableSet(
                EnumSet.of(
                    ActionCapability.MOVEMENT,
                    ActionCapability.LOOK,
                    ActionCapability.PLACE,
                    ActionCapability.CONTAINER,
                    ActionCapability.HELD_USE));
        }
        throw new IllegalArgumentException("tree decision does not require an action: " + action);
    }

    private static String describe(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }

    static boolean sameTreeBounds(io.github.kaseyawolf2.horizonwright.core.base.NamedArea saved,
        io.github.kaseyawolf2.horizonwright.core.base.NamedArea live) {
        if (saved.isCircular() != live.isCircular()) return false;
        return saved.getId()
            .equals(live.getId())
            && saved.getMinimum()
                .equals(live.getMinimum())
            && saved.getMaximum()
                .equals(live.getMaximum());
    }
}
