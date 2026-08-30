package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S06PacketUpdateHealth;

import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.core.action.ActionAuthorizationDecision;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketBridge.GraveActivationWriteDecision;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

final class OutboundPacketFirewall extends ChannelDuplexHandler {

    interface LifecycleListener {

        void onFirewallUnavailable(ChannelHandlerContext context, boolean transportClosed);

        void onFirewallFailure(ChannelHandlerContext context, RuntimeException failure);
    }

    private final ActionSessionGuard actionSessionGuard;
    private final LifecycleListener lifecycleListener;
    private final DeathSafetyPacketBridge deathSafetyBridge;
    private long blockedCount;

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard) {
        this(actionSessionGuard, null);
    }

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard, LifecycleListener lifecycleListener) {
        this(actionSessionGuard, lifecycleListener, null);
    }

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard, LifecycleListener lifecycleListener,
        DeathSafetyPacketBridge deathSafetyBridge) {
        this.actionSessionGuard = actionSessionGuard;
        this.lifecycleListener = lifecycleListener;
        this.deathSafetyBridge = deathSafetyBridge;
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        if (deathSafetyBridge != null && message instanceof S06PacketUpdateHealth) {
            double health = ((S06PacketUpdateHealth) message).func_149332_c();
            if (health <= 0.0D) {
                try {
                    deathSafetyBridge.beforeLethalHealthPacket(health);
                } catch (RuntimeException failure) {
                    failClosedInbound(context, message, failure);
                    return;
                }
            }
        }
        context.fireChannelRead(message);
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        if (deathSafetyBridge != null && isPerformRespawn(message)) {
            authorizeRespawnWrite(context, message, promise);
            return;
        }
        if (deathSafetyBridge != null && message instanceof C08PacketPlayerBlockPlacement) {
            if (authorizeGraveActivationWrite(context, (C08PacketPlayerBlockPlacement) message, promise)) {
                return;
            }
        }
        PacketActionRequirement requirement = OutboundPacketClassifier.classify(message);
        if (!requirement.isRestricted()) {
            context.write(message, promise);
            return;
        }
        synchronized (actionSessionGuard) {
            ActionAuthorizationDecision decision = requirement.evaluate(actionSessionGuard);
            if (!decision.isAllowed()) {
                blockedCount++;
                actionSessionGuard.recordBlockedAction(requirement.getDescription());
                if (blockedCount <= 5L || blockedCount % 100L == 0L) {
                    HorizonwrightMod.LOG.warn(
                        "Blocked outbound {} packet at action epoch {}: {} (blocked count {})",
                        requirement.getDescription(),
                        actionSessionGuard.activeEpochOrZero(),
                        decision,
                        blockedCount);
                }
                ReferenceCountUtil.release(message);
                promise.trySuccess();
                return;
            }
            context.write(message, promise);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext context) throws Exception {
        boolean transportClosed = !context.channel()
            .isOpen();
        if (!transportClosed) {
            actionSessionGuard.markFirewallUnavailable();
        }
        if (!transportClosed && (actionSessionGuard.isGuarding() || deathSafetyBridge != null)) {
            HorizonwrightMod.LOG.error(
                "Required outbound packet boundary was removed; disabling Horizonwright automation without "
                    + "closing the client connection");
        }
        if (lifecycleListener != null) {
            lifecycleListener.onFirewallUnavailable(context, transportClosed);
        }
        super.handlerRemoved(context);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        if (lifecycleListener != null) {
            lifecycleListener.onFirewallUnavailable(context, true);
        }
        context.fireChannelInactive();
    }

    private void authorizeRespawnWrite(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        OneShotWriteContinuation continuation = new OneShotWriteContinuation();
        final boolean authorized;
        try {
            authorized = deathSafetyBridge.tryAuthorizeRespawnPacket(continuation);
            continuation.close();
            if (authorized != continuation.wasInvoked()) {
                throw new IllegalStateException("respawn bridge decision did not match its write continuation");
            }
        } catch (RuntimeException failure) {
            continuation.close();
            failClosedOutbound(context, message, promise, failure);
            return;
        }
        if (authorized) {
            context.write(message, promise);
        } else {
            rejectSpecializedWrite(message, promise, "player respawn");
        }
    }

    /** @return true when the bridge handled the packet, whether authorized or rejected. */
    private boolean authorizeGraveActivationWrite(ChannelHandlerContext context, C08PacketPlayerBlockPlacement packet,
        ChannelPromise promise) {
        OneShotWriteContinuation continuation = new OneShotWriteContinuation();
        final GraveActivationWriteDecision decision;
        try {
            decision = deathSafetyBridge.tryAuthorizeGraveActivationPacket(packet, continuation);
            continuation.close();
            if (decision == null) {
                throw new IllegalStateException("grave activation bridge returned no decision");
            }
            boolean authorized = decision == GraveActivationWriteDecision.AUTHORIZED;
            if (authorized != continuation.wasInvoked()) {
                throw new IllegalStateException("grave activation decision did not match its write continuation");
            }
        } catch (RuntimeException failure) {
            continuation.close();
            failClosedOutbound(context, packet, promise, failure);
            return true;
        }
        if (decision == GraveActivationWriteDecision.NOT_APPLICABLE) {
            return false;
        }
        if (decision == GraveActivationWriteDecision.AUTHORIZED) {
            context.write(packet, promise);
        } else {
            rejectSpecializedWrite(packet, promise, "exact grave activation");
        }
        return true;
    }

    private void rejectSpecializedWrite(Object message, ChannelPromise promise, String description) {
        blockedCount++;
        if (blockedCount <= 5L || blockedCount % 100L == 0L) {
            HorizonwrightMod.LOG
                .warn("Death safety rejected outbound {} packet (blocked count {})", description, blockedCount);
        }
        ReferenceCountUtil.release(message);
        promise.trySuccess();
    }

    private void failClosedInbound(ChannelHandlerContext context, Object message, RuntimeException failure) {
        HorizonwrightMod.LOG.error(
            "Death-safety inbound packet hook failed; denying the integrated packet and disabling automation",
            failure);
        ReferenceCountUtil.release(message);
        failClosed(context, failure);
    }

    private void failClosedOutbound(ChannelHandlerContext context, Object message, ChannelPromise promise,
        RuntimeException failure) {
        HorizonwrightMod.LOG.error(
            "Death-safety outbound packet gate failed; denying the integrated packet and disabling automation",
            failure);
        ReferenceCountUtil.release(message);
        promise.tryFailure(failure);
        failClosed(context, failure);
    }

    private void failClosed(ChannelHandlerContext context, RuntimeException failure) {
        actionSessionGuard.markFirewallUnavailable();
        if (lifecycleListener != null) {
            lifecycleListener.onFirewallFailure(context, failure);
        }
    }

    private static boolean isPerformRespawn(Object message) {
        return message instanceof C16PacketClientStatus
            && ((C16PacketClientStatus) message).func_149435_c() == C16PacketClientStatus.EnumState.PERFORM_RESPAWN;
    }

    private static final class OneShotWriteContinuation implements Runnable {

        private boolean open = true;
        private boolean invoked;

        @Override
        public void run() {
            if (!open || invoked) {
                throw new IllegalStateException("final packet-write continuation is one-shot and synchronous");
            }
            invoked = true;
        }

        private void close() {
            open = false;
        }

        private boolean wasInvoked() {
            return invoked;
        }
    }
}
