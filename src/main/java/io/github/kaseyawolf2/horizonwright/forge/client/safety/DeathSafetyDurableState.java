package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;

/** Durable unresolved-death boundary implemented by the profile persistence runtime. */
public interface DeathSafetyDurableState {

    void persistUnresolvedDeath(DeathSafetySnapshot snapshot);

    void clearResolvedDeath();
}
