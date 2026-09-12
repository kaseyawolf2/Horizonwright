package io.github.kaseyawolf2.horizonwright.forge.client.network;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

/** A dropped C09 does not invalidate vanilla's cached slot; explicitly resynchronize at action boundaries. */
public final class HeldSlotSynchronization {

    private HeldSlotSynchronization() {}

    public static void sendCurrentSlot(Minecraft minecraft) {
        sendSelectedSlot(
            minecraft.thePlayer.inventory.currentItem,
            packet -> minecraft.getNetHandler()
                .addToSendQueue(packet));
    }

    static void sendSelectedSlot(int selectedSlot, Consumer<C09PacketHeldItemChange> outbound) {
        if (selectedSlot < 0 || selectedSlot > 8) throw new IllegalArgumentException("Invalid selected hotbar slot");
        outbound.accept(new C09PacketHeldItemChange(selectedSlot));
    }
}
