package io.github.kaseyawolf2.horizonwright.forge.client.network;

import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.core.action.ActionAuthorizationDecision;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

final class OutboundPacketFirewall extends ChannelDuplexHandler {

    interface LifecycleListener {

        void onFirewallUnavailable(ChannelHandlerContext context, boolean transportClosed);
    }

    private final ActionSessionGuard actionSessionGuard;
    private final LifecycleListener lifecycleListener;
    private long blockedCount;

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard) {
        this(actionSessionGuard, null);
    }

    OutboundPacketFirewall(ActionSessionGuard actionSessionGuard, LifecycleListener lifecycleListener) {
        this.actionSessionGuard = actionSessionGuard;
        this.lifecycleListener = lifecycleListener;
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        synchronized (actionSessionGuard) {
            PacketActionRequirement requirement = OutboundPacketClassifier.classify(message);
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
        if (context.channel()
            .isOpen() && actionSessionGuard.isGuarding()) {
            HorizonwrightMod.LOG.error("Outbound action firewall was removed while guarding; closing connection");
            context.close();
        }
        if (lifecycleListener != null) {
            lifecycleListener.onFirewallUnavailable(
                context,
                !context.channel()
                    .isOpen());
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
}
