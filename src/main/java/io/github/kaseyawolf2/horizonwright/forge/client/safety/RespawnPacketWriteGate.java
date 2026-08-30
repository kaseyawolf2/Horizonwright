package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyUpdate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;

/** Exactly-once respawn authorization consumed at the final outbound packet-write boundary. */
public final class RespawnPacketWriteGate {

    private final DeathSafetyController controller;
    private final ConnectionSafetyEventStampSource stamps;
    private final DeathSafetyDirectiveProcessor directives;

    public RespawnPacketWriteGate(DeathSafetyController controller, ConnectionSafetyEventStampSource stamps,
        DeathSafetyDirectiveProcessor directives) {
        if (controller == null || stamps == null || directives == null) {
            throw new IllegalArgumentException("respawn write gate dependencies must not be null");
        }
        this.controller = controller;
        this.stamps = stamps;
        this.directives = directives;
    }

    public boolean tryWrite(long deathEpoch, long clientTick, final Runnable packetWrite) {
        if (packetWrite == null) {
            throw new IllegalArgumentException("packetWrite must not be null");
        }
        if (!stamps.isOpen()) {
            return false;
        }
        final boolean[] written = { false };
        SafetyEventStamp stamp;
        try {
            stamp = stamps.next(clientTick);
        } catch (IllegalStateException retiredConnection) {
            return false;
        }
        DeathSafetyUpdate update = controller.authorizeRespawnPacket(stamp, deathEpoch);
        directives.process(update, new DeathSafetyDirectiveEffect() {

            @Override
            public void apply(DeathSafetyDirective directive, DeathSafetySnapshot snapshot) {
                if (directive == DeathSafetyDirective.SEND_EXACTLY_ONE_RESPAWN) {
                    written[0] = stamps.runIfOpen(packetWrite);
                }
            }
        });
        return written[0];
    }
}
