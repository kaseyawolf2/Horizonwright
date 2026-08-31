package io.github.kaseyawolf2.horizonwright.runtime.task;

public interface HusbandryRuntimeAccess {

    HusbandryBackend getHusbandryBackend();

    boolean isDryRun();
}
