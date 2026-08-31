package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.Set;

public interface ActionLease extends AutoCloseable {

    String getOwner();

    long getEpoch();

    Set<ActionCapability> getCapabilities();

    boolean isValid();

    default boolean isSafetyRecoveryLease() {
        return false;
    }

    @Override
    void close();
}
