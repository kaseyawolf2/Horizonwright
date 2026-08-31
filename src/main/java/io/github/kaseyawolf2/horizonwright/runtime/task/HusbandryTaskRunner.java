package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryAction;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryObservation;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryPlan;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryPlanner;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryPolicy;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

/** Reobserves the complete pen before and after every bounded livestock action. */
final class HusbandryTaskRunner implements TaskRunner {

    private final TaskSpec spec;
    private final HusbandryRuntimeAccess runtime;
    private final HusbandryPlanner planner = new HusbandryPlanner();
    private TaskCheckpoint checkpoint;
    private int verifiedActions;
    private HusbandryBackend activeBackend;
    private HusbandryBackend.ActionHandle activeHandle;
    private ActionLease activeLease;
    private String activeRequestId;

    HusbandryTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, HusbandryRuntimeAccess runtime) {
        if (checkpoint == null || runtime == null)
            throw new IllegalArgumentException("checkpoint and runtime required");
        HusbandryTask.penId(spec);
        HusbandryTask.species(spec);
        HusbandryTask.maximumAdults(spec);
        HusbandryTask.maximumActions(spec);
        this.spec = spec;
        this.runtime = runtime;
        this.checkpoint = checkpoint;
        this.verifiedActions = decode(checkpoint);
    }

    @Override
    public synchronized StepResult step(TaskStepContext context) {
        requireContext(context);
        if (context.isSuspensionRequested()) return suspend(context);
        if (!context.getActions()
            .isAuthoritative()) return failure(context, "Husbandry task lost its action epoch", false);
        if (runtime.isDryRun()) {
            cancelActive();
            return blocked(context, "Dry-run prevents livestock actions", "live husbandry execution");
        }
        HusbandryBackend backend = runtime.getHusbandryBackend();
        HusbandryBackend.Availability availability = availability(backend);
        if (backend == null || !availability.isAvailable()) {
            cancelActive();
            return blocked(context, availability.getDiagnostic(), "a tested husbandry backend");
        }
        if (activeHandle != null) return observeAction(context, backend);
        return observeAndPlan(context, backend);
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) throw new IllegalArgumentException("interruption required");
        cancelActive();
    }

    private StepResult observeAndPlan(TaskStepContext context, HusbandryBackend backend) {
        HusbandryBackend.ObservationRequest request = new HusbandryBackend.ObservationRequest(
            spec.getId(),
            HusbandryTask.penId(spec),
            context.getActionEpoch(),
            verifiedActions);
        try {
            HusbandryBackend.ObservationSnapshot snapshot = backend.observe(request);
            validate(request, snapshot);
            HusbandryObservation observation = snapshot.getObservation();
            HusbandryPolicy policy = new HusbandryPolicy(
                observation.getPen(),
                HusbandryTask.species(spec),
                1L,
                HusbandryTask.minimumAdults(spec),
                HusbandryTask.maximumAdults(spec));
            HusbandryPlan plan = planner.plan(policy, observation);
            if (plan.isHeld()) return blocked(context, plan.getHoldReason(), "a complete, loaded, safe named pen");
            if (plan.getActions()
                .isEmpty())
                return completed(context, "Husbandry pass confirmed stable after " + verifiedActions + " action(s)");
            if (verifiedActions >= HusbandryTask.maximumActions(spec)) {
                return blocked(
                    context,
                    "Husbandry pass reached its configured action cap",
                    "operator review of the pen policy");
            }
            HusbandryBackend.ActionReadiness readiness = backend.readiness(plan);
            if (readiness == null || !readiness.isReady()) {
                String diagnostic = readiness == null ? "Husbandry backend returned no action preflight"
                    : readiness.getDiagnostic();
                return blocked(context, diagnostic, "the exact species-specific action prerequisite");
            }
            return submit(context, backend, plan);
        } catch (RuntimeException failure) {
            return StepResult.failed(
                context.getActionEpoch(),
                checkpoint,
                "Husbandry observation failed: " + describe(failure),
                true);
        }
    }

    private StepResult submit(TaskStepContext context, HusbandryBackend backend, HusbandryPlan plan) {
        HusbandryAction action = plan.getActions()
            .get(0);
        Optional<ActionLease> acquired = context.getActions()
            .tryAcquire(capabilities(action.getKind()));
        if (!acquired.isPresent())
            return StepResult.waitFor(context.getActionEpoch(), checkpoint, 0L, "waiting for husbandry capabilities");
        ActionLease lease = acquired.get();
        String requestId = spec.getId() + "-husbandry-" + verifiedActions;
        HusbandryBackend.ActionRequest request = new HusbandryBackend.ActionRequest(
            requestId,
            spec.getId(),
            context.getActionEpoch(),
            verifiedActions,
            plan);
        try {
            if (!lease.isValid() || runtime.getHusbandryBackend() != backend)
                throw new IllegalStateException("husbandry authority changed");
            HusbandryBackend.ActionHandle handle = backend.execute(request, lease);
            if (handle == null || !requestId.equals(handle.getRequestId()))
                throw new IllegalStateException("mismatched husbandry handle");
            activeBackend = backend;
            activeHandle = handle;
            activeLease = lease;
            activeRequestId = requestId;
            return StepResult.progress(context.getActionEpoch(), checkpoint, "Submitted " + action.getKind());
        } catch (RuntimeException failure) {
            lease.close();
            return StepResult.failed(
                context.getActionEpoch(),
                checkpoint,
                "Husbandry submission failed: " + describe(failure),
                true);
        }
    }

    private StepResult observeAction(TaskStepContext context, HusbandryBackend backend) {
        if (activeBackend != backend || runtime.getHusbandryBackend() != backend
            || activeLease == null
            || !activeLease.isValid()
            || activeLease.getEpoch() != context.getActionEpoch()) {
            return failure(context, "Husbandry authority changed before confirmation", false);
        }
        try {
            HusbandryBackend.ActionProgress progress = activeHandle.progress();
            if (progress == null || !activeRequestId.equals(progress.getRequestId()))
                throw new IllegalStateException("mismatched husbandry progress");
            if (progress.getState() == HusbandryBackend.ActionState.SUBMITTED
                || progress.getState() == HusbandryBackend.ActionState.EXECUTING) {
                return StepResult.waitFor(context.getActionEpoch(), checkpoint, 0L, progress.getDetail());
            }
            if (progress.getState() == HusbandryBackend.ActionState.CONFIRMED) {
                releaseActive();
                verifiedActions++;
                checkpoint = encode(verifiedActions);
                return StepResult.progress(
                    context.getActionEpoch(),
                    checkpoint,
                    progress.getDetail() + "; reobserving complete pen");
            }
            return failure(context, progress.getDetail(), progress.getState() == HusbandryBackend.ActionState.FAILED);
        } catch (RuntimeException failure) {
            return failure(context, "Husbandry confirmation failed: " + describe(failure), false);
        }
    }

    private StepResult completed(TaskStepContext context, String detail) {
        if (checkpoint.getRevision() == 0L) checkpoint = encode(verifiedActions);
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
                "Resolve the requirement, then resume this husbandry task."));
    }

    private StepResult suspend(TaskStepContext context) {
        cancelActive();
        return StepResult.safeSuspension(
            context.getActionEpoch(),
            checkpoint,
            "Husbandry stopped before advancing an unconfirmed action");
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
        HusbandryBackend.ActionHandle handle = activeHandle;
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
            throw new IllegalArgumentException("husbandry context mismatched");
        if (!checkpoint.equals(context.getCheckpoint()))
            throw new IllegalStateException("husbandry checkpoint diverged");
    }

    private TaskCheckpoint encode(int actions) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("verifiedActions", Integer.toString(actions));
        return new TaskCheckpoint(checkpoint.getRevision() + 1L, values);
    }

    private static int decode(TaskCheckpoint checkpoint) {
        if (checkpoint.getRevision() == 0L && checkpoint.getValues()
            .isEmpty()) return 0;
        if (checkpoint.getValues()
            .size() != 1
            || !checkpoint.getValues()
                .containsKey("verifiedActions"))
            throw new IllegalArgumentException("invalid husbandry checkpoint");
        try {
            int value = Integer.parseInt(
                checkpoint.getValues()
                    .get("verifiedActions"));
            if (value < 0) throw new IllegalArgumentException("negative husbandry count");
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid husbandry count", failure);
        }
    }

    private static HusbandryBackend.Availability availability(HusbandryBackend backend) {
        if (backend == null) return HusbandryBackend.Availability.unavailable("No husbandry backend is configured");
        HusbandryBackend.Availability value = backend.availability();
        return value == null ? HusbandryBackend.Availability.unavailable("Husbandry backend returned no availability")
            : value;
    }

    private static void validate(HusbandryBackend.ObservationRequest request,
        HusbandryBackend.ObservationSnapshot snapshot) {
        if (snapshot == null || !request.getTaskId()
            .equals(snapshot.getTaskId())
            || request.getActionEpoch() != snapshot.getActionEpoch()
            || request.getVerifiedActions() != snapshot.getVerifiedActions()
            || !request.getPenId()
                .equals(
                    snapshot.getObservation()
                        .getPen()
                        .getId())) {
            throw new IllegalStateException("stale or mismatched husbandry evidence");
        }
    }

    private static Set<ActionCapability> capabilities(HusbandryActionKind kind) {
        if (kind == HusbandryActionKind.FEED_ADULT) return Collections.unmodifiableSet(
            EnumSet
                .of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.USE, ActionCapability.HELD_USE));
        if (kind == HusbandryActionKind.CULL_EXCESS_ADULT) return Collections
            .unmodifiableSet(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.ATTACK));
        if (kind == HusbandryActionKind.COLLECT_DROPS)
            return Collections.unmodifiableSet(EnumSet.of(ActionCapability.MOVEMENT));
        throw new IllegalArgumentException("unsupported husbandry action " + kind);
    }

    private static String describe(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }
}
