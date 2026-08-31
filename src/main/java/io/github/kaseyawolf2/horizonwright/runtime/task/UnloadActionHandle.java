package io.github.kaseyawolf2.horizonwright.runtime.task;

/** One bounded unload transaction executing through the live confirmed-click boundary. */
public interface UnloadActionHandle {

    String getRequestId();

    UnloadActionProgress progress();

    void cancel();
}
