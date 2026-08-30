package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import baritone.api.BaritoneAPI;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;

/** Loaded reflectively only after the installation probe has accepted Baritone. */
public final class BaritoneNavigationBackendFactory {

    private BaritoneNavigationBackendFactory() {}

    public static NavigationBackend create(ActionSessionGuard actionSessionGuard) {
        return new BaritoneNavigationBackend(
            BaritoneAPI.getProvider()
                .getPrimaryBaritone(),
            actionSessionGuard);
    }
}
