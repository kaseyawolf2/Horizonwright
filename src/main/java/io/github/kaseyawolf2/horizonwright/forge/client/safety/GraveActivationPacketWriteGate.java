package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationAttempt;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationDecision;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationResult;
import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;

/** Exact-grave one-shot authorization consumed at the final outbound use-packet boundary. */
public final class GraveActivationPacketWriteGate {

    private final DeathSafetyController controller;
    private final ConnectionSafetyEventStampSource stamps;
    private final DeathSafetyDirectiveProcessor directives;
    private final GraveActivationReplayBlock replayBlock;

    public GraveActivationPacketWriteGate(DeathSafetyController controller, ConnectionSafetyEventStampSource stamps,
        DeathSafetyDirectiveProcessor directives, GraveActivationReplayBlock replayBlock) {
        if (controller == null || stamps == null || directives == null || replayBlock == null) {
            throw new IllegalArgumentException("grave activation write gate dependencies must not be null");
        }
        this.controller = controller;
        this.stamps = stamps;
        this.directives = directives;
        this.replayBlock = replayBlock;
    }

    public boolean tryWrite(final GraveActivationAttempt attempt, long clientTick, final Runnable packetWrite) {
        if (attempt == null || packetWrite == null) {
            throw new IllegalArgumentException("attempt and packetWrite must not be null");
        }
        if (replayBlock.isBlocked() || !stamps.isOpen()) {
            return false;
        }
        final boolean[] written = { false };
        SafetyEventStamp stamp;
        try {
            stamp = stamps.next(clientTick);
        } catch (IllegalStateException retiredConnection) {
            return false;
        }
        GraveActivationResult result = controller.authorizeGraveActivation(stamp, attempt);
        if (result.getDecision() != GraveActivationDecision.AUTHORIZED_AND_CONSUMED) {
            return false;
        }
        directives.process(result.getUpdate(), new DeathSafetyDirectiveEffect() {

            @Override
            public void apply(DeathSafetyDirective directive, DeathSafetySnapshot snapshot) {
                if (directive == DeathSafetyDirective.AUTHORIZE_EXACT_GRAVE_ACTIVATION) {
                    written[0] = stamps.runIfOpen(packetWrite);
                }
            }
        });
        return written[0];
    }
}
