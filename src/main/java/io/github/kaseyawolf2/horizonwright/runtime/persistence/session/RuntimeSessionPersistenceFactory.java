package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Creates a persistence boundary permanently partitioned to the selected profile/world. */
@FunctionalInterface
public interface RuntimeSessionPersistenceFactory {

    RuntimeSessionPersistence create(WorldProfileIdentity identity);
}
