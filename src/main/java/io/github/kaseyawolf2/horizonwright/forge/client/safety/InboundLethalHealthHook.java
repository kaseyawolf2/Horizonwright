package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathContext;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyUpdate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;

/** Decision hook invoked in the inbound S06 health-packet call stack before vanilla queues that packet. */
public final class InboundLethalHealthHook {

    private final DeathSafetyController controller;
    private final ConnectionSafetyEventStampSource stamps;
    private final ClientDeathContextPublisher contextPublisher;
    private final DeathSafetyDirectiveProcessor directives;
    private final DeathSafetyDirectiveEffect effects;

    public InboundLethalHealthHook(DeathSafetyController controller, ConnectionSafetyEventStampSource stamps,
        ClientDeathContextPublisher contextPublisher, DeathSafetyDirectiveProcessor directives,
        DeathSafetyDirectiveEffect effects) {
        if (controller == null || stamps == null || contextPublisher == null || directives == null || effects == null) {
            throw new IllegalArgumentException("inbound health hook dependencies must not be null");
        }
        this.controller = controller;
        this.stamps = stamps;
        this.contextPublisher = contextPublisher;
        this.directives = directives;
        this.effects = effects;
    }

    public DeathSafetyUpdate beforeS06HealthPacketQueued(double health, double maximumHealth, long clientTick) {
        if (!Double.isFinite(health) || !Double.isFinite(maximumHealth) || maximumHealth <= 0.0D) {
            throw new IllegalArgumentException("health values must be finite and maximumHealth positive");
        }
        SafetyEventStamp stamp = stamps.next(clientTick);
        DeathContext context = null;
        if (health <= 0.0D) {
            ClientDeathContextSnapshot snapshot = contextPublisher.latestFor(stamps.getConnectionEpoch())
                .orElse(null);
            if (snapshot == null || snapshot.getCapturedAtClientTick() > clientTick) {
                DeathSafetyUpdate update = controller.onLethalHealthWithoutContext(stamp);
                directives.process(update, effects);
                return update;
            }
            context = snapshot.toDeathContext();
        }
        DeathSafetyUpdate update = controller.onHealthObservation(stamp, health, maximumHealth, context);
        directives.process(update, effects);
        return update;
    }
}
