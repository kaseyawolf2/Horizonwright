package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;

/** Live client values read exactly once while creating an immutable death-context snapshot. */
public interface ClientDeathContextSource {

    DimensionBlockPosition getPlayerPosition();

    String getPlayerIdentity();

    String getActiveTaskId();

    ClientInventorySnapshot getInventorySnapshot();
}
