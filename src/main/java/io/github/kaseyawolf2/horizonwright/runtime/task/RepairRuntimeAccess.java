package io.github.kaseyawolf2.horizonwright.runtime.task;

public interface RepairRuntimeAccess {

    RepairBackend getRepairBackend();

    boolean isDryRun();
}
