package io.github.kaseyawolf2.horizonwright.forge.client.network;

import java.util.Optional;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationAttempt;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.GraveActivationPacketWriteGate;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.InboundLethalHealthHook;
import io.github.kaseyawolf2.horizonwright.forge.client.safety.RespawnPacketWriteGate;

/** Direct adapter from the Netty boundary contract to the death-safety controller gates. */
public final class GateBackedDeathSafetyPacketBridge implements DeathSafetyPacketBridge {

    private final InboundLethalHealthHook lethalHealthHook;
    private final RespawnPacketWriteGate respawnWriteGate;
    private final GraveActivationPacketWriteGate graveActivationWriteGate;
    private final DeathSafetyPacketContext context;

    public GateBackedDeathSafetyPacketBridge(InboundLethalHealthHook lethalHealthHook,
        RespawnPacketWriteGate respawnWriteGate, GraveActivationPacketWriteGate graveActivationWriteGate,
        DeathSafetyPacketContext context) {
        if (lethalHealthHook == null || respawnWriteGate == null
            || graveActivationWriteGate == null
            || context == null) {
            throw new IllegalArgumentException("death-safety bridge dependencies must not be null");
        }
        this.lethalHealthHook = lethalHealthHook;
        this.respawnWriteGate = respawnWriteGate;
        this.graveActivationWriteGate = graveActivationWriteGate;
        this.context = context;
    }

    @Override
    public void beforeLethalHealthPacket(double health) {
        lethalHealthHook.beforeS06HealthPacketQueued(health, context.getMaximumHealth(), context.getClientTick());
    }

    @Override
    public boolean tryAuthorizeRespawnPacket(Runnable finalWriteContinuation) {
        return respawnWriteGate
            .tryWrite(context.getActiveDeathEpoch(), context.getClientTick(), finalWriteContinuation);
    }

    @Override
    public GraveActivationWriteDecision tryAuthorizeGraveActivationPacket(C08PacketPlayerBlockPlacement packet,
        Runnable finalWriteContinuation) {
        Optional<GraveActivationAttempt> attempt = context.matchGraveActivation(packet);
        if (!attempt.isPresent()) {
            return GraveActivationWriteDecision.NOT_APPLICABLE;
        }
        return graveActivationWriteGate.tryWrite(attempt.get(), context.getClientTick(), finalWriteContinuation)
            ? GraveActivationWriteDecision.AUTHORIZED
            : GraveActivationWriteDecision.REJECTED;
    }

    @Override
    public void onBoundaryUnavailable(boolean transportClosed) {
        context.onBoundaryUnavailable(transportClosed);
    }
}
