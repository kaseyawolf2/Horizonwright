package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.action.ActionBrokerSnapshot;

/** Immutable controller state suitable for GUIs, commands, and persistence adapters. */
public final class ControllerSnapshot {

    private final long actionEpoch;
    private final ActionBrokerSnapshot actionAuthority;
    private final long observedAtMillis;
    private final String activeTaskId;
    private final QueueSnapshot queue;
    private final List<TaskSnapshot> tasks;
    private final SchedulerSnapshot scheduler;

    ControllerSnapshot(ActionBrokerSnapshot actionAuthority, long observedAtMillis, String activeTaskId,
        QueueSnapshot queue, List<TaskSnapshot> tasks, SchedulerSnapshot scheduler) {
        if (actionAuthority == null) {
            throw new IllegalArgumentException("actionAuthority must not be null");
        }
        this.actionEpoch = actionAuthority.getEpoch();
        this.actionAuthority = actionAuthority;
        this.observedAtMillis = observedAtMillis;
        this.activeTaskId = activeTaskId;
        this.queue = queue;
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        this.scheduler = scheduler;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public ActionBrokerSnapshot getActionAuthority() {
        return actionAuthority;
    }

    public long getObservedAtMillis() {
        return observedAtMillis;
    }

    public Optional<String> getActiveTaskId() {
        return Optional.ofNullable(activeTaskId);
    }

    public QueueSnapshot getQueue() {
        return queue;
    }

    public List<TaskSnapshot> getTasks() {
        return tasks;
    }

    public SchedulerSnapshot getScheduler() {
        return scheduler;
    }

    public Optional<TaskSnapshot> findTask(String taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        for (TaskSnapshot task : tasks) {
            if (task.getSpec()
                .getId()
                .equals(taskId)) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }
}
