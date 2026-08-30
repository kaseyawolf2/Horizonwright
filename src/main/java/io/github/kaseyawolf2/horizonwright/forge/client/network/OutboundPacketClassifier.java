package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C10PacketCreativeInventoryAction;
import net.minecraft.network.play.client.C11PacketEnchantItem;
import net.minecraft.network.play.client.C12PacketUpdateSign;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.network.play.client.C15PacketClientSettings;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;

public final class OutboundPacketClassifier {

    private OutboundPacketClassifier() {}

    public static PacketActionRequirement classify(Object message) {
        if (!(message instanceof Packet)) {
            return PacketActionRequirement.observeOnly("unknown non-packet outbound message");
        }
        if (message instanceof FMLProxyPacket) {
            return classifyFmlProxyPacket((FMLProxyPacket) message);
        }
        if (message instanceof C00PacketKeepAlive || message instanceof C0FPacketConfirmTransaction
            || message instanceof C14PacketTabComplete
            || message instanceof C15PacketClientSettings) {
            return PacketActionRequirement.unrestricted();
        }
        if (message instanceof C0DPacketCloseWindow) {
            return PacketActionRequirement.safeRelease("close container");
        }
        if (message instanceof C01PacketChatMessage) {
            String text = ((C01PacketChatMessage) message).func_149439_c();
            return text != null && text.startsWith("/") ? PacketActionRequirement.observeOnly("server command")
                : PacketActionRequirement.unrestricted();
        }
        if (message instanceof C16PacketClientStatus) {
            return ((C16PacketClientStatus) message).func_149435_c() == C16PacketClientStatus.EnumState.PERFORM_RESPAWN
                ? PacketActionRequirement.safeRelease("player respawn")
                : PacketActionRequirement.unrestricted();
        }
        if (message instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            return PacketActionRequirement
                .allOf("player position and look", ActionCapability.MOVEMENT, ActionCapability.LOOK);
        }
        if (message instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            return PacketActionRequirement.allOf("player position", ActionCapability.MOVEMENT);
        }
        if (message instanceof C03PacketPlayer.C05PacketPlayerLook) {
            return PacketActionRequirement.allOf("player look", ActionCapability.LOOK);
        }
        if (message instanceof C03PacketPlayer) {
            // The base packet carries only the on-ground heartbeat. Position and look mutations use the
            // subclasses above and remain capability-gated.
            return PacketActionRequirement.unrestricted();
        }
        if (message instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) message;
            return packet.func_149565_c() == C02PacketUseEntity.Action.ATTACK
                ? PacketActionRequirement.allOf("entity attack", ActionCapability.ATTACK)
                : PacketActionRequirement.allOf("entity interaction", ActionCapability.USE);
        }
        if (message instanceof C07PacketPlayerDigging) {
            return classifyDigging((C07PacketPlayerDigging) message);
        }
        if (message instanceof C08PacketPlayerBlockPlacement) {
            return classifyUseOrPlacement((C08PacketPlayerBlockPlacement) message);
        }
        if (message instanceof C09PacketHeldItemChange) {
            return PacketActionRequirement.allOf("held-slot change", ActionCapability.HELD_USE);
        }
        if (message instanceof C0APacketAnimation) {
            return PacketActionRequirement.anyOf(
                "arm animation",
                ActionCapability.ATTACK,
                ActionCapability.DIG,
                ActionCapability.PLACE,
                ActionCapability.USE,
                ActionCapability.HELD_USE);
        }
        if (message instanceof C0BPacketEntityAction) {
            return classifyEntityAction((C0BPacketEntityAction) message);
        }
        if (message instanceof C0CPacketInput) {
            return classifyRidingInput((C0CPacketInput) message);
        }
        if (message instanceof C13PacketPlayerAbilities) {
            return ((C13PacketPlayerAbilities) message).func_149488_d()
                ? PacketActionRequirement.allOf("start flying", ActionCapability.MOVEMENT)
                : PacketActionRequirement.safeRelease("stop flying");
        }
        if (message instanceof C0EPacketClickWindow || message instanceof C10PacketCreativeInventoryAction
            || message instanceof C11PacketEnchantItem) {
            return PacketActionRequirement.allOf("container mutation", ActionCapability.CONTAINER);
        }
        if (message instanceof C12PacketUpdateSign) {
            return PacketActionRequirement.allOf("sign update", ActionCapability.PLACE);
        }
        if (message instanceof C17PacketCustomPayload) {
            String channel = ((C17PacketCustomPayload) message).func_149559_c();
            return PacketActionRequirement.observeOnly("custom payload " + String.valueOf(channel));
        }
        return PacketActionRequirement.observeOnly(
            "unclassified packet " + message.getClass()
                .getName());
    }

    private static PacketActionRequirement classifyFmlProxyPacket(FMLProxyPacket packet) {
        String channel = packet.channel();
        // Waila's 1.7.10 client channel only requests read-only block/entity metadata for the overlay. The three FML
        // channels below are Forge's own registration and handshake control plane. Every other mod channel remains
        // observe-only until an explicit, tested integration classifies its semantics.
        if ("Waila".equals(channel) || "REGISTER".equals(channel)
            || "UNREGISTER".equals(channel)
            || "FML|HS".equals(channel)) {
            return PacketActionRequirement.unrestricted();
        }
        return PacketActionRequirement.observeOnly("FML proxy channel '" + printableChannel(channel) + "'");
    }

    private static String printableChannel(String channel) {
        return channel == null ? "<null>"
            : channel.replace('\r', '?')
                .replace('\n', '?')
                .replace('\t', '?');
    }

    private static PacketActionRequirement classifyDigging(C07PacketPlayerDigging packet) {
        int status = packet.func_149506_g();
        if (status == 0 || status == 2) {
            return PacketActionRequirement.allOf("block digging", ActionCapability.DIG);
        }
        if (status == 1 || status == 5) {
            return PacketActionRequirement.safeRelease(status == 1 ? "abort block digging" : "release held-item use");
        }
        if (status == 3 || status == 4) {
            return PacketActionRequirement.allOf("inventory drop", ActionCapability.CONTAINER);
        }
        return PacketActionRequirement.observeOnly("unknown digging action " + status);
    }

    private static PacketActionRequirement classifyEntityAction(C0BPacketEntityAction packet) {
        int action = packet.func_149513_d();
        if (action == 2 || action == 5) {
            return PacketActionRequirement.safeRelease(action == 2 ? "stop sneaking" : "stop sprinting");
        }
        if (action == 7) {
            return PacketActionRequirement.allOf("open horse inventory", ActionCapability.CONTAINER);
        }
        if (action >= 1 && action <= 6) {
            return PacketActionRequirement.allOf("movement state", ActionCapability.MOVEMENT);
        }
        return PacketActionRequirement.observeOnly("unknown entity action " + action);
    }

    private static PacketActionRequirement classifyRidingInput(C0CPacketInput packet) {
        if (packet.func_149620_c() == 0.0F && packet.func_149616_d() == 0.0F
            && !packet.func_149618_e()
            && !packet.func_149617_f()) {
            return PacketActionRequirement.safeRelease("release riding input");
        }
        return PacketActionRequirement.allOf("riding movement", ActionCapability.MOVEMENT);
    }

    private static PacketActionRequirement classifyUseOrPlacement(C08PacketPlayerBlockPlacement packet) {
        if (packet.func_149568_f() == 255) {
            return PacketActionRequirement.allOf("held-item use", ActionCapability.HELD_USE);
        }
        ItemStack stack = packet.func_149574_g();
        if (stack != null && stack.getItem() instanceof ItemBlock) {
            return PacketActionRequirement.allOf("block placement", ActionCapability.PLACE);
        }
        return PacketActionRequirement.allOf("block interaction", ActionCapability.USE);
    }
}
