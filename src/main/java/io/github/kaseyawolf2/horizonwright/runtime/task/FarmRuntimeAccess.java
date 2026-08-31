package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Session-owned access to the optional live farm backend. */
public interface FarmRuntimeAccess {

    FarmBackend getFarmBackend();

    boolean isDryRun();
}
