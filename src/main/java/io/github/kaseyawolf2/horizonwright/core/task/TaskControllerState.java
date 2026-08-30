package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable aggregate that persistence adapters can encode without core depending on a format. */
public final class TaskControllerState {

    private final long lastActionEpoch;
    private final List<RestoredTaskSnapshot> tasks;
    private final SchedulerSnapshot scheduler;

    public TaskControllerState(List<RestoredTaskSnapshot> tasks, SchedulerSnapshot scheduler) {
        this(0L, tasks, scheduler);
    }

    public TaskControllerState(long lastActionEpoch, List<RestoredTaskSnapshot> tasks, SchedulerSnapshot scheduler) {
        if (lastActionEpoch < 0L || lastActionEpoch >= Long.MAX_VALUE - 1L) {
            throw new IllegalArgumentException(
                "lastActionEpoch must be non-negative and leave a subsequently advanceable epoch");
        }
        if (tasks == null) {
            throw new IllegalArgumentException("tasks must not be null");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        List<RestoredTaskSnapshot> copy = new ArrayList<>(tasks.size());
        Set<String> taskIds = new HashSet<>();
        EnumMap<TaskLane, Set<Integer>> positions = new EnumMap<>(TaskLane.class);
        EnumMap<TaskLane, Integer> counts = new EnumMap<>(TaskLane.class);
        for (TaskLane lane : TaskLane.values()) {
            positions.put(lane, new HashSet<Integer>());
            counts.put(lane, 0);
        }
        int activeTasks = 0;
        for (RestoredTaskSnapshot task : tasks) {
            if (task == null) {
                throw new IllegalArgumentException("tasks must not contain null values");
            }
            String taskId = task.getSpec()
                .getId();
            if (!taskIds.add(taskId)) {
                throw new IllegalArgumentException("duplicate task: " + taskId);
            }
            if (!task.getState()
                .isTerminal()) {
                TaskLane lane = task.getSpec()
                    .getLane();
                if (!positions.get(lane)
                    .add(task.getQueuePosition())) {
                    throw new IllegalArgumentException("duplicate queue position in " + lane + " lane");
                }
                counts.put(lane, counts.get(lane) + 1);
            }
            if (task.getState() == TaskState.RUNNING || task.getState() == TaskState.SUSPENDING) {
                activeTasks++;
            }
            if (task.getSourceScheduleId()
                .isPresent()
                && !scheduler.findSchedule(
                    task.getSourceScheduleId()
                        .get())
                    .isPresent()) {
                throw new IllegalArgumentException(
                    "task references unknown schedule: " + task.getSourceScheduleId()
                        .get());
            }
            copy.add(task);
        }
        if (activeTasks > 1) {
            throw new IllegalArgumentException("state contains more than one active task");
        }
        for (TaskLane lane : TaskLane.values()) {
            int count = counts.get(lane);
            for (int position = 0; position < count; position++) {
                if (!positions.get(lane)
                    .contains(position)) {
                    throw new IllegalArgumentException("queue positions are not contiguous in " + lane + " lane");
                }
            }
        }
        this.lastActionEpoch = lastActionEpoch;
        this.tasks = Collections.unmodifiableList(copy);
        this.scheduler = scheduler;
    }

    public static TaskControllerState empty() {
        return new TaskControllerState(Collections.<RestoredTaskSnapshot>emptyList(), SchedulerSnapshot.empty());
    }

    public List<RestoredTaskSnapshot> getTasks() {
        return tasks;
    }

    /** Epoch floor used to reject results or leases retained from the exported controller. */
    public long getLastActionEpoch() {
        return lastActionEpoch;
    }

    public SchedulerSnapshot getScheduler() {
        return scheduler;
    }
}
