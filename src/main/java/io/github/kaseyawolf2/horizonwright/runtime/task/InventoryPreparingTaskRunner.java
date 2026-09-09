package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunnerFactory;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;

/**
 * Inserts shared inventory work only at explicit runner boundaries, with a persisted uncertainty marker before
 * mutation. The delegate retains its own checkpoint revision and sees no decorator metadata.
 */
final class InventoryPreparingTaskRunner implements TaskRunner {

    private static final String PREFIX = "horizonwright.inventory.";
    private static final String VERSION = PREFIX + "version";
    private static final String DELEGATE_REVISION = PREFIX + "delegateRevision";
    private static final String PREPARING = PREFIX + "preparing";
    private static final String COMPLETING_UNLOAD = PREFIX + "completingUnload";
    private static final String COMPLETION_DETAIL = PREFIX + "completionDetail";

    private final TaskSpec spec;
    private final InventoryRuntimeAccess runtime;
    private final RuntimeTaskServices.DryRunSource dryRun;
    private final TaskRunnerFactory factory;
    private TaskRunner delegate;
    private TaskCheckpoint delegateCheckpoint;
    private TaskCheckpoint lastEnvelope;
    private long revision;
    private boolean preparing;
    private boolean reconcile;
    private boolean completingUnload;
    private boolean journaled;
    private String completionDetail;
    private InventoryService activeService;

    InventoryPreparingTaskRunner(TaskSpec spec, TaskCheckpoint checkpoint, InventoryRuntimeAccess runtime,
        RuntimeTaskServices.DryRunSource dryRun, TaskRunnerFactory factory) {
        if (spec == null || checkpoint == null || dryRun == null || factory == null)
            throw new IllegalArgumentException("inventory wrapper dependencies are required");
        this.spec = spec;
        this.runtime = runtime;
        this.dryRun = dryRun;
        this.factory = factory;
        revision = checkpoint.getRevision();
        delegateCheckpoint = checkpoint;
        if (isWrapped(checkpoint)) {
            lastEnvelope = checkpoint;
            journaled = true;
            Map<String, String> values = new LinkedHashMap<>(checkpoint.getValues());
            if (!"1".equals(values.remove(VERSION))) throw new IllegalArgumentException("unknown inventory checkpoint");
            long innerRevision = Long.parseLong(values.remove(DELEGATE_REVISION));
            preparing = parseBoolean(values.remove(PREPARING));
            completingUnload = parseBoolean(values.remove(COMPLETING_UNLOAD));
            completionDetail = values.remove(COMPLETION_DETAIL);
            if (completingUnload && !UnloadTask.TYPE.equals(spec.getType()))
                throw new IllegalArgumentException("only unload tasks can have pending inventory completion");
            delegateCheckpoint = new TaskCheckpoint(innerRevision, values);
            reconcile = preparing;
        }
        delegate = factory.create(spec, delegateCheckpoint);
    }

    static boolean isWrapped(TaskCheckpoint checkpoint) {
        return checkpoint.getValues()
            .containsKey(VERSION);
    }

    static boolean isPreparing(TaskCheckpoint checkpoint) {
        return isWrapped(checkpoint) && "true".equals(
            checkpoint.getValues()
                .get(PREPARING));
    }

    static TaskCheckpoint unwrap(TaskCheckpoint checkpoint) {
        if (!isWrapped(checkpoint)) return checkpoint;
        Map<String, String> values = new LinkedHashMap<>(checkpoint.getValues());
        if (!"1".equals(values.remove(VERSION))) throw new IllegalArgumentException("unknown inventory checkpoint");
        long innerRevision = Long.parseLong(values.remove(DELEGATE_REVISION));
        values.remove(PREPARING);
        values.remove(COMPLETING_UNLOAD);
        values.remove(COMPLETION_DETAIL);
        return new TaskCheckpoint(innerRevision, values);
    }

    @Override
    public synchronized StepResult step(TaskStepContext outerContext) {
        TaskStepContext context = outerContext.withCheckpoint(delegateCheckpoint);
        InventoryService service = runtime == null ? null : runtime.getInventoryService();
        if (preparing) {
            if (service == null)
                return wrap(blocked(context, "Portable inventory service is unavailable during recovery."));
            if (activeService != null && activeService != service) reconcile = true;
            if (!context.getActions()
                .isAuthoritative())
                return wrap(
                    StepResult.failed(
                        context.getActionEpoch(),
                        delegateCheckpoint,
                        "Portable inventory preparation lost action authority",
                        false));
            if (dryRun.isDryRun() && !reconcile && !context.isSuspensionRequested())
                return wrap(blocked(context, "Dry-run mode prevents pending portable inventory preparation."));
            activeService = service;
            StepResult preparation = reconcile ? service.reconcileInterruptedPreparation(context, completingUnload)
                : service.prepare(context, completingUnload);
            if (preparation != null) return wrapPreparation(preparation, context);
            preparing = false;
            reconcile = false;
            activeService = null;
            if (completingUnload) {
                completingUnload = false;
                delegateCheckpoint = TaskCheckpoint.empty();
                delegate = factory.create(spec, delegateCheckpoint);
            }
            return wrap(
                StepResult.progress(
                    context.getActionEpoch(),
                    delegateCheckpoint,
                    "Portable inventory synchronized; task inventory will be observed again"));
        }
        if (context.isSuspensionRequested() || !context.getActions()
            .isAuthoritative() || dryRun.isDryRun()) {
            if (completingUnload) return wrap(
                StepResult.safeSuspension(
                    context.getActionEpoch(),
                    delegateCheckpoint,
                    "Unload suspended between inventory batches"));
            return delegateStep(context);
        }
        if (completingUnload) {
            if (service != null && service.needsPreparation(context, true)) return beginPreparation(context, service);
            return wrap(StepResult.completed(context.getActionEpoch(), delegateCheckpoint, completionDetail));
        }
        if (service != null && delegate.isInventoryPreparationSafe() && service.needsPreparation(context, false))
            return beginPreparation(context, service);
        return delegateStep(context);
    }

    private StepResult beginPreparation(TaskStepContext context, InventoryService service) {
        preparing = true;
        activeService = service;
        return wrap(
            StepResult.progress(
                context.getActionEpoch(),
                delegateCheckpoint,
                "Recorded portable inventory preparation before sending any inventory action"));
    }

    private StepResult delegateStep(TaskStepContext context) {
        StepResult result = delegate.step(context);
        delegateCheckpoint = result.getCheckpoint();
        InventoryService service = runtime == null ? null : runtime.getInventoryService();
        if (result.getKind() == StepResult.Kind.COMPLETED && UnloadTask.TYPE.equals(spec.getType())
            && service != null
            && !dryRun.isDryRun()
            && service.needsPreparation(context.withCheckpoint(delegateCheckpoint), true)) {
            completingUnload = true;
            completionDetail = result.getDetail();
            return beginPreparation(context.withCheckpoint(delegateCheckpoint), service);
        }
        return wrap(result);
    }

    private StepResult wrapPreparation(StepResult result, TaskStepContext context) {
        if (result.getActionEpoch() != context.getActionEpoch())
            throw new IllegalStateException("inventory preparation returned another action epoch");
        if (!delegateCheckpoint.equals(result.getCheckpoint()))
            throw new IllegalStateException("inventory preparation must preserve the task checkpoint");
        if (result.getKind() == StepResult.Kind.COMPLETED)
            throw new IllegalStateException("inventory preparation must return null when cleaned up");
        if (result.getKind() == StepResult.Kind.SAFE_SUSPENSION || result.getKind() == StepResult.Kind.BLOCKED
            || result.getKind() == StepResult.Kind.FAILED) reconcile = true;
        return wrap(result);
    }

    private StepResult wrap(StepResult result) {
        if (!journaled && !preparing) {
            revision = result.getCheckpoint()
                .getRevision();
            return result;
        }
        journaled = true;
        Map<String, String> values = new LinkedHashMap<>(delegateCheckpoint.getValues());
        values.put(VERSION, "1");
        values.put(DELEGATE_REVISION, Long.toString(delegateCheckpoint.getRevision()));
        values.put(PREPARING, Boolean.toString(preparing));
        values.put(COMPLETING_UNLOAD, Boolean.toString(completingUnload));
        values.put(COMPLETION_DETAIL, completionDetail == null ? "" : completionDetail);
        TaskCheckpoint checkpoint = lastEnvelope != null && lastEnvelope.getValues()
            .equals(values) ? lastEnvelope : new TaskCheckpoint(Math.addExact(revision, 1L), values);
        lastEnvelope = checkpoint;
        revision = checkpoint.getRevision();
        long epoch = result.getActionEpoch();
        switch (result.getKind()) {
            case PROGRESS:
                return StepResult.progress(epoch, checkpoint, result.getDetail());
            case WAIT:
                return StepResult
                    .waitFor(epoch, checkpoint, ((StepResult.Wait) result).getDelayMillis(), result.getDetail());
            case SAFE_SUSPENSION:
                return StepResult.safeSuspension(epoch, checkpoint, result.getDetail());
            case COMPLETED:
                return StepResult.completed(epoch, checkpoint, result.getDetail());
            case FAILED:
                return StepResult
                    .failed(epoch, checkpoint, result.getDetail(), ((StepResult.Failed) result).isRetryable());
            case BLOCKED:
                return StepResult.blocked(epoch, checkpoint, ((StepResult.Blocked) result).getReason());
            default:
                throw new IllegalStateException("unknown inventory wrapper result");
        }
    }

    private StepResult blocked(TaskStepContext context, String detail) {
        return StepResult.blocked(
            context.getActionEpoch(),
            delegateCheckpoint,
            BlockedReason.missingRequirement(
                detail,
                spec.getId(),
                "available portable inventory service",
                "Restore the inventory integration, then resume the task."));
    }

    @Override
    public synchronized void interrupt(TaskInterruption interruption) {
        if (interruption == null) throw new IllegalArgumentException("interruption is required");
        if (preparing) reconcile = true;
        InventoryService currentService = runtime == null ? null : runtime.getInventoryService();
        try {
            if (activeService != null) activeService.interrupt(spec.getId(), interruption);
        } finally {
            try {
                if (currentService != null && currentService != activeService)
                    currentService.interrupt(spec.getId(), interruption);
            } finally {
                delegate.interrupt(interruption);
            }
        }
    }

    private static boolean parseBoolean(String value) {
        if (!"true".equals(value) && !"false".equals(value))
            throw new IllegalArgumentException("malformed inventory checkpoint flag");
        return Boolean.parseBoolean(value);
    }
}
