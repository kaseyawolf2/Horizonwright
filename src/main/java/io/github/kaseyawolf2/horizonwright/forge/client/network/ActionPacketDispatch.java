package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;

import io.netty.channel.Channel;

/** Places cleanup after already-queued Minecraft packet writes on the connection event loop. */
public final class ActionPacketDispatch {

    private ActionPacketDispatch() {}

    public static void afterPendingWrites(Minecraft minecraft, Runnable cleanup) {
        if (minecraft == null || cleanup == null) {
            throw new IllegalArgumentException("minecraft and cleanup are required");
        }
        NetHandlerPlayClient handler = minecraft.getNetHandler();
        if (handler == null) throw new IllegalStateException("no active client network handler");
        NetworkManager manager = handler.getNetworkManager();
        Channel channel = manager == null ? null : manager.channel();
        if (channel == null || !channel.isOpen()) throw new IllegalStateException("no open client network channel");
        channel.eventLoop()
            .execute(cleanup);
    }
}
