package io.github.kaseyawolf2.horizonwright.core.task;

public enum TaskState {

    QUEUED,
    RUNNING,
    SUSPENDING,
    SUSPENDED,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
