package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

/** Creates an isolated death-state boundary for each fresh runtime connection. */
@FunctionalInterface
public interface RuntimeSessionDeathStateBoundaryFactory {

    RuntimeSessionDeathStateBoundary create(RuntimeSessionConnection connection);
}
