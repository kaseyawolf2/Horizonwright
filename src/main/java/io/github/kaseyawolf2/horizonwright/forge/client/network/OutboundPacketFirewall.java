package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.core.action.ActionAuthorizationDecision;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketBridge.GraveActivationWriteDecision;
import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketBridge.GravePreparationWriteDecision;
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
    private final ContainerTransactionPacketBridge containerTransactionBridge;
    private long blockedCount;

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard) {
        this(actionSessionGuard, null);
    }

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard, LifecycleListener lifecycleListener) {
        this(actionSessionGuard, lifecycleListener, null);
    }

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard, LifecycleListener lifecycleListener,
        DeathSafetyPacketBridge deathSafetyBridge) {
        this(actionSessionGuard, lifecycleListener, deathSafetyBridge, null);
    }

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard, LifecycleListener lifecycleListener,
        DeathSafetyPacketBridge deathSafetyBridge, ContainerTransactionPacketBridge containerTransactionBridge) {
        this.actionSessionGuard = actionSessionGuard;
        this.lifecycleListener = lifecycleListener;
        this.deathSafetyBridge = deathSafetyBridge;
        this.containerTransactionBridge = containerTransactionBridge;
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
        if (containerTransactionBridge != null && message instanceof S32PacketConfirmTransaction) {
            try {
                containerTransactionBridge.beforeConfirmationRead((S32PacketConfirmTransaction) message);
            } catch (RuntimeException failure) {
                failContainerObservation(context, failure);
            }
        }
        context.fireChannelRead(message);
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        // Respawn is a direct vanilla player control, not a Horizonwright automation action. Let it pass
        // unchanged. The death controller still observes the resulting live-player state and keeps all task
        // authority revoked until post-respawn validation completes.
        if (deathSafetyBridge != null && message instanceof C08PacketPlayerBlockPlacement) {
            if (authorizeGraveActivationWrite(context, (C08PacketPlayerBlockPlacement) message, promise)) {
                return;
            }
        }
        if (deathSafetyBridge != null
            && (message instanceof C09PacketHeldItemChange || message instanceof C0BPacketEntityAction)
            && authorizeGravePreparationWrite(context, message, promise)) {
            return;
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
            if (containerTransactionBridge != null && message instanceof C0EPacketClickWindow) {
                try {
                    ContainerTransactionPacketBridge.ClickWriteDecision observation = containerTransactionBridge
                        .beforeClickWrite((C0EPacketClickWindow) message);
                    if (observation == null) {
                        throw new IllegalStateException("container transaction bridge returned no decision");
                    }
                } catch (RuntimeException failure) {
                    failClosedContainerWrite(context, message, promise, failure);
                    return;
                }
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
        if (!transportClosed
            && (actionSessionGuard.isGuarding() || deathSafetyBridge != null || containerTransactionBridge != null)) {
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

    /** @return true when the bridge handled the packet, whether authorized or rejected. */
    private boolean authorizeGravePreparationWrite(ChannelHandlerContext context, Object packet,
        ChannelPromise promise) {
        OneShotWriteContinuation continuation = new OneShotWriteContinuation();
        final GravePreparationWriteDecision decision;
        try {
            decision = deathSafetyBridge.tryAuthorizeGravePreparationPacket(packet, continuation);
            continuation.close();
            if (decision == null) {
                throw new IllegalStateException("grave preparation bridge returned no decision");
            }
            boolean authorized = decision == GravePreparationWriteDecision.AUTHORIZED;
            if (authorized != continuation.wasInvoked()) {
                throw new IllegalStateException("grave preparation decision did not match its write continuation");
            }
        } catch (RuntimeException failure) {
            continuation.close();
            failClosedOutbound(context, packet, promise, failure);
            return true;
        }
        if (decision == GravePreparationWriteDecision.NOT_APPLICABLE) {
            return false;
        }
        if (decision == GravePreparationWriteDecision.AUTHORIZED) {
            context.write(packet, promise);
        } else {
            rejectSpecializedWrite(packet, promise, "grave activation preparation");
        }
        return true;
    }

    private void failClosedInbound(ChannelHandlerContext context, Object message, RuntimeException failure) {
        HorizonwrightMod.LOG.error(
            "Death-safety inbound packet hook failed; denying the integrated packet and disabling automation",
            failure);
        ReferenceCountUtil.release(message);
        failClosed(context, failure);
    }

    private void failContainerObservation(ChannelHandlerContext context, RuntimeException failure) {
        HorizonwrightMod.LOG.error(
            "Container confirmation observation failed; forwarding the vanilla packet but disabling automation",
            failure);
        failClosed(context, failure);
    }

    private void failClosedContainerWrite(ChannelHandlerContext context, Object message, ChannelPromise promise,
        RuntimeException failure) {
        HorizonwrightMod.LOG.error(
            "Prepared container click correlation failed; denying only that integrated click and disabling automation",
            failure);
        ReferenceCountUtil.release(message);
        promise.tryFailure(failure);
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
