package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

/**
 * Per-connection death-safety authority used at the actual Netty packet boundary.
 *
 * <p>
 * The write continuations are one-shot and must be invoked synchronously. Invoking a continuation records
 * authorization; the firewall performs the actual {@code ChannelHandlerContext.write} only after the bridge returns
 * a matching decision.
 * </p>
 */
public interface DeathSafetyPacketBridge {

    enum GraveActivationWriteDecision {
        NOT_APPLICABLE,
        AUTHORIZED,
        REJECTED
    }

    /** Called in the inbound S06 call stack before the packet is forwarded to {@code packet_handler}. */
    void beforeLethalHealthPacket(double health);

    /** Authorizes the one exact C16 PERFORM_RESPAWN write for the active death epoch. */
    boolean tryAuthorizeRespawnPacket(Runnable finalWriteContinuation);

    /**
     * Recognizes and authorizes an exact grave activation. Ordinary C08 packets return {@link
     * GraveActivationWriteDecision#NOT_APPLICABLE} and continue through the generic action firewall.
     */
    GraveActivationWriteDecision tryAuthorizeGraveActivationPacket(C08PacketPlayerBlockPlacement packet,
        Runnable finalWriteContinuation);

    /** Retires this connection-scoped authority when its packet boundary becomes unavailable. */
    void onBoundaryUnavailable(boolean transportClosed);
}
