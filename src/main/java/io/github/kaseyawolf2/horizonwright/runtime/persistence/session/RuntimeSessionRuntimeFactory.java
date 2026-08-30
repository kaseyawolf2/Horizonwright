package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

/** Creates a new runtime only after the durable profile binding has been validated. */
@FunctionalInterface
public interface RuntimeSessionRuntimeFactory {

    RuntimeSessionRuntime create(RuntimeSessionConnection connection);
}
