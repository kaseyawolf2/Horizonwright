package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;

/**
 * Post-persistence client effect for every non-durability directive.
 *
 * <p>
 * Some directives also describe work begun by the synchronous interlock. Implementations must make those operations
 * idempotent and use this explicit boundary to finish or audit the requested work; no directive is silently assumed
 * complete.
 */
public interface DeathSafetyDirectiveEffect {

    void apply(DeathSafetyDirective directive, DeathSafetySnapshot snapshot);
}
