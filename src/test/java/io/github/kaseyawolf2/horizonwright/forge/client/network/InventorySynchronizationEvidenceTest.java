package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;

import org.junit.Test;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

public class InventorySynchronizationEvidenceTest {

    @Test
    public void nativeModActionRequiresFreshServerEvidenceFromItsWindowOrPlayerInventory() {
        ContainerTransactionPacketCoordinator coordinator = new ContainerTransactionPacketCoordinator();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            ContainerTransactionPacketBridge bridge = coordinator.open(new NetworkManager(false), channel);
            long before = coordinator.inventorySyncRevision(7);
            bridge.beforeWindowItemsRead(new S30PacketWindowItems(8, Collections.<ItemStack>emptyList()));
            assertEquals(before, coordinator.inventorySyncRevision(7));
            bridge.beforeSetSlotRead(new S2FPacketSetSlot(-1, -1, null));
            assertEquals(before, coordinator.inventorySyncRevision(7));
            bridge.beforeSetSlotRead(new S2FPacketSetSlot(7, 35, null));
            assertTrue(coordinator.inventorySyncRevision(7) > before);
            before = coordinator.inventorySyncRevision(7);
            bridge.beforeSetSlotRead(new S2FPacketSetSlot(-2, 9, null));
            assertTrue(coordinator.inventorySyncRevision(7) > before);
        } finally {
            channel.finish();
        }
    }

    @Test
    public void retiredConnectionCannotConfirmANewInventoryRequest() {
        ContainerTransactionPacketCoordinator coordinator = new ContainerTransactionPacketCoordinator();
        EmbeddedChannel oldChannel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        EmbeddedChannel newChannel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            ContainerTransactionPacketBridge old = coordinator.open(new NetworkManager(false), oldChannel);
            old.beforeSetSlotRead(new S2FPacketSetSlot(7, 35, null));
            assertTrue(coordinator.inventorySyncRevision(7) > 0);
            old.onBoundaryUnavailable(true);
            ContainerTransactionPacketBridge current = coordinator.open(new NetworkManager(false), newChannel);
            assertEquals(0, coordinator.inventorySyncRevision(7));
            old.beforeSetSlotRead(new S2FPacketSetSlot(7, 35, null));
            assertEquals(0, coordinator.inventorySyncRevision(7));
            current.beforeWindowItemsRead(new S30PacketWindowItems(7, Collections.<ItemStack>emptyList()));
            assertTrue(coordinator.inventorySyncRevision(7) > 0);
        } finally {
            oldChannel.finish();
            newChannel.finish();
        }
    }
}
