package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.Optional;
import java.util.Set;

public interface ActionBroker {

    Optional<ActionLease> tryAcquire(String owner, Set<ActionCapability> capabilities);

    long currentEpoch();

    boolean isSafetyLocked();

    ActionBrokerSnapshot snapshot();

    void addRevocationListener(ActionRevocationListener listener);

    void removeRevocationListener(ActionRevocationListener listener);

    void revokeAll();

    void enterSafetyLockdown();

    void leaveSafetyLockdown();
}
