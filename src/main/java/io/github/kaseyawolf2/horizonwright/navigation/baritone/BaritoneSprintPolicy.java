package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import baritone.api.BaritoneAPI;

/** Keeps direct Baritone setting access inside the version-isolated adapter package. */
public final class BaritoneSprintPolicy {

    private BaritoneSprintPolicy() {}

    public static boolean isSprintAllowed() {
        return BaritoneAPI.getSettings().allowSprint.value;
    }
}
