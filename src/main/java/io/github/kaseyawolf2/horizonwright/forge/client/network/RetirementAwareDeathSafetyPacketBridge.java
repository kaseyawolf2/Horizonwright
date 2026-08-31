package io.github.kaseyawolf2.horizonwright.forge.client.network;

import java.util.function.BooleanSupplier;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

/** Stops applying an old connection's integrated gates after its runtime boundary has retired. */
public final class RetirementAwareDeathSafetyPacketBridge implements DeathSafetyPacketBridge {

    private final DeathSafetyPacketBridge delegate;
    private final BooleanSupplier active;

    public RetirementAwareDeathSafetyPacketBridge(DeathSafetyPacketBridge delegate, BooleanSupplier active) {
        if (delegate == null || active == null) {
            throw new IllegalArgumentException("delegate and active state must not be null");
        }
        this.delegate = delegate;
        this.active = active;
    }

    @Override
    public void beforeLethalHealthPacket(double health) {
        if (active.getAsBoolean()) {
            delegate.beforeLethalHealthPacket(health);
        }
    }

    @Override
    public boolean tryAuthorizeRespawnPacket(Runnable finalWriteContinuation) {
        if (finalWriteContinuation == null) {
            throw new IllegalArgumentException("final write continuation must not be null");
        }
        if (active.getAsBoolean()) {
            return delegate.tryAuthorizeRespawnPacket(finalWriteContinuation);
        }
        finalWriteContinuation.run();
        return true;
    }

    @Override
    public GraveActivationWriteDecision tryAuthorizeGraveActivationPacket(C08PacketPlayerBlockPlacement packet,
        Runnable finalWriteContinuation) {
        if (packet == null || finalWriteContinuation == null) {
            throw new IllegalArgumentException("packet and final write continuation must not be null");
        }
        return active.getAsBoolean() ? delegate.tryAuthorizeGraveActivationPacket(packet, finalWriteContinuation)
            : GraveActivationWriteDecision.NOT_APPLICABLE;
    }

    @Override
    public GravePreparationWriteDecision tryAuthorizeGravePreparationPacket(Object packet,
        Runnable finalWriteContinuation) {
        if (packet == null || finalWriteContinuation == null) {
            throw new IllegalArgumentException("packet and final write continuation must not be null");
        }
        return active.getAsBoolean() ? delegate.tryAuthorizeGravePreparationPacket(packet, finalWriteContinuation)
            : GravePreparationWriteDecision.NOT_APPLICABLE;
    }

    @Override
    public void onBoundaryUnavailable(boolean transportClosed) {
        delegate.onBoundaryUnavailable(transportClosed);
    }
}
