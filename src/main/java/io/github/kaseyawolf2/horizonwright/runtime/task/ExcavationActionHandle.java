package io.github.kaseyawolf2.horizonwright.runtime.task;

/** One bounded action submitted to a typed excavation backend. */
public interface ExcavationActionHandle {

    String getRequestId();

    ExcavationActionProgress progress();

    void cancel();
}
