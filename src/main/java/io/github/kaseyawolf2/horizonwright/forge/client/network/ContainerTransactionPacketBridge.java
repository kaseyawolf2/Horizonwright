package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

/** Observes only explicitly prepared Horizonwright container transactions. */
public interface ContainerTransactionPacketBridge {

    enum ClickWriteDecision {
        NOT_APPLICABLE,
        AUTHORIZED
    }

    ClickWriteDecision beforeClickWrite(C0EPacketClickWindow packet);

    void beforeConfirmationRead(S32PacketConfirmTransaction packet);

    default void beforeWindowItemsRead(S30PacketWindowItems packet) {}

    default void beforeSetSlotRead(S2FPacketSetSlot packet) {}

    void onBoundaryUnavailable(boolean transportClosed);
}
