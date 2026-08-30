package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.network.NetworkManager;

import io.netty.channel.Channel;

/** Opens one death-safety packet bridge only when the corresponding Netty boundary is installable. */
public interface DeathSafetyPacketBridgeFactory {

    DeathSafetyPacketBridge open(NetworkManager manager, Channel channel);
}
