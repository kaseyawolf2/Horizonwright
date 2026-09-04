package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationGeometry;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationExecutionResult;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
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
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetOutcome;
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
 * Non-mutating target prefixes are observed and checkpointed in bounded batches. A plan which performs gameplay
 * actions still contains exactly one target, and only an exact post-action confirmation accepted by
 * {@link ExcavationReducer} advances that target's frontier.
 */
final class ExcavationTaskRunner implements TaskRunner {

    private static final int TARGETS_PER_SCAN = CylinderExcavationGeometry.MAX_BATCH_SIZE;
    private static final int RANDOM_AUDIT_INTERVAL_ACTIONS = 64;
    private static final int RANDOM_AUDIT_SAMPLES = 16;
    private static final long POLL_DELAY_MILLIS = 0L;
    private static final Set<ActionCapability> REQUIRED_CAPABILITIES = Collections.unmodifiableSet(
        EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.DIG, ActionCapability.HELD_USE));

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
    private ExcavationCheckpoint completionCandidate;
    private ExcavationFrontier verificationFrontier;
    private Integer verificationLayerY;
    private boolean verificationChanged;
    private boolean activeVerification;
    private boolean randomVerification;
    private boolean randomAuditPending;
    private int primaryActionsSinceRandomAudit;
    private long randomAuditState;

    ExcavationTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, ExcavationRuntimeAccess runtime) {
        if (checkpoint == null || runtime == null) {
            throw new IllegalArgumentException("checkpoint and runtime must not be null");
        }
        this.spec = spec;
        this.cylinder = ExcavationTask.parse(spec);
        this.runtime = runtime;
        this.taskCheckpoint = checkpoint;
        this.excavationCheckpoint = ExcavationTaskCheckpointCodec.decode(cylinder, checkpoint);
        long restoredProgress = excavationCheckpoint == null ? 0L
            : excavationCheckpoint.getProgress()
                .getProcessed();
        this.randomAuditState = mixAuditSeed(
            cylinder.getGeometryKey()
                .hashCode() ^ restoredProgress);
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
        if (verificationFrontier != null) {
            return randomVerification ? verifyRandomAuditTarget(context, backend)
                : verifyCompletedVolume(context, backend);
        }
        if (randomAuditPending) return performRandomAudit(context, backend);
        return observeAndSubmit(context, backend);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) {
            throw new IllegalArgumentException("interruption must not be null");
        }
        RuntimeException failure = stopActive();
        clearVerification();
        restoredAuthorityNeedsRebind = true;
        if (failure != null) {
            throw failure;
        }
    }

    private StepResult suspend(TaskStepContext context) {
        RuntimeException stopFailure = stopActive();
        clearVerification();
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
            .nextBatch(cylinder, excavationCheckpoint.getFrontier(), TARGETS_PER_SCAN);
        if (batch.isEmpty()) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Non-complete excavation checkpoint produced no target",
                false);
        }
        ExcavationServicePolicy policy = ExcavationTask.servicePolicy(spec);
        ExcavationServiceRequirements requirements = serviceRequirements(policy);
        List<ExcavationObservation> passiveObservations = new ArrayList<>(
            batch.getTargets()
                .size());
        ExcavationObservationResult actionable = null;
        for (ExcavationTarget target : batch.getTargets()) {
            if (target.getPosition()
                .getY()
                != excavationCheckpoint.getFrontier()
                    .getLayerY()) {
                break;
            }
            ExcavationObservationRequest observationRequest = observationRequest(context, target, requirements);
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
            if (observed.getSuspensionReason() != ExcavationSuspensionReason.NONE || requiresGameplayAction(
                observed.getObservation()
                    .getClassification())) {
                actionable = observed;
                break;
            }
            passiveObservations.add(observed.getObservation());
        }

        if (!passiveObservations.isEmpty()) {
            return applyPassivePrefix(context, backend, passiveObservations);
        }
        if (actionable == null) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation scan produced neither passive progress nor an actionable target",
                false);
        }

        ExcavationTargetBatch actionBatch = CylinderExcavationGeometry
            .nextBatch(cylinder, excavationCheckpoint.getFrontier(), 1);

        ExcavationPlanningWindow window = new ExcavationPlanningWindow(
            excavationCheckpoint.getTaskRevision(),
            context.getActionEpoch(),
            actionBatch,
            Collections.singletonList(actionable.getObservation()));
        ExcavationPlan plan = ExcavationPlanner.calculate(cylinder, window, null);
        if (plan.getIntents()
            .size() != 1
            || !plan.getManagedIntents()
                .isEmpty()) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Clean-volume excavation planner violated the single-target contract",
                false);
        }
        if (actionable.getSuspensionReason() != ExcavationSuspensionReason.NONE) {
            return suspendForSharedOperation(context, plan, actionable);
        }

        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(REQUIRED_CAPABILITIES);
        if (!acquired.isPresent()) {
            return StepResult.waitFor(
                context.getActionEpoch(),
                taskCheckpoint,
                POLL_DELAY_MILLIS,
                "waiting for MOVEMENT, LOOK, DIG, and tool-selection capabilities");
        }
        ActionLease lease = acquired.get();
        ExcavationIntent intent = plan.getIntents()
            .get(0);
        String requestId = spec.getId() + "-excavate-" + plan.getTaskRevision();
        ExcavationActionRequest actionRequest = new ExcavationActionRequest(
            requestId,
            spec.getId(),
            cylinder.getDimensionId(),
            plan.getTaskRevision(),
            plan.getActionEpoch(),
            plan.getGeometryKey(),
            plan.getStartFrontier(),
            intent,
            cylinder,
            policy != null && policy.hasRepair() ? policy.getReservedToolSlot() : -1);
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

    private ExcavationObservationRequest observationRequest(TaskStepContext context, ExcavationTarget target,
        ExcavationServiceRequirements requirements) {
        return new ExcavationObservationRequest(
            spec.getId(),
            cylinder.getDimensionId(),
            excavationCheckpoint.getTaskRevision(),
            context.getActionEpoch(),
            cylinder.getGeometryKey(),
            excavationCheckpoint.getFrontier(),
            target.getPosition(),
            requirements);
    }

    private StepResult applyPassivePrefix(TaskStepContext context, ExcavationBackend backend,
        List<ExcavationObservation> observations) {
        ExcavationTargetBatch passiveBatch = CylinderExcavationGeometry
            .nextBatch(cylinder, excavationCheckpoint.getFrontier(), observations.size());
        ExcavationPlan passivePlan = ExcavationPlanner.calculate(
            cylinder,
            new ExcavationPlanningWindow(
                excavationCheckpoint.getTaskRevision(),
                context.getActionEpoch(),
                passiveBatch,
                observations),
            null);
        if (!passivePlan.getManagedIntents()
            .isEmpty()) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Clean-volume excavation produced managed intents while skipping passive targets",
                false);
        }
        List<ExcavationTargetResult> results = new ArrayList<>(
            passivePlan.getIntents()
                .size());
        try {
            for (ExcavationIntent intent : passivePlan.getIntents()) {
                results.add(passiveResult(intent));
            }
            if (!isLiveAuthority(context, backend)) {
                throw new IllegalStateException("excavation authority changed before passive progress was applied");
            }
            ExcavationResultApplication application = ExcavationReducer.apply(
                excavationCheckpoint,
                new ExcavationExecutionResult(passivePlan, results, ExcavationSuspensionReason.NONE));
            if (!application.wasApplied()) {
                throw new IllegalStateException(
                    "passive excavation progress was rejected as " + application.getDisposition());
            }
            return acceptPrimaryCheckpoint(
                context,
                application.getCheckpoint(),
                "Skipped " + results.size() + " confirmed non-mutating excavation targets");
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Passive excavation progress rejected: " + describe(failure),
                false);
        }
    }

    private static boolean requiresGameplayAction(ExcavationBlockClassification classification) {
        return classification == ExcavationBlockClassification.BREAKABLE
            || classification == ExcavationBlockClassification.FLUID_SOURCE_REACHABLE;
    }

    private static ExcavationTargetResult passiveResult(ExcavationIntent intent) {
        ExcavationTargetOutcome outcome;
        switch (intent.getKind()) {
            case ALREADY_CLEAR:
                outcome = ExcavationTargetOutcome.COMPLETED;
                break;
            case PROTECT_GRAVE:
            case PROTECT_INFRASTRUCTURE:
                outcome = ExcavationTargetOutcome.PROTECTED;
                break;
            case MARK_UNREACHABLE:
                outcome = ExcavationTargetOutcome.UNREACHABLE;
                break;
            case MARK_FAILED:
                outcome = ExcavationTargetOutcome.FAILED;
                break;
            default:
                throw new IllegalStateException("passive excavation prefix contained action " + intent.getKind());
        }
        return new ExcavationTargetResult(intent.getPosition(), outcome);
    }

    private static ExcavationServiceRequirements serviceRequirements(ExcavationServicePolicy policy) {
        if (policy == null) return ExcavationServiceRequirements.none();
        return ExcavationServiceRequirements.of(
            policy.hasUnload(),
            policy.hasRepair(),
            policy.hasRepair() ? policy.getReservedToolSlot() : 0,
            policy.hasRepair() ? policy.getPredictedWorkDamage() : 0);
    }

    private StepResult suspendForSharedOperation(TaskStepContext context, ExcavationPlan plan,
        ExcavationObservationResult observed) {
        ExcavationSuspensionReason reason = observed.getSuspensionReason();
        if (reason != ExcavationSuspensionReason.UNLOADING_REQUIRED
            && reason != ExcavationSuspensionReason.REPAIR_REQUIRED) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation backend requested unsupported shared operation " + reason,
                false);
        }
        ExcavationExecutionResult result = new ExcavationExecutionResult(
            plan,
            Collections.<ExcavationTargetResult>emptyList(),
            reason);
        ExcavationResultApplication application = ExcavationReducer.apply(excavationCheckpoint, result);
        if (!application.wasApplied()) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation shared-operation suspension was rejected as " + application.getDisposition(),
                false);
        }
        excavationCheckpoint = application.getCheckpoint();
        taskCheckpoint = ExcavationTaskCheckpointCodec.encode(cylinder, excavationCheckpoint);
        String requirement = reason == ExcavationSuspensionReason.UNLOADING_REQUIRED ? "verified unloading"
            : "verified Tinkers repair";
        int repairSlot = reason == ExcavationSuspensionReason.REPAIR_REQUIRED
            ? observed.getRepairToolSlot() >= 0 ? observed.getRepairToolSlot() : configuredRepairSlotOrDefault()
            : -1;
        String action = reason == ExcavationSuspensionReason.UNLOADING_REQUIRED
            ? "Complete the configured unload transaction, then resume this task."
            : "Open the configured Tool Station or Tool Forge. Horizonwright will repair the damaged Tinkers tool "
                + "from inventory slot "
                + repairSlot
                + " and resume excavation automatically.";
        return StepResult.blocked(
            context.getActionEpoch(),
            taskCheckpoint,
            BlockedReason.missingRequirement(
                "Excavation suspended at its exact frontier for " + requirement + ".",
                reason == ExcavationSuspensionReason.REPAIR_REQUIRED ? repairLocation(repairSlot) : spec.getId(),
                requirement,
                action));
    }

    static String repairLocation(int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot > 35) {
            throw new IllegalArgumentException("repair inventory slot must be from 0 to 35");
        }
        return "repair-tool-slot:" + inventorySlot;
    }

    private int configuredRepairSlotOrDefault() {
        ExcavationServicePolicy configured = ExcavationTask.servicePolicy(spec);
        return configured != null && configured.hasRepair() ? configured.getReservedToolSlot() : 0;
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
        if (activeVerification) {
            return applyVerificationConfirmation(context, backend, progress);
        }
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
            primaryActionsSinceRandomAudit++;
            if (primaryActionsSinceRandomAudit >= RANDOM_AUDIT_INTERVAL_ACTIONS) randomAuditPending = true;
            RuntimeException releaseFailure = releaseConfirmed();
            if (releaseFailure != null) {
                throw releaseFailure;
            }
            return acceptPrimaryCheckpoint(
                context,
                application.getCheckpoint(),
                "Confirmed excavation target " + targetResult.getPosition());
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

    private StepResult verifyCompletedVolume(TaskStepContext context, ExcavationBackend backend) {
        if (verificationBoundaryReached()) return finishOrRepeatVerification(context);
        ExcavationTargetBatch batch = CylinderExcavationGeometry
            .nextBatch(cylinder, verificationFrontier, TARGETS_PER_SCAN);
        if (batch.isEmpty()) {
            return finishOrRepeatVerification(context);
        }
        int verified = 0;
        for (ExcavationTarget target : batch.getTargets()) {
            if (verificationLayerY != null && target.getPosition()
                .getY() != verificationLayerY.intValue()) {
                break;
            }
            ExcavationObservationRequest request = new ExcavationObservationRequest(
                spec.getId(),
                cylinder.getDimensionId(),
                completionCandidate.getTaskRevision(),
                context.getActionEpoch(),
                cylinder.getGeometryKey(),
                verificationFrontier,
                target.getPosition(),
                ExcavationServiceRequirements.none());
            ExcavationObservationResult observed;
            try {
                observed = backend.observe(request);
                validateObservation(request, observed);
            } catch (RuntimeException failure) {
                return StepResult.failed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Excavation verification observation failed: " + describe(failure),
                    true);
            }
            if (!isLiveAuthority(context, backend)) {
                return StepResult.failed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Excavation authority changed during cleared-volume verification",
                    false);
            }
            ExcavationBlockClassification classification = observed.getObservation()
                .getClassification();
            if (requiresGameplayAction(classification)) {
                if (verified > 0) {
                    verificationFrontier = frontierAfter(batch, verified);
                    return StepResult.progress(
                        context.getActionEpoch(),
                        taskCheckpoint,
                        "Freshly verified " + verified + " previously processed excavation positions");
                }
                return submitVerificationAction(context, backend, observed);
            }
            verified++;
        }
        if (verified == 0) return finishOrRepeatVerification(context);
        verificationFrontier = frontierAfter(batch, verified);
        if (verificationBoundaryReached()) {
            return finishOrRepeatVerification(context);
        }
        return StepResult.progress(
            context.getActionEpoch(),
            taskCheckpoint,
            "Freshly verified " + verified + " previously processed excavation positions");
    }

    private ExcavationFrontier frontierAfter(ExcavationTargetBatch batch, int targetCount) {
        return batch.getTargets()
            .get(targetCount - 1)
            .getNextFrontier();
    }

    private StepResult submitVerificationAction(TaskStepContext context, ExcavationBackend backend,
        ExcavationObservationResult observed) {
        ExcavationTargetBatch actionBatch = CylinderExcavationGeometry.nextBatch(cylinder, verificationFrontier, 1);
        ExcavationPlan plan = ExcavationPlanner.calculate(
            cylinder,
            new ExcavationPlanningWindow(
                completionCandidate.getTaskRevision(),
                context.getActionEpoch(),
                actionBatch,
                Collections.singletonList(observed.getObservation())),
            null);
        if (plan.getIntents()
            .size() != 1
            || !plan.getManagedIntents()
                .isEmpty()) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Cleared-volume verification found an unsupported actionable target",
                false);
        }
        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(REQUIRED_CAPABILITIES);
        if (!acquired.isPresent()) {
            return StepResult.waitFor(
                context.getActionEpoch(),
                taskCheckpoint,
                POLL_DELAY_MILLIS,
                "waiting for excavation capabilities during cleared-volume verification");
        }
        ActionLease lease = acquired.get();
        ExcavationIntent intent = plan.getIntents()
            .get(0);
        long verificationIndex = CylinderExcavationGeometry.processedBefore(cylinder, verificationFrontier);
        String requestId = spec.getId() + "-verify-" + verificationIndex;
        ExcavationActionRequest actionRequest = new ExcavationActionRequest(
            requestId,
            spec.getId(),
            cylinder.getDimensionId(),
            plan.getTaskRevision(),
            plan.getActionEpoch(),
            plan.getGeometryKey(),
            plan.getStartFrontier(),
            intent,
            cylinder,
            -1);
        ExcavationActionHandle handle = null;
        try {
            handle = backend.execute(actionRequest, lease);
            if (handle == null || !requestId.equals(handle.getRequestId())) {
                throw new IllegalStateException("excavation backend returned a mismatched verification handle");
            }
            activeBackend = backend;
            activePlan = plan;
            activeRequest = actionRequest;
            activeHandle = handle;
            activeLease = lease;
            activeVerification = true;
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Rediscovered block in previously processed area at " + intent.getPosition());
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = cancelAndClose(handle, lease);
            if (cleanupFailure != null) failure.addSuppressed(cleanupFailure);
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation verification action failed: " + describe(failure),
                true);
        }
    }

    private StepResult applyVerificationConfirmation(TaskStepContext context, ExcavationBackend backend,
        ExcavationActionProgress progress) {
        ConfirmedExcavationTargetResult confirmation = progress.getConfirmation()
            .orElseThrow(() -> new IllegalStateException("confirmed verification action omitted its result"));
        try {
            validateConfirmation(confirmation);
            ExcavationTargetResult result = confirmation.getTargetResult();
            if (result.getOutcome() != ExcavationTargetOutcome.COMPLETED) {
                throw new IllegalStateException("rediscovered block was not confirmed clear: " + result.getOutcome());
            }
            if (randomVerification) {
                RuntimeException releaseFailure = releaseConfirmed();
                if (releaseFailure != null) throw releaseFailure;
                excavationCheckpoint = completionCandidate;
                taskCheckpoint = ExcavationTaskCheckpointCodec.encode(cylinder, excavationCheckpoint);
                BlockPosition cleared = result.getPosition();
                clearVerification();
                primaryActionsSinceRandomAudit = 0;
                randomAuditPending = false;
                return StepResult.progress(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Cleared randomly rediscovered block " + cleared + "; resuming primary excavation");
            }
            ExcavationFrontier next = activePlan.getNextFrontier();
            RuntimeException releaseFailure = releaseConfirmed();
            if (releaseFailure != null) throw releaseFailure;
            verificationFrontier = next;
            verificationChanged = true;
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Cleared rediscovered block " + result.getPosition() + "; continuing cleared-area verification");
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = stopActive();
            if (cleanupFailure != null) failure.addSuppressed(cleanupFailure);
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Excavation verification confirmation rejected: " + describe(failure),
                false);
        }
    }

    private StepResult finishOrRepeatVerification(TaskStepContext context) {
        if (verificationChanged) {
            verificationFrontier = verificationLayerY == null ? CylinderExcavationGeometry.initialFrontier(cylinder)
                : CylinderExcavationGeometry.layerStart(cylinder, verificationLayerY.intValue());
            verificationChanged = false;
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                verificationLayerY == null
                    ? "Rediscovered blocks were cleared; starting the required final clean verification pass"
                    : "Rediscovered blocks were cleared; rechecking completed layer " + verificationLayerY);
        }
        excavationCheckpoint = completionCandidate;
        taskCheckpoint = ExcavationTaskCheckpointCodec.encode(cylinder, excavationCheckpoint);
        Integer completedLayer = verificationLayerY;
        clearVerification();
        if (!excavationCheckpoint.isComplete()) {
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Completed a clean verification of layer " + completedLayer + "; continuing excavation");
        }
        return StepResult.completed(
            context.getActionEpoch(),
            taskCheckpoint,
            "Clean-volume excavation completed after a fresh full-volume verification pass");
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
        activeVerification = false;
    }

    private void clearVerification() {
        completionCandidate = null;
        verificationFrontier = null;
        verificationLayerY = null;
        verificationChanged = false;
        activeVerification = false;
        randomVerification = false;
    }

    private StepResult acceptPrimaryCheckpoint(TaskStepContext context, ExcavationCheckpoint appliedCheckpoint,
        String detailPrefix) {
        ExcavationFrontier previous = excavationCheckpoint.getFrontier();
        if (appliedCheckpoint.isComplete()) {
            completionCandidate = appliedCheckpoint;
            verificationFrontier = CylinderExcavationGeometry.initialFrontier(cylinder);
            verificationLayerY = null;
            verificationChanged = false;
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                detailPrefix + "; verifying the entire cleared volume against fresh world state");
        }
        if (!previous.isComplete() && appliedCheckpoint.getFrontier()
            .getLayerY() < previous.getLayerY()) {
            completionCandidate = appliedCheckpoint;
            verificationLayerY = Integer.valueOf(previous.getLayerY());
            verificationFrontier = CylinderExcavationGeometry.layerStart(cylinder, previous.getLayerY());
            verificationChanged = false;
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                detailPrefix + "; verifying completed layer " + previous.getLayerY() + " before descending");
        }
        excavationCheckpoint = appliedCheckpoint;
        taskCheckpoint = ExcavationTaskCheckpointCodec.encode(cylinder, excavationCheckpoint);
        return StepResult.progress(
            context.getActionEpoch(),
            taskCheckpoint,
            detailPrefix + "; "
                + excavationCheckpoint.getProgress()
                    .getRemaining()
                + " blocks remain");
    }

    private boolean verificationBoundaryReached() {
        return verificationFrontier.isComplete()
            || verificationLayerY != null && verificationFrontier.getLayerY() != verificationLayerY.intValue();
    }

    private StepResult performRandomAudit(TaskStepContext context, ExcavationBackend backend) {
        randomAuditPending = false;
        if (excavationCheckpoint.getFrontier()
            .isComplete()) return observeAndSubmit(context, backend);
        int completedLayers = cylinder.getTopY() - excavationCheckpoint.getFrontier()
            .getLayerY();
        if (completedLayers <= 0) {
            primaryActionsSinceRandomAudit = 0;
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Deferred cleared-area sampling until a complete layer exists");
        }

        Set<BlockPosition> sampled = new java.util.LinkedHashSet<>();
        long availablePositions = cylinder.getColumnCount() * completedLayers;
        int desiredSamples = (int) Math.min(RANDOM_AUDIT_SAMPLES, availablePositions);
        for (int attempt = 0; sampled.size() < desiredSamples && attempt < RANDOM_AUDIT_SAMPLES * 16; attempt++) {
            int y = cylinder.getTopY() - nextAuditInt(completedLayers);
            int deltaX;
            int deltaZ;
            do {
                deltaX = nextAuditInt(cylinder.getRadius() * 2 + 1) - cylinder.getRadius();
                deltaZ = nextAuditInt(cylinder.getRadius() * 2 + 1) - cylinder.getRadius();
            } while ((long) deltaX * deltaX + (long) deltaZ * deltaZ
                > (long) cylinder.getRadius() * cylinder.getRadius());
            sampled.add(new BlockPosition(cylinder.getCenterX() + deltaX, y, cylinder.getCenterZ() + deltaZ));
        }

        int checked = 0;
        for (BlockPosition position : sampled) {
            ExcavationFrontier targetFrontier = CylinderExcavationGeometry.atPosition(cylinder, position);
            ExcavationObservationRequest request = new ExcavationObservationRequest(
                spec.getId(),
                cylinder.getDimensionId(),
                excavationCheckpoint.getTaskRevision(),
                context.getActionEpoch(),
                cylinder.getGeometryKey(),
                targetFrontier,
                position,
                ExcavationServiceRequirements.none());
            ExcavationObservationResult observed;
            try {
                observed = backend.observe(request);
                validateObservation(request, observed);
            } catch (RuntimeException failure) {
                randomAuditPending = true;
                return StepResult.failed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Random cleared-area observation failed: " + describe(failure),
                    true);
            }
            if (!isLiveAuthority(context, backend)) {
                return StepResult.failed(
                    context.getActionEpoch(),
                    taskCheckpoint,
                    "Excavation authority changed during random cleared-area sampling",
                    false);
            }
            checked++;
            if (observed.getObservation()
                .getClassification() != ExcavationBlockClassification.BREAKABLE) continue;
            completionCandidate = excavationCheckpoint;
            verificationFrontier = targetFrontier;
            verificationLayerY = null;
            verificationChanged = false;
            randomVerification = true;
            return verifyRandomAuditTarget(context, backend);
        }
        primaryActionsSinceRandomAudit = 0;
        return StepResult.progress(
            context.getActionEpoch(),
            taskCheckpoint,
            "Randomly checked " + checked + " positions in previously cleared layers");
    }

    private StepResult verifyRandomAuditTarget(TaskStepContext context, ExcavationBackend backend) {
        BlockPosition position = verificationFrontier.getPosition();
        ExcavationObservationRequest request = new ExcavationObservationRequest(
            spec.getId(),
            cylinder.getDimensionId(),
            completionCandidate.getTaskRevision(),
            context.getActionEpoch(),
            cylinder.getGeometryKey(),
            verificationFrontier,
            position,
            ExcavationServiceRequirements.none());
        ExcavationObservationResult observed;
        try {
            observed = backend.observe(request);
            validateObservation(request, observed);
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                taskCheckpoint,
                "Random cleared-area target observation failed: " + describe(failure),
                true);
        }
        if (observed.getObservation()
            .getClassification() != ExcavationBlockClassification.BREAKABLE) {
            clearVerification();
            primaryActionsSinceRandomAudit = 0;
            return StepResult.progress(
                context.getActionEpoch(),
                taskCheckpoint,
                "Randomly rediscovered position changed before cleanup; resuming excavation");
        }
        return submitVerificationAction(context, backend, observed);
    }

    private int nextAuditInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("audit bound must be positive");
        randomAuditState = randomAuditState * 6364136223846793005L + 1442695040888963407L;
        return (int) Math.floorMod(randomAuditState >>> 1, (long) bound);
    }

    private static long mixAuditSeed(long seed) {
        long mixed = seed + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ mixed >>> 30) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ mixed >>> 27) * 0x94D049BB133111EBL;
        return mixed ^ mixed >>> 31;
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
