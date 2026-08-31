package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;

import org.junit.Test;

public class GravePreparationPacketMatcherTest {

    private final GravePreparationPacketMatcher matcher = new GravePreparationPacketMatcher();
    private final GravePreparationPacketSnapshot snapshot = new GravePreparationPacketSnapshot(7L, 3);

    @Test
    public void matchesOnlyThePublishedEmptySlotAndStartSneaking() {
        assertTrue(matcher.matches(new C09PacketHeldItemChange(3), snapshot));
        assertFalse(matcher.matches(new C09PacketHeldItemChange(2), snapshot));
        assertTrue(matcher.matches(entityAction(1), snapshot));
        assertFalse(matcher.matches(entityAction(2), snapshot));
        assertFalse(matcher.matches(new Object(), snapshot));
        assertFalse(matcher.matches(new C09PacketHeldItemChange(3), null));
    }

    private static C0BPacketEntityAction entityAction(int action) {
        try {
            C0BPacketEntityAction packet = new C0BPacketEntityAction();
            Field value = C0BPacketEntityAction.class.getDeclaredField("field_149515_b");
            value.setAccessible(true);
            value.setInt(packet, action);
            return packet;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
