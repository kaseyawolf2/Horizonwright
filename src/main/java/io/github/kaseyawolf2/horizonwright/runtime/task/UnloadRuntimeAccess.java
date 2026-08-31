package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Narrow runtime services used by verified unload task runners. */
public interface UnloadRuntimeAccess {

    UnloadBackend getUnloadBackend();

    boolean isDryRun();
}
