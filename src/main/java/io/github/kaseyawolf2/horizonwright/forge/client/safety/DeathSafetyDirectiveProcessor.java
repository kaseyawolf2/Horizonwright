package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyUpdate;

/** Serializes durable state before any packet, navigation, or UI effect represented by an update. */
public final class DeathSafetyDirectiveProcessor {

    private final DeathSafetyDurableState durableState;

    public DeathSafetyDirectiveProcessor(DeathSafetyDurableState durableState) {
        if (durableState == null) {
            throw new IllegalArgumentException("durableState must not be null");
        }
        this.durableState = durableState;
    }

    public synchronized void process(DeathSafetyUpdate update, DeathSafetyDirectiveEffect effect) {
        if (update == null || effect == null) {
            throw new IllegalArgumentException("update and effect must not be null");
        }
        Set<DeathSafetyDirective> directives = update.getDirectives();
        if (directives.contains(DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH)) {
            durableState.persistUnresolvedDeath(update.getSnapshot());
        }
        if (directives.contains(DeathSafetyDirective.CLEAR_UNRESOLVED_DEATH)) {
            durableState.clearResolvedDeath();
        }
        for (DeathSafetyDirective directive : DeathSafetyDirective.values()) {
            if (!directives.contains(directive) || directive == DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH
                || directive == DeathSafetyDirective.CLEAR_UNRESOLVED_DEATH) {
                continue;
            }
            effect.apply(directive, update.getSnapshot());
        }
    }
}
