package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Optional;

/** Typed task-control boundary used by UI, commands, and integrations. */
public interface IHorizonwrightController {

    TaskSnapshot submit(TaskSpec spec);

    TaskSnapshot restore(TaskSpec spec, TaskCheckpoint checkpoint);

    TaskSnapshot update(TaskSpec replacement);

    TaskSnapshot pause(String taskId);

    TaskSnapshot resume(String taskId);

    default TaskSnapshot retryNow(String taskId) {
        throw new UnsupportedOperationException("Immediate retry is unavailable.");
    }

    TaskSnapshot cancel(String taskId);

    /** Permanently removes a task that is not currently executing or draining an action. */
    TaskSnapshot remove(String taskId);

    TaskSnapshot reorder(String taskId, int targetPosition);

    Optional<TaskSnapshot> inspect(String taskId);

    ScheduleSnapshot submitSchedule(ScheduleRule rule);

    ScheduleSnapshot updateSchedule(ScheduleRule replacement);

    ScheduleSnapshot pauseSchedule(String scheduleId);

    ScheduleSnapshot resumeSchedule(String scheduleId);

    default ScheduleSnapshot runScheduleNow(String scheduleId, ScheduleEnvironment environment) {
        throw new UnsupportedOperationException("Immediate schedules are unavailable.");
    }

    ScheduleSnapshot cancelSchedule(String scheduleId);

    /** Permanently removes a schedule while leaving already-created task history intact. */
    ScheduleSnapshot removeSchedule(String scheduleId);

    Optional<ScheduleSnapshot> inspectSchedule(String scheduleId);

    TaskControllerState exportState();

    ControllerSnapshot restoreState(TaskControllerState state);

    ControllerSnapshot snapshot();

    ControllerSnapshot tick();

    ControllerSnapshot tick(ScheduleEnvironment environment);
}
