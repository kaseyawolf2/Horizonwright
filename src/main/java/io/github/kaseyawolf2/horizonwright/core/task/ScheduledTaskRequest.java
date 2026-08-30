package io.github.kaseyawolf2.horizonwright.core.task;

/** Package-private hand-off from the pure scheduler to the orchestrator queue. */
final class ScheduledTaskRequest {

    private final String scheduleId;
    private final TaskSpec task;
    private final boolean catchUp;
    private final int relativeOrder;

    ScheduledTaskRequest(String scheduleId, TaskSpec task, boolean catchUp, int relativeOrder) {
        this.scheduleId = scheduleId;
        this.task = task;
        this.catchUp = catchUp;
        this.relativeOrder = relativeOrder;
    }

    String getScheduleId() {
        return scheduleId;
    }

    TaskSpec getTask() {
        return task;
    }

    boolean isCatchUp() {
        return catchUp;
    }

    int getRelativeOrder() {
        return relativeOrder;
    }
}
