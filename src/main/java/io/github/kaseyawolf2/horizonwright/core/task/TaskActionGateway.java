package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

/** Task-bound, epoch-checked boundary for requesting typed gameplay capabilities. */
public interface TaskActionGateway {

    String getTaskId();

    long getActionEpoch();

    boolean isAuthoritative();

    Optional<ActionLease> tryAcquire(Set<ActionCapability> capabilities);
}
