package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;

/** Recognizes only empty-slot selection and start-sneaking packets inside one active grave permit. */
public final class GravePreparationPacketMatcher {

    public boolean matches(Object packet, GravePreparationPacketSnapshot snapshot) {
        if (packet == null || snapshot == null) {
            return false;
        }
        if (packet instanceof C09PacketHeldItemChange) {
            return ((C09PacketHeldItemChange) packet).func_149614_c() == snapshot.getEmptyHotbarSlot();
        }
        return packet instanceof C0BPacketEntityAction && ((C0BPacketEntityAction) packet).func_149513_d() == 1;
    }
}
