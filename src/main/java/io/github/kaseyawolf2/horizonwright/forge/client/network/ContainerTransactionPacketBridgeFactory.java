package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.network.NetworkManager;

import io.netty.channel.Channel;

/** Opens the container correlation bridge for one exact client connection. */
public interface ContainerTransactionPacketBridgeFactory {

    ContainerTransactionPacketBridge open(NetworkManager manager, Channel channel);
}
