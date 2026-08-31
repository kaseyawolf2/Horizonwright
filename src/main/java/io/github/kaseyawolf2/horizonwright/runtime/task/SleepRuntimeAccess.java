package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Session-owned access to the optional live sleep backend. */
public interface SleepRuntimeAccess {

    SleepBackend getSleepBackend();

    boolean isDryRun();
}
