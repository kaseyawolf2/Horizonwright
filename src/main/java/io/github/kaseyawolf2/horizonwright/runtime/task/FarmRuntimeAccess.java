package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Session-owned access to the optional live farm backend. */
public interface FarmRuntimeAccess {

    FarmBackend getFarmBackend();

    /** Optional ordinary-tree adapter sharing the named farm-area service. */
    default TreeBackend getTreeBackend() {
        return null;
    }

    boolean isDryRun();
}
