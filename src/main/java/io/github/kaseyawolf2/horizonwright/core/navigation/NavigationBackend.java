package io.github.kaseyawolf2.horizonwright.core.navigation;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

public interface NavigationBackend {

    BackendAvailability availability();

    NavigationHandle submit(NavigationRequest request, ActionLease movementLease);
}
