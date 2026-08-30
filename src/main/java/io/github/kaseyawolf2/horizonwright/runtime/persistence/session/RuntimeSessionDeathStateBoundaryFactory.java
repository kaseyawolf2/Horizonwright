package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;

/** Creates an isolated death-state boundary for each fresh runtime connection. */
@FunctionalInterface
public interface RuntimeSessionDeathStateBoundaryFactory {

    RuntimeSessionDeathStateBoundary create(HorizonwrightRuntime runtime, RuntimeSessionConnection connection);
}
