package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;

/** Narrow runtime services used by navigation task runners. */
public interface NavigationRuntimeAccess {

    NavigationBackend getNavigationBackend();

    boolean isDryRun();

    void publishNavigationProgress(NavigationProgress progress);
}
