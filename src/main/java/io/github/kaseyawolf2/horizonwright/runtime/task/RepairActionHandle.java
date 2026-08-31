package io.github.kaseyawolf2.horizonwright.runtime.task;

public interface RepairActionHandle {

    String getRequestId();

    RepairActionProgress progress();

    void cancel();
}
