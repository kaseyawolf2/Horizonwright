package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Narrow runtime services used by excavation task runners. */
public interface ExcavationRuntimeAccess {

    ExcavationBackend getExcavationBackend();

    boolean isDryRun();
}
