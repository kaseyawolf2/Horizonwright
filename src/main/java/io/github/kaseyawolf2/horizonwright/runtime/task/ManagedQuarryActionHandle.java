package io.github.kaseyawolf2.horizonwright.runtime.task;

/** One bounded infrastructure action submitted to an excavation backend. */
public interface ManagedQuarryActionHandle {

    String getRequestId();

    ManagedQuarryActionProgress progress();

    void cancel();
}
