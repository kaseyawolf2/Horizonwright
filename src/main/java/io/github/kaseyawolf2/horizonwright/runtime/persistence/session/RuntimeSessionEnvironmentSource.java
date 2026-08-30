package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;

/** Supplies the current connected-world scheduler environment at each admitted session tick. */
@FunctionalInterface
public interface RuntimeSessionEnvironmentSource {

    ScheduleEnvironment snapshot(RuntimeSessionConnection connection);
}
