package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionBroker;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocation;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocationListener;

/**
 * Deterministic, in-memory implementation of the Horizonwright task controller.
 *
 * <p>
 * One bounded runner step is executed per tick. Safety preemption is synchronous; all other
 * preemption waits for a runner-declared safe suspension point.
 */
public final class TaskOrchestrator implements IHorizonwrightController, ActionRevocationListener, AutoCloseable {

    private final MonotonicClock clock;
    private final TaskRunnerFactory runnerFactory;
    private final ActionBroker actionBroker;
    private final RetryPolicy retryPolicy;
    private final TaskScheduler scheduler = new TaskScheduler();
    private final Map<String, TaskRecord> tasks = new LinkedHashMap<>();
    private final EnumMap<TaskLane, List<String>> laneOrder = new EnumMap<>(TaskLane.class);
    private final List<DeferredCallback> deferredCallbacks = new ArrayList<>();

    private long lastObservedMillis = Long.MIN_VALUE;
    private long nextOperationToken = 1L;
    private String activeTaskId;

    public TaskOrchestrator(MonotonicClock clock, TaskRunnerFactory runnerFactory, ActionBroker actionBroker) {
        this(clock, runnerFactory, actionBroker, RetryPolicy.defaultPolicy());
    }

    public TaskOrchestrator(MonotonicClock clock, TaskRunnerFactory runnerFactory, ActionBroker actionBroker,
        RetryPolicy retryPolicy) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (runnerFactory == null) {
            throw new IllegalArgumentException("runnerFactory must not be null");
        }
        if (actionBroker == null) {
            throw new IllegalArgumentException("actionBroker must not be null");
        }
        if (retryPolicy == null) {
            throw new IllegalArgumentException("retryPolicy must not be null");
        }
        this.clock = clock;
        this.runnerFactory = runnerFactory;
        this.actionBroker = actionBroker;
        this.retryPolicy = retryPolicy;
        for (TaskLane lane : TaskLane.values()) {
            laneOrder.put(lane, new ArrayList<String>());
        }
        currentActionEpoch();
        actionBroker.addRevocationListener(this);
        DevelopmentTrace.event(
            "task-controller",
            "created",
            "epoch",
            currentActionEpoch(),
            "retryLimit",
            retryPolicy.getMaximumRetries());
    }

    @Override
    public TaskSnapshot submit(TaskSpec spec) {
        return restore(spec, TaskCheckpoint.empty());
    }

    @Override
    public TaskSnapshot restore(TaskSpec spec, TaskCheckpoint checkpoint) {
        requireSpec(spec);
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        synchronized (this) {
            if (tasks.containsKey(spec.getId())) {
                throw new IllegalArgumentException("task already exists: " + spec.getId());
            }

            long now = readNow();
            TaskRecord record = new TaskRecord(spec, checkpoint, now);
            tasks.put(spec.getId(), record);
            laneOrder.get(spec.getLane())
                .add(spec.getId());
            interruptForNewSafetyIfNeeded(record, now);
            enqueueRunnerBuild(record, "runner ready");
            traceRecord("restored", record, "checkpointRevision", checkpoint.getRevision());
        }
        drainDeferredCallbacks();
        synchronized (this) {
            return taskSnapshotAt(spec.getId(), readNow());
        }
    }

    @Override
    public TaskSnapshot update(TaskSpec replacement) {
        requireSpec(replacement);
        synchronized (this) {
            long now = readNow();
            TaskRecord record = requireTask(replacement.getId());
            if (record.state.isTerminal()) {
                throw new IllegalStateException("terminal tasks cannot be edited: " + replacement.getId());
            }
            if (isActive(record)) {
                throw new IllegalStateException(
                    "active tasks must be suspended before editing: " + replacement.getId());
            }

            if (record.spec.getLane() != replacement.getLane()) {
                laneOrder.get(record.spec.getLane())
                    .remove(record.spec.getId());
                laneOrder.get(replacement.getLane())
                    .add(replacement.getId());
            }
            record.spec = replacement;
            record.controlVersion++;
            interruptForNewSafetyIfNeeded(record, now);
            enqueueRunnerBuild(record, "task specification updated");
            traceRecord("updated", record, "lane", replacement.getLane());
        }
        drainDeferredCallbacks();
        synchronized (this) {
            return taskSnapshotAt(replacement.getId(), readNow());
        }
    }

    @Override
    public TaskSnapshot pause(String taskId) {
        synchronized (this) {
            long now = readNow();
            TaskRecord record = requireTask(taskId);
            switch (record.state) {
                case QUEUED:
                    record.state = TaskState.SUSPENDED;
                    record.suspensionReason = TaskSuspensionReason.OPERATOR_PAUSE;
                    record.detail = "paused by operator";
                    break;
                case RUNNING:
                    record.state = TaskState.SUSPENDING;
                    record.suspensionReason = TaskSuspensionReason.OPERATOR_PAUSE;
                    record.detail = "waiting for a safe pause point";
                    break;
                case SUSPENDING:
                    if (record.suspensionReason == TaskSuspensionReason.CANCELLATION) {
                        throw new IllegalStateException("cancellation is already pending: " + taskId);
                    }
                    record.suspensionReason = TaskSuspensionReason.OPERATOR_PAUSE;
                    record.detail = "waiting for a safe pause point";
                    break;
                case SUSPENDED:
                    record.suspensionReason = TaskSuspensionReason.OPERATOR_PAUSE;
                    record.detail = "paused by operator";
                    break;
                case BLOCKED:
                    throw new IllegalStateException(
                        "blocked tasks must be resumed after their requirement is fixed: " + taskId);
                default:
                    throw new IllegalStateException("terminal task cannot be paused: " + taskId);
            }
            record.controlVersion++;
            traceRecord("pause-requested", record, "reason", record.suspensionReason);
            return taskSnapshotAt(record.spec.getId(), now);
        }
    }

    @Override
    public TaskSnapshot resume(String taskId) {
        String normalizedTaskId;
        boolean rebuildRunner = false;
        synchronized (this) {
            long now = readNow();
            TaskRecord record = requireTask(taskId);
            normalizedTaskId = record.spec.getId();
            switch (record.state) {
                case QUEUED:
                case RUNNING:
                    break;
                case SUSPENDING:
                    if (record.suspensionReason == TaskSuspensionReason.CANCELLATION) {
                        throw new IllegalStateException("cancellation cannot be resumed: " + taskId);
                    }
                    record.state = TaskState.RUNNING;
                    record.suspensionReason = TaskSuspensionReason.NONE;
                    record.detail = "suspension request withdrawn";
                    break;
                case SUSPENDED:
                    record.state = TaskState.QUEUED;
                    record.suspensionReason = TaskSuspensionReason.NONE;
                    record.nextEligibleAtMillis = now;
                    record.detail = "queued after resume";
                    break;
                case BLOCKED:
                    record.state = TaskState.QUEUED;
                    record.blockedReason = null;
                    record.retryCount = 0;
                    record.nextEligibleAtMillis = now;
                    record.suspensionReason = TaskSuspensionReason.NONE;
                    rebuildRunner = true;
                    break;
                default:
                    throw new IllegalStateException("terminal task cannot be resumed: " + taskId);
            }
            record.controlVersion++;
            interruptForNewSafetyIfNeeded(record, now);
            if (rebuildRunner) {
                enqueueRunnerBuild(record, "queued after blocked condition was acknowledged");
            }
            traceRecord("resume-requested", record, "runnerRebuild", rebuildRunner);
        }
        drainDeferredCallbacks();
        synchronized (this) {
            return taskSnapshotAt(normalizedTaskId, readNow());
        }
    }

    @Override
    public TaskSnapshot cancel(String taskId) {
        synchronized (this) {
            long now = readNow();
            TaskRecord record = requireTask(taskId);
            if (record.state.isTerminal()) {
                return taskSnapshotAt(record.spec.getId(), now);
            }
            record.controlVersion++;
            record.buildToken = 0L;
            if (isActive(record)) {
                record.state = TaskState.SUSPENDING;
                record.suspensionReason = TaskSuspensionReason.CANCELLATION;
                record.detail = "waiting for a safe cancellation point";
            } else {
                record.state = TaskState.CANCELLED;
                record.suspensionReason = TaskSuspensionReason.CANCELLATION;
                record.blockedReason = null;
                record.detail = "cancelled";
                removeFromLane(record);
            }
            traceRecord("cancel-requested", record, "active", isActive(record));
            return taskSnapshotAt(record.spec.getId(), now);
        }
    }

    @Override
    public TaskSnapshot remove(String taskId) {
        synchronized (this) {
            long now = readNow();
            TaskRecord record = requireTask(taskId);
            if (isActive(record) || record.state == TaskState.RUNNING || record.state == TaskState.SUSPENDING) {
                throw new IllegalStateException(
                    "active or draining tasks must finish or be cancelled before deletion: " + record.spec.getId());
            }
            TaskSnapshot removed = taskSnapshotAt(record.spec.getId(), now);
            removeFromLane(record);
            record.controlVersion++;
            record.buildToken = 0L;
            tasks.remove(record.spec.getId());
            traceRecord("removed", record, "remainingTasks", tasks.size());
            return removed;
        }
    }

    @Override
    public TaskSnapshot reorder(String taskId, int targetPosition) {
        synchronized (this) {
            long now = readNow();
            TaskRecord record = requireTask(taskId);
            if (record.state.isTerminal()) {
                throw new IllegalStateException("terminal tasks are not in a lane queue: " + taskId);
            }
            List<String> queue = laneOrder.get(record.spec.getLane());
            if (targetPosition < 0 || targetPosition >= queue.size()) {
                throw new IllegalArgumentException("targetPosition is outside the lane queue");
            }
            String normalizedTaskId = record.spec.getId();
            queue.remove(normalizedTaskId);
            queue.add(targetPosition, normalizedTaskId);
            record.detail = "reordered within " + record.spec.getLane() + " lane";
            traceRecord("reordered", record, "targetPosition", targetPosition);
            return taskSnapshotAt(normalizedTaskId, now);
        }
    }

    @Override
    public Optional<TaskSnapshot> inspect(String taskId) {
        synchronized (this) {
            long now = readNow();
            if (taskId == null) {
                return Optional.empty();
            }
            String normalizedTaskId = taskId.trim();
            if (normalizedTaskId.isEmpty() || !tasks.containsKey(normalizedTaskId)) {
                return Optional.empty();
            }
            return Optional.of(taskSnapshotAt(normalizedTaskId, now));
        }
    }

    @Override
    public ScheduleSnapshot submitSchedule(ScheduleRule rule) {
        synchronized (this) {
            return scheduler.submit(rule);
        }
    }

    @Override
    public ScheduleSnapshot updateSchedule(ScheduleRule replacement) {
        synchronized (this) {
            return scheduler.update(replacement);
        }
    }

    @Override
    public ScheduleSnapshot pauseSchedule(String scheduleId) {
        synchronized (this) {
            return scheduler.pause(scheduleId);
        }
    }

    @Override
    public ScheduleSnapshot resumeSchedule(String scheduleId) {
        synchronized (this) {
            return scheduler.resume(scheduleId);
        }
    }

    @Override
    public ScheduleSnapshot cancelSchedule(String scheduleId) {
        synchronized (this) {
            return scheduler.cancel(scheduleId);
        }
    }

    @Override
    public Optional<ScheduleSnapshot> inspectSchedule(String scheduleId) {
        synchronized (this) {
            return scheduler.inspect(scheduleId);
        }
    }

    @Override
    public TaskControllerState exportState() {
        synchronized (this) {
            long now = readNow();
            Map<String, Integer> positions = queuePositions();
            List<RestoredTaskSnapshot> exported = new ArrayList<>(tasks.size());
            for (TaskRecord record : tasks.values()) {
                int queuePosition = record.state.isTerminal() ? -1 : positions.get(record.spec.getId());
                long remainingDelay = record.nextEligibleAtMillis <= now ? 0L : record.nextEligibleAtMillis - now;
                exported.add(
                    new RestoredTaskSnapshot(
                        record.spec,
                        record.state,
                        record.checkpoint,
                        record.retryCount,
                        remainingDelay,
                        record.suspensionReason,
                        record.blockedReason,
                        queuePosition,
                        record.rejectedStaleResults,
                        record.detail,
                        record.sourceScheduleId));
            }
            return new TaskControllerState(currentActionEpoch(), exported, scheduler.snapshot());
        }
    }

    @Override
    public ControllerSnapshot restoreState(TaskControllerState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        synchronized (this) {
            if (!tasks.isEmpty() || activeTaskId != null || !scheduler.isPristine()) {
                throw new IllegalStateException("controller restore requires a new, empty orchestrator");
            }
            long now = readNow();
            advanceActionAuthorityPast(state.getLastActionEpoch());
            scheduler.restore(state.getScheduler());

            EnumMap<TaskLane, List<RestoredTaskSnapshot>> queued = new EnumMap<>(TaskLane.class);
            for (TaskLane lane : TaskLane.values()) {
                queued.put(lane, new ArrayList<RestoredTaskSnapshot>());
            }
            for (RestoredTaskSnapshot saved : state.getTasks()) {
                TaskRecord record = restoreRecord(saved, now);
                tasks.put(record.spec.getId(), record);
                if (!record.state.isTerminal()) {
                    queued.get(record.spec.getLane())
                        .add(saved);
                }
                if (record.state == TaskState.QUEUED || record.state == TaskState.SUSPENDED) {
                    enqueueRunnerBuild(record, "runner restored from checkpoint");
                }
            }
            Comparator<RestoredTaskSnapshot> byPosition = new Comparator<RestoredTaskSnapshot>() {

                @Override
                public int compare(RestoredTaskSnapshot left, RestoredTaskSnapshot right) {
                    return Integer.compare(left.getQueuePosition(), right.getQueuePosition());
                }
            };
            for (TaskLane lane : TaskLane.values()) {
                List<RestoredTaskSnapshot> savedLane = queued.get(lane);
                Collections.sort(savedLane, byPosition);
                for (RestoredTaskSnapshot saved : savedLane) {
                    TaskRecord record = tasks.get(
                        saved.getSpec()
                            .getId());
                    if (!record.state.isTerminal()) {
                        laneOrder.get(lane)
                            .add(record.spec.getId());
                    }
                }
            }
        }
        drainDeferredCallbacks();
        synchronized (this) {
            return snapshotAt(readNow());
        }
    }

    @Override
    public ControllerSnapshot snapshot() {
        synchronized (this) {
            return snapshotAt(readNow());
        }
    }

    @Override
    public ControllerSnapshot tick() {
        return tick(ScheduleEnvironment.disconnected());
    }

    @Override
    public ControllerSnapshot tick(ScheduleEnvironment environment) {
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        synchronized (this) {
            long now = readNow();
            DevelopmentTrace.event(
                "task-controller",
                "tick",
                "now",
                now,
                "tasks",
                tasks.size(),
                "active",
                activeTaskId,
                "epoch",
                currentActionEpoch(),
                "safetyLocked",
                actionBroker.isSafetyLocked());
            List<ScheduledTaskRequest> requests = scheduler
                .evaluate(now, environment, isControllerIdle(), occupiedScheduleIds());
            DevelopmentTrace.event(
                "task-controller",
                "schedule-evaluated",
                "requests",
                requests.size(),
                "environment",
                environment);
            for (ScheduledTaskRequest request : requests) {
                enqueueScheduledTask(request, now);
            }
        }
        drainDeferredCallbacks();

        StepInvocation invocation = null;
        synchronized (this) {
            long now = readNow();
            TaskRecord active = activeRecord();
            if (!actionBroker.isSafetyLocked()) {
                if (active != null && active.spec.getLane() != TaskLane.SAFETY && hasUnresolvedSafetyWork()) {
                    interruptForSafety(active, now);
                    active = null;
                }
                TaskRecord contender = highestEligible(now);

                if (active != null && contender != null
                    && contender.spec.getLane()
                        .hasPriorityOver(active.spec.getLane())) {
                    if (contender.spec.getLane() == TaskLane.SAFETY) {
                        interruptForSafety(active, now);
                        active = null;
                    } else if (active.suspensionReason == TaskSuspensionReason.NONE
                        || active.suspensionReason == TaskSuspensionReason.PREEMPTION) {
                            active.state = TaskState.SUSPENDING;
                            active.suspensionReason = TaskSuspensionReason.PREEMPTION;
                            active.detail = "waiting for a safe point before " + contender.spec.getId();
                            active.controlVersion++;
                        }
                } else if (active != null && active.suspensionReason == TaskSuspensionReason.PREEMPTION) {
                    active.state = TaskState.RUNNING;
                    active.suspensionReason = TaskSuspensionReason.NONE;
                    active.detail = "preemption request withdrawn";
                    active.controlVersion++;
                }

                if (active == null) {
                    active = highestEligible(now);
                    if (active != null) {
                        activate(active);
                    }
                }

                if (active != null && now >= active.nextEligibleAtMillis
                    && active.runner != null
                    && !active.stepInFlight) {
                    long stepToken = nextOperationToken();
                    active.stepInFlight = true;
                    active.stepToken = stepToken;
                    TaskActionGateway actions = new BrokerTaskActionGateway(
                        active.spec.getId(),
                        active.activeEpoch,
                        active.controlVersion);
                    TaskStepContext context = new TaskStepContext(
                        active.spec,
                        active.activeEpoch,
                        now,
                        active.checkpoint,
                        active.state == TaskState.SUSPENDING ? active.suspensionReason : TaskSuspensionReason.NONE,
                        actions);
                    invocation = new StepInvocation(active, active.runner, context, stepToken, active.controlVersion);
                    traceRecord(
                        "step-invoked",
                        active,
                        "stepToken",
                        stepToken,
                        "checkpointRevision",
                        active.checkpoint.getRevision());
                }
            }
        }

        drainDeferredCallbacks();
        if (invocation == null) {
            synchronized (this) {
                return snapshotAt(readNow());
            }
        }

        StepResult result;
        try {
            result = invocation.runner.step(invocation.context);
            if (result == null) {
                result = StepResult.failed(
                    invocation.context.getActionEpoch(),
                    invocation.context.getCheckpoint(),
                    "runner returned no result",
                    true);
            }
        } catch (RuntimeException failure) {
            DevelopmentTrace.event(
                "task-controller",
                "runner-threw",
                "task",
                invocation.record.spec.getId(),
                "error",
                DevelopmentTrace.error(failure));
            result = StepResult.failed(
                invocation.context.getActionEpoch(),
                invocation.context.getCheckpoint(),
                describeFailure(failure),
                true);
        }

        synchronized (this) {
            long now = readNow();
            TaskRecord record = invocation.record;
            DevelopmentTrace.event(
                "task-controller",
                "step-returned",
                "task",
                record.spec.getId(),
                "kind",
                result.getKind(),
                "resultEpoch",
                result.getActionEpoch(),
                "checkpointRevision",
                result.getCheckpoint()
                    .getRevision(),
                "detail",
                result.getDetail());
            if (record.stepToken == invocation.stepToken) {
                record.stepInFlight = false;
            }
            if (record.stepToken != invocation.stepToken || record.controlVersion != invocation.controlVersion
                || record.runner != invocation.runner) {
                record.rejectedStaleResults++;
                traceRecord(
                    "step-rejected-stale",
                    record,
                    "stepToken",
                    invocation.stepToken,
                    "rejectedCount",
                    record.rejectedStaleResults);
            } else {
                applyRunnerResult(record, result, now);
            }
        }
        drainDeferredCallbacks();
        synchronized (this) {
            return snapshotAt(readNow());
        }
    }

    /**
     * Applies a result delivered by an asynchronous runner boundary. Results from revoked epochs or
     * inactive tasks are rejected without changing authoritative task state.
     */
    public boolean acceptRunnerResult(String taskId, StepResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        boolean accepted;
        synchronized (this) {
            long now = readNow();
            TaskRecord record = requireTask(taskId);
            if (record.stepInFlight) {
                record.rejectedStaleResults++;
                accepted = false;
            } else {
                accepted = applyRunnerResult(record, result, now);
            }
        }
        drainDeferredCallbacks();
        return accepted;
    }

    private boolean applyRunnerResult(TaskRecord record, StepResult result, long now) {
        DevelopmentTrace.event(
            "task-controller",
            "apply-result",
            "task",
            record.spec.getId(),
            "state",
            record.state,
            "kind",
            result.getKind(),
            "activeEpoch",
            record.activeEpoch,
            "resultEpoch",
            result.getActionEpoch(),
            "detail",
            result.getDetail());
        long authoritativeEpoch = currentActionEpoch();
        if (isActive(record) && record.activeEpoch != authoritativeEpoch) {
            record.rejectedStaleResults++;
            blockAfterExternalRevocation(record, record.activeEpoch, authoritativeEpoch, "action authority changed");
            return false;
        }
        if (!isActive(record) || result.getActionEpoch() != record.activeEpoch
            || result.getActionEpoch() != authoritativeEpoch) {
            record.rejectedStaleResults++;
            record.detail = "rejected stale runner result for epoch " + result.getActionEpoch();
            return false;
        }
        if (result.getCheckpoint()
            .getRevision() < record.checkpoint.getRevision()) {
            finishFailure(record, "runner checkpoint revision moved backwards", false, now);
            return true;
        }

        record.checkpoint = result.getCheckpoint();
        record.detail = result.getDetail();
        switch (result.getKind()) {
            case PROGRESS:
                record.nextEligibleAtMillis = now;
                return true;
            case WAIT:
                StepResult.Wait wait = (StepResult.Wait) result;
                record.nextEligibleAtMillis = safeAdd(now, wait.getDelayMillis());
                return true;
            case SAFE_SUSPENSION:
                handleSafeSuspension(record, now);
                return true;
            case COMPLETED:
                finishTerminal(record, TaskState.COMPLETED, now);
                return true;
            case FAILED:
                StepResult.Failed failed = (StepResult.Failed) result;
                finishFailure(record, failed.getDetail(), failed.isRetryable(), now);
                return true;
            case BLOCKED:
                StepResult.Blocked blocked = (StepResult.Blocked) result;
                clearActive(record);
                record.controlVersion++;
                record.state = TaskState.BLOCKED;
                record.blockedReason = blocked.getReason();
                record.suspensionReason = TaskSuspensionReason.NONE;
                record.nextEligibleAtMillis = now;
                record.detail = blocked.getReason()
                    .getDetail();
                applyRevocationFailure(record, revokeActionAuthority(), now);
                return true;
            default:
                throw new IllegalStateException("unhandled step result: " + result.getKind());
        }
    }

    private void handleSafeSuspension(TaskRecord record, long now) {
        TaskSuspensionReason requested = record.suspensionReason;
        if (requested == TaskSuspensionReason.NONE) {
            record.state = TaskState.RUNNING;
            record.nextEligibleAtMillis = now;
            return;
        }

        long revokedEpoch = record.activeEpoch;
        clearActive(record);
        record.controlVersion++;
        record.nextEligibleAtMillis = now;
        if (requested == TaskSuspensionReason.CANCELLATION) {
            record.state = TaskState.CANCELLED;
            record.detail = "cancelled at a safe suspension point";
        } else {
            record.state = TaskState.SUSPENDED;
            record.detail = requested == TaskSuspensionReason.OPERATOR_PAUSE ? "paused at a safe suspension point"
                : "preempted at a safe suspension point";
        }

        EpochTransition transition = revokeActionAuthority();
        if (applyRevocationFailure(record, transition, now)) {
            return;
        }
        if (requested == TaskSuspensionReason.CANCELLATION) {
            enqueueInterruption(
                record,
                TaskInterruptionReason.CANCELLATION,
                revokedEpoch,
                transition.replacementEpoch,
                true,
                now);
        }
    }

    private void finishFailure(TaskRecord record, String detail, boolean retryable, long now) {
        clearActive(record);
        record.controlVersion++;
        record.detail = detail == null ? "" : detail;
        record.suspensionReason = TaskSuspensionReason.NONE;
        record.nextEligibleAtMillis = now;
        if (applyRevocationFailure(record, revokeActionAuthority(), now)) {
            return;
        }

        if (record.spec.getLane() == TaskLane.SAFETY) {
            record.state = TaskState.BLOCKED;
            record.blockedReason = new BlockedReason(
                BlockedCause.SAFETY_FAILURE,
                record.detail,
                record.spec.getId(),
                record.retryCount,
                "safe automatic continuation",
                "Resolve the safety condition, then resume the task manually.");
            return;
        }
        if (!retryable) {
            record.state = TaskState.FAILED;
            removeFromLane(record);
            return;
        }
        if (record.retryCount >= retryPolicy.getMaximumRetries()) {
            record.state = TaskState.BLOCKED;
            record.blockedReason = new BlockedReason(
                BlockedCause.RETRY_EXHAUSTED,
                record.detail,
                record.spec.getId(),
                record.retryCount,
                "successful task step",
                "Inspect the failure, correct it, then resume the task.");
            return;
        }

        long delay = retryPolicy.getDelayMillis(record.retryCount);
        record.retryCount++;
        record.nextEligibleAtMillis = safeAdd(now, delay);
        record.state = TaskState.QUEUED;
        record.blockedReason = null;
        enqueueRunnerBuild(record, "queued for retry " + record.retryCount);
    }

    private void finishTerminal(TaskRecord record, TaskState state, long now) {
        clearActive(record);
        record.controlVersion++;
        record.state = state;
        record.suspensionReason = TaskSuspensionReason.NONE;
        record.blockedReason = null;
        record.nextEligibleAtMillis = now;
        if (applyRevocationFailure(record, revokeActionAuthority(), now)) {
            return;
        }
        removeFromLane(record);
        traceRecord("terminal", record, "terminalState", state);
    }

    private void activate(TaskRecord record) {
        record.controlVersion++;
        record.state = TaskState.RUNNING;
        record.suspensionReason = TaskSuspensionReason.NONE;
        record.activeEpoch = currentActionEpoch();
        record.detail = "running";
        activeTaskId = record.spec.getId();
        traceRecord("activated", record, "checkpointRevision", record.checkpoint.getRevision());
    }

    private static void traceRecord(String event, TaskRecord record, Object... extraFields) {
        Object[] fields = new Object[12 + extraFields.length];
        fields[0] = "task";
        fields[1] = record.spec.getId();
        fields[2] = "type";
        fields[3] = record.spec.getType();
        fields[4] = "lane";
        fields[5] = record.spec.getLane();
        fields[6] = "state";
        fields[7] = record.state;
        fields[8] = "epoch";
        fields[9] = record.activeEpoch;
        fields[10] = "controlVersion";
        fields[11] = record.controlVersion;
        System.arraycopy(extraFields, 0, fields, 12, extraFields.length);
        DevelopmentTrace.event("task-controller", event, fields);
    }

    private void interruptForNewSafetyIfNeeded(TaskRecord candidate, long now) {
        if (candidate.spec.getLane() != TaskLane.SAFETY
            || (candidate.state != TaskState.QUEUED && !isUnresolvedSafetyRecord(candidate))) {
            return;
        }
        TaskRecord active = activeRecord();
        if (active != null && active.spec.getLane() != TaskLane.SAFETY) {
            interruptForSafety(active, now);
        }
    }

    private void interruptForSafety(TaskRecord record, long now) {
        long revokedEpoch = record.activeEpoch;
        clearActive(record);
        record.controlVersion++;
        record.nextEligibleAtMillis = now;
        record.blockedReason = null;

        if (record.suspensionReason == TaskSuspensionReason.CANCELLATION) {
            record.state = TaskState.CANCELLED;
            record.detail = "cancelled by an immediate safety interruption";
        } else if (record.suspensionReason == TaskSuspensionReason.OPERATOR_PAUSE) {
            record.state = TaskState.SUSPENDED;
            record.detail = "paused by an immediate safety interruption";
        } else {
            record.state = TaskState.SUSPENDED;
            record.suspensionReason = TaskSuspensionReason.PREEMPTION;
            record.detail = "immediately preempted by safety";
        }

        EpochTransition transition = revokeActionAuthority();
        if (applyRevocationFailure(record, transition, now)) {
            return;
        }
        enqueueInterruption(
            record,
            TaskInterruptionReason.SAFETY_PREEMPTION,
            revokedEpoch,
            transition.replacementEpoch,
            record.state == TaskState.CANCELLED,
            now);
    }

    private TaskRecord highestEligible(long now) {
        if (actionBroker.isSafetyLocked()) {
            return null;
        }
        boolean safetyHold = hasUnresolvedSafetyWork();
        for (TaskLane lane : TaskLane.values()) {
            if (safetyHold && lane != TaskLane.SAFETY) {
                return null;
            }
            for (String taskId : laneOrder.get(lane)) {
                TaskRecord record = tasks.get(taskId);
                if (isEligible(record, now)) {
                    return record;
                }
            }
        }
        return null;
    }

    private boolean hasUnresolvedSafetyWork() {
        for (String taskId : laneOrder.get(TaskLane.SAFETY)) {
            if (isUnresolvedSafetyRecord(tasks.get(taskId))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnresolvedSafetyRecord(TaskRecord record) {
        return record != null && record.spec.getLane() == TaskLane.SAFETY
            && ((record.state == TaskState.QUEUED && record.runner == null) || record.state == TaskState.BLOCKED
                || (record.state == TaskState.SUSPENDED
                    && record.suspensionReason == TaskSuspensionReason.OPERATOR_PAUSE));
    }

    private boolean isEligible(TaskRecord record, long now) {
        if (record == null || record.runner == null || record.stepInFlight || now < record.nextEligibleAtMillis) {
            return false;
        }
        return record.state == TaskState.QUEUED
            || (record.state == TaskState.SUSPENDED && record.suspensionReason == TaskSuspensionReason.PREEMPTION);
    }

    private boolean isActive(TaskRecord record) {
        return record != null && record.spec.getId()
            .equals(activeTaskId) && (record.state == TaskState.RUNNING || record.state == TaskState.SUSPENDING);
    }

    private TaskRecord activeRecord() {
        return activeTaskId == null ? null : tasks.get(activeTaskId);
    }

    private void clearActive(TaskRecord record) {
        if (isActive(record)) {
            activeTaskId = null;
        }
    }

    private void removeFromLane(TaskRecord record) {
        laneOrder.get(record.spec.getLane())
            .remove(record.spec.getId());
    }

    private void enqueueScheduledTask(ScheduledTaskRequest request, long now) {
        TaskSpec spec = request.getTask();
        if (tasks.containsKey(spec.getId())) {
            throw new IllegalStateException("scheduled task id already exists: " + spec.getId());
        }
        TaskRecord record = new TaskRecord(spec, TaskCheckpoint.empty(), now);
        record.sourceScheduleId = request.getScheduleId();
        tasks.put(spec.getId(), record);
        laneOrder.get(spec.getLane())
            .add(spec.getId());
        interruptForNewSafetyIfNeeded(record, now);
        enqueueRunnerBuild(
            record,
            request.isCatchUp() ? "runner ready after reconnect catch-up" : "scheduled runner ready");
    }

    private boolean isControllerIdle() {
        if (activeTaskId != null) {
            return false;
        }
        for (TaskLane lane : TaskLane.values()) {
            if (!laneOrder.get(lane)
                .isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Set<String> occupiedScheduleIds() {
        Set<String> occupied = new HashSet<>();
        for (TaskRecord record : tasks.values()) {
            if (!record.state.isTerminal() && record.sourceScheduleId != null) {
                occupied.add(record.sourceScheduleId);
            }
        }
        return occupied;
    }

    private TaskRecord restoreRecord(RestoredTaskSnapshot saved, long now) {
        TaskState restoredState = saved.getState();
        TaskSuspensionReason restoredReason = saved.getSuspensionReason();
        if (restoredState == TaskState.RUNNING) {
            restoredState = TaskState.QUEUED;
            restoredReason = TaskSuspensionReason.NONE;
        } else if (restoredState == TaskState.SUSPENDING) {
            if (restoredReason == TaskSuspensionReason.CANCELLATION) {
                restoredState = TaskState.CANCELLED;
            } else if (restoredReason == TaskSuspensionReason.OPERATOR_PAUSE) {
                restoredState = TaskState.SUSPENDED;
            } else {
                restoredState = TaskState.QUEUED;
                restoredReason = TaskSuspensionReason.NONE;
            }
        } else if (restoredState == TaskState.SUSPENDED && restoredReason == TaskSuspensionReason.PREEMPTION) {
            restoredState = TaskState.QUEUED;
            restoredReason = TaskSuspensionReason.NONE;
        }

        TaskRecord record = new TaskRecord(saved.getSpec(), saved.getCheckpoint(), now);
        record.state = restoredState;
        record.retryCount = saved.getRetryCount();
        record.nextEligibleAtMillis = safeAdd(now, saved.getRemainingDelayMillis());
        record.suspensionReason = restoredReason;
        record.blockedReason = saved.getBlockedReason()
            .orElse(null);
        record.rejectedStaleResults = saved.getRejectedStaleResults();
        record.detail = saved.getDetail();
        record.sourceScheduleId = saved.getSourceScheduleId()
            .orElse(null);
        return record;
    }

    private TaskRunner createRunner(TaskSpec spec, TaskCheckpoint checkpoint) {
        TaskRunner runner = runnerFactory.create(spec, checkpoint);
        if (runner == null) {
            throw new IllegalStateException("runnerFactory returned null for " + spec.getId());
        }
        return runner;
    }

    private void enqueueRunnerBuild(TaskRecord record, String successDetail) {
        long buildToken = nextOperationToken();
        record.buildToken = buildToken;
        record.runner = null;
        record.detail = "constructing task runner";
        TaskSpec spec = record.spec;
        TaskCheckpoint checkpoint = record.checkpoint;
        deferredCallbacks.add(() -> {
            TaskRunner runner = null;
            RuntimeException failure = null;
            try {
                runner = createRunner(spec, checkpoint);
            } catch (RuntimeException factoryFailure) {
                failure = factoryFailure;
            }
            synchronized (TaskOrchestrator.this) {
                if (record.buildToken != buildToken) {
                    return;
                }
                record.buildToken = 0L;
                if (failure != null) {
                    record.controlVersion++;
                    record.state = TaskState.BLOCKED;
                    record.suspensionReason = TaskSuspensionReason.NONE;
                    record.detail = describeFailure(failure);
                    record.blockedReason = new BlockedReason(
                        BlockedCause.INVALID_CONFIGURATION,
                        record.detail,
                        record.spec.getId(),
                        record.retryCount,
                        "constructable task runner",
                        "Correct the task configuration, then resume it.");
                    return;
                }
                record.runner = runner;
                if (record.state == TaskState.QUEUED) {
                    record.detail = successDetail;
                }
            }
        });
    }

    private void enqueueInterruption(TaskRecord record, TaskInterruptionReason reason, long revokedEpoch,
        long replacementEpoch, boolean removeCancelledOnSuccess, long now) {
        TaskRunner runner = record.runner;
        long expectedControlVersion = record.controlVersion;
        deferredCallbacks.add(() -> {
            RuntimeException failure = null;
            try {
                runner.interrupt(new TaskInterruption(reason, revokedEpoch, replacementEpoch));
            } catch (RuntimeException interruptionFailure) {
                failure = interruptionFailure;
            }
            synchronized (TaskOrchestrator.this) {
                if (record.controlVersion != expectedControlVersion) {
                    return;
                }
                if (failure != null) {
                    record.controlVersion++;
                    markUnsafeBlock(record, "runner interruption failed: " + describeFailure(failure), now);
                } else if (removeCancelledOnSuccess && record.state == TaskState.CANCELLED) {
                    removeFromLane(record);
                }
            }
        });
    }

    private void drainDeferredCallbacks() {
        if (Thread.holdsLock(this)) {
            throw new IllegalStateException("deferred callbacks must not run under the orchestrator monitor");
        }
        while (true) {
            DeferredCallback callback;
            synchronized (this) {
                if (deferredCallbacks.isEmpty()) {
                    return;
                }
                callback = deferredCallbacks.remove(0);
            }
            callback.execute();
        }
    }

    private long nextOperationToken() {
        if (nextOperationToken == Long.MAX_VALUE) {
            throw new IllegalStateException("task operation token exhausted");
        }
        return nextOperationToken++;
    }

    private TaskRecord requireTask(String taskId) {
        if (taskId == null || taskId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        TaskRecord record = tasks.get(taskId.trim());
        if (record == null) {
            throw new IllegalArgumentException("unknown task: " + taskId.trim());
        }
        return record;
    }

    private static void requireSpec(TaskSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
    }

    private long readNow() {
        long now = clock.nowMillis();
        if (now < 0L) {
            throw new IllegalStateException("monotonic clock returned a negative value");
        }
        if (lastObservedMillis != Long.MIN_VALUE && now < lastObservedMillis) {
            throw new IllegalStateException("monotonic clock moved backwards");
        }
        lastObservedMillis = now;
        return now;
    }

    private long currentActionEpoch() {
        long epoch = actionBroker.currentEpoch();
        if (epoch < 1L) {
            throw new IllegalStateException("action broker returned a non-positive epoch");
        }
        return epoch;
    }

    private EpochTransition revokeActionAuthority() {
        long revokedEpoch = currentActionEpoch();
        RuntimeException listenerFailure = null;
        try {
            actionBroker.revokeAll();
        } catch (RuntimeException failure) {
            listenerFailure = failure;
        }
        long replacementEpoch = currentActionEpoch();
        if (replacementEpoch <= revokedEpoch) {
            IllegalStateException invariantFailure = new IllegalStateException(
                "action broker did not advance its epoch during revocation");
            if (listenerFailure != null) {
                invariantFailure.addSuppressed(listenerFailure);
            }
            throw invariantFailure;
        }
        if (replacementEpoch != revokedEpoch + 1L) {
            IllegalStateException concurrentRevocation = new IllegalStateException(
                "action authority changed concurrently during task revocation");
            if (listenerFailure != null) {
                concurrentRevocation.addSuppressed(listenerFailure);
            }
            listenerFailure = concurrentRevocation;
        }
        return new EpochTransition(revokedEpoch, replacementEpoch, listenerFailure);
    }

    private void advanceActionAuthorityPast(long previousEpoch) {
        try {
            actionBroker.advanceEpochPast(previousEpoch);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("could not establish fresh action authority during restore", failure);
        }
        if (currentActionEpoch() <= previousEpoch) {
            throw new IllegalStateException("action broker did not advance past the persisted epoch floor");
        }
    }

    private boolean applyRevocationFailure(TaskRecord record, EpochTransition transition, long now) {
        if (transition.listenerFailure == null) {
            return false;
        }
        markUnsafeBlock(
            record,
            "action revocation listener failed: " + describeFailure(transition.listenerFailure),
            now);
        return true;
    }

    private void markUnsafeBlock(TaskRecord record, String detail, long now) {
        record.state = TaskState.BLOCKED;
        record.suspensionReason = TaskSuspensionReason.NONE;
        record.nextEligibleAtMillis = now;
        record.detail = detail;
        BlockedCause cause = record.spec.getLane() == TaskLane.SAFETY ? BlockedCause.SAFETY_FAILURE
            : BlockedCause.UNSAFE_TO_CONTINUE;
        record.blockedReason = new BlockedReason(
            cause,
            detail,
            record.spec.getId(),
            record.retryCount,
            "successful synchronous action revocation",
            "Inspect the revocation failure, then resume the task manually.");
    }

    private void blockAfterExternalRevocation(TaskRecord record, long revokedEpoch, long replacementEpoch,
        String detail) {
        clearActive(record);
        record.controlVersion++;
        long now = lastObservedMillis == Long.MIN_VALUE ? 0L : lastObservedMillis;
        markExternalRevocationBlock(record, detail, now);
        enqueueInterruption(
            record,
            TaskInterruptionReason.ACTION_AUTHORITY_REVOCATION,
            revokedEpoch,
            replacementEpoch,
            false,
            now);
    }

    private void markExternalRevocationBlock(TaskRecord record, String detail, long now) {
        record.state = TaskState.BLOCKED;
        record.suspensionReason = TaskSuspensionReason.NONE;
        record.nextEligibleAtMillis = now;
        record.detail = detail;
        BlockedCause cause = record.spec.getLane() == TaskLane.SAFETY ? BlockedCause.SAFETY_FAILURE
            : BlockedCause.EXTERNAL_FAILURE;
        record.blockedReason = new BlockedReason(
            cause,
            detail,
            record.spec.getId(),
            record.retryCount,
            "valid task action authority",
            "Inspect the external revocation, then resume the task manually.");
    }

    private ControllerSnapshot snapshotAt(long now) {
        EnumMap<TaskLane, List<TaskSnapshot>> lanes = new EnumMap<>(TaskLane.class);
        Map<String, Integer> positions = queuePositions();

        Map<String, TaskSnapshot> snapshotsById = new LinkedHashMap<>();
        List<TaskSnapshot> all = new ArrayList<>(tasks.size());
        for (TaskRecord record : tasks.values()) {
            Integer position = positions.get(record.spec.getId());
            TaskSnapshot snapshot = record.snapshot(position == null ? -1 : position);
            snapshotsById.put(record.spec.getId(), snapshot);
            all.add(snapshot);
        }
        for (TaskLane lane : TaskLane.values()) {
            List<TaskSnapshot> laneSnapshots = new ArrayList<>();
            for (String id : laneOrder.get(lane)) {
                laneSnapshots.add(snapshotsById.get(id));
            }
            lanes.put(lane, laneSnapshots);
        }
        return new ControllerSnapshot(
            actionBroker.snapshot(),
            now,
            activeTaskId,
            new QueueSnapshot(lanes),
            all,
            scheduler.snapshot());
    }

    private Map<String, Integer> queuePositions() {
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (TaskLane lane : TaskLane.values()) {
            List<String> ids = laneOrder.get(lane);
            for (int index = 0; index < ids.size(); index++) {
                positions.put(ids.get(index), index);
            }
        }
        return positions;
    }

    private TaskSnapshot taskSnapshotAt(String taskId, long now) {
        Optional<TaskSnapshot> snapshot = snapshotAt(now).findTask(taskId);
        if (!snapshot.isPresent()) {
            throw new IllegalStateException("task disappeared while creating snapshot: " + taskId);
        }
        return snapshot.get();
    }

    private static long safeAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static String describeFailure(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass()
            .getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    @Override
    public void onActionEpochRevoked(ActionRevocation revocation) {
        if (revocation == null) {
            throw new IllegalArgumentException("revocation must not be null");
        }
        boolean outerMonitorHeld = Thread.holdsLock(this);
        synchronized (this) {
            TaskRecord active = activeRecord();
            if (active == null) {
                return;
            }
            long replacementEpoch = revocation.getNewEpoch();
            if (replacementEpoch <= active.activeEpoch) {
                throw new IllegalStateException("action broker revocation did not supersede the active task epoch");
            }
            blockAfterExternalRevocation(
                active,
                active.activeEpoch,
                replacementEpoch,
                "action authority revoked externally: " + revocation.getReason());
        }
        if (!outerMonitorHeld) {
            drainDeferredCallbacks();
        }
    }

    @Override
    public void close() {
        actionBroker.removeRevocationListener(this);
    }

    private static final class EpochTransition {

        private final long revokedEpoch;
        private final long replacementEpoch;
        private final RuntimeException listenerFailure;

        private EpochTransition(long revokedEpoch, long replacementEpoch, RuntimeException listenerFailure) {
            this.revokedEpoch = revokedEpoch;
            this.replacementEpoch = replacementEpoch;
            this.listenerFailure = listenerFailure;
        }
    }

    private interface DeferredCallback {

        void execute();
    }

    private static final class StepInvocation {

        private final TaskRecord record;
        private final TaskRunner runner;
        private final TaskStepContext context;
        private final long stepToken;
        private final long controlVersion;

        private StepInvocation(TaskRecord record, TaskRunner runner, TaskStepContext context, long stepToken,
            long controlVersion) {
            this.record = record;
            this.runner = runner;
            this.context = context;
            this.stepToken = stepToken;
            this.controlVersion = controlVersion;
        }
    }

    private final class BrokerTaskActionGateway implements TaskActionGateway {

        private final String taskId;
        private final long actionEpoch;
        private final long controlVersion;

        private BrokerTaskActionGateway(String taskId, long actionEpoch, long controlVersion) {
            this.taskId = taskId;
            this.actionEpoch = actionEpoch;
            this.controlVersion = controlVersion;
        }

        @Override
        public String getTaskId() {
            return taskId;
        }

        @Override
        public long getActionEpoch() {
            return actionEpoch;
        }

        @Override
        public boolean isAuthoritative() {
            if (actionBroker.currentEpoch() != actionEpoch) {
                return false;
            }
            synchronized (TaskOrchestrator.this) {
                TaskRecord record = tasks.get(taskId);
                return isActive(record) && record.activeEpoch == actionEpoch && record.controlVersion == controlVersion;
            }
        }

        @Override
        public Optional<ActionLease> tryAcquire(Set<ActionCapability> capabilities) {
            if (!isAuthoritative()) {
                return Optional.empty();
            }
            Optional<ActionLease> lease = actionBroker.tryAcquire("task:" + taskId, capabilities);
            if (!lease.isPresent()) {
                return lease;
            }
            ActionLease acquired = lease.get();
            if (acquired.getEpoch() != actionEpoch || !isAuthoritative()) {
                acquired.close();
                return Optional.empty();
            }
            return lease;
        }
    }

    private static final class TaskRecord {

        private TaskSpec spec;
        private TaskState state = TaskState.QUEUED;
        private TaskCheckpoint checkpoint;
        private TaskRunner runner;
        private long activeEpoch;
        private int retryCount;
        private long nextEligibleAtMillis;
        private TaskSuspensionReason suspensionReason = TaskSuspensionReason.NONE;
        private BlockedReason blockedReason;
        private long rejectedStaleResults;
        private long controlVersion;
        private long buildToken;
        private long stepToken;
        private boolean stepInFlight;
        private String detail = "queued";
        private String sourceScheduleId;

        private TaskRecord(TaskSpec spec, TaskCheckpoint checkpoint, long now) {
            this.spec = spec;
            this.checkpoint = checkpoint;
            this.nextEligibleAtMillis = now;
        }

        private TaskSnapshot snapshot(int queuePosition) {
            return new TaskSnapshot(
                spec,
                state,
                checkpoint,
                activeEpoch,
                retryCount,
                nextEligibleAtMillis,
                suspensionReason,
                blockedReason,
                queuePosition,
                rejectedStaleResults,
                detail);
        }
    }
}
