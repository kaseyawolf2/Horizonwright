package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceException;

/** Durable load/save boundary permanently partitioned to one profile and world identity. */
public interface RuntimeSessionPersistence {

    WorldProfileIdentity getExpectedIdentity();

    RuntimeEnvelope load() throws TaskControllerPersistenceException;

    RuntimeEnvelope save(long writtenAtEpochMillis, RuntimeSessionConnection connection, RuntimeSessionRuntime runtime)
        throws TaskControllerPersistenceException;
}
