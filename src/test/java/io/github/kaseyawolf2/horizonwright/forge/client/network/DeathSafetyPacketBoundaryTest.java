package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S06PacketUpdateHealth;

import org.junit.Test;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketBridge.GraveActivationWriteDecision;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

public class DeathSafetyPacketBoundaryTest {

    @Test
    public void lethalS06IsObservedBeforePacketHandlerCanQueueIt() {
        List<String> order = new ArrayList<>();
        RecordingBridge bridge = new RecordingBridge(order);
        EmbeddedChannel channel = new EmbeddedChannel(
            new OutboundPacketFirewall(new ActionSessionGuard(), null, bridge),
            new ChannelInboundHandlerAdapter() {

                @Override
                public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                    order.add("packet-handler");
                    context.fireChannelRead(message);
                }
            });
        S06PacketUpdateHealth lethal = new S06PacketUpdateHealth(0.0F, 20, 5.0F);

        assertTrue(channel.writeInbound(lethal));
        assertSame(lethal, channel.readInbound());
        assertEquals(java.util.Arrays.asList("lethal-health", "packet-handler"), order);

        S06PacketUpdateHealth healthy = new S06PacketUpdateHealth(20.0F, 20, 5.0F);
        assertTrue(channel.writeInbound(healthy));
        assertSame(healthy, channel.readInbound());
        assertEquals(java.util.Arrays.asList("lethal-health", "packet-handler", "packet-handler"), order);
        channel.finish();
    }

    @Test
    public void performRespawnMustBeConsumedBySpecializedGate() {
        RecordingBridge rejectedBridge = new RecordingBridge(new ArrayList<>());
        EmbeddedChannel rejected = new EmbeddedChannel(
            new OutboundPacketFirewall(new ActionSessionGuard(), null, rejectedBridge));
        C16PacketClientStatus rejectedPacket = respawnPacket();

        rejected.writeOutbound(rejectedPacket);
        assertNull(rejected.readOutbound());
        assertEquals(1, rejectedBridge.respawnAttempts);

        RecordingBridge authorizedBridge = new RecordingBridge(new ArrayList<>());
        authorizedBridge.authorizeRespawn = true;
        EmbeddedChannel authorized = new EmbeddedChannel(
            new OutboundPacketFirewall(new ActionSessionGuard(), null, authorizedBridge));
        C16PacketClientStatus authorizedPacket = respawnPacket();

        assertTrue(authorized.writeOutbound(authorizedPacket));
        assertSame(authorizedPacket, authorized.readOutbound());
        assertEquals(1, authorizedBridge.respawnAttempts);
        rejected.finish();
        authorized.finish();
    }

    @Test
    public void exactGraveAuthorizationPrecedesGenericActionRejection() {
        ActionSessionGuard genericWouldRejectUse = activeGuard(EnumSet.of(ActionCapability.MOVEMENT));
        RecordingBridge bridge = new RecordingBridge(new ArrayList<>());
        bridge.graveDecision = GraveActivationWriteDecision.AUTHORIZED;
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(genericWouldRejectUse, null, bridge));
        C08PacketPlayerBlockPlacement exactGrave = usePacket(10);

        assertTrue(channel.writeOutbound(exactGrave));
        assertSame(exactGrave, channel.readOutbound());
        assertEquals(1, bridge.graveAttempts);

        bridge.graveDecision = GraveActivationWriteDecision.NOT_APPLICABLE;
        channel.writeOutbound(usePacket(11));
        assertNull(channel.readOutbound());
        assertEquals(2, bridge.graveAttempts);
        channel.finish();
    }

    @Test
    public void inconsistentSpecializedDecisionFailsTheExactWriteWithoutClosingTransport() {
        RecordingBridge bridge = new RecordingBridge(new ArrayList<>()) {

            @Override
            public boolean tryAuthorizeRespawnPacket(Runnable finalWriteContinuation) {
                return true;
            }
        };
        ActionSessionGuard guard = activeGuard(EnumSet.of(ActionCapability.MOVEMENT));
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard, null, bridge));

        try {
            channel.writeOutbound(respawnPacket());
        } catch (RuntimeException expected) {
            // EmbeddedChannel surfaces the deliberately failed exact-packet promise.
        }

        assertTrue(channel.isOpen());
        assertFalse(guard.isReadyForSession());
        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());
        assertNull(channel.readOutbound());

        Object unknown = new Object();
        assertTrue(channel.writeOutbound(unknown));
        assertSame(unknown, channel.readOutbound());
        assertEquals(0L, guard.getBlockedActionCount());

        channel.writeOutbound(new C07PacketPlayerDigging(0, 1, 64, 1, 1));
        assertNull(channel.readOutbound());
        assertEquals(1L, guard.getBlockedActionCount());
        channel.finish();
    }

    @Test
    public void lethalHookFailureDropsOnlyTheIntegratedPacketAndKeepsUnknownTrafficFlowing() {
        RecordingBridge bridge = new RecordingBridge(new ArrayList<>()) {

            @Override
            public void beforeLethalHealthPacket(double health) {
                throw new IllegalStateException("simulated hook failure");
            }
        };
        ActionSessionGuard guard = activeGuard(EnumSet.of(ActionCapability.MOVEMENT));
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard, null, bridge));

        assertFalse(channel.writeInbound(new S06PacketUpdateHealth(0.0F, 20, 5.0F)));
        assertNull(channel.readInbound());
        assertTrue(channel.isOpen());
        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());

        Object unknown = new Object();
        assertTrue(channel.writeOutbound(unknown));
        assertSame(unknown, channel.readOutbound());
        assertEquals(0L, guard.getBlockedActionCount());
        channel.finish();
    }

    @Test
    public void configuredInstallerOpensBridgeOnlyAtInstallAndRetiresItOnRemoval() {
        ActionSessionGuard guard = new ActionSessionGuard();
        RecordingBridge bridge = new RecordingBridge(new ArrayList<>());
        final int[] opened = { 0 };
        ClientPacketFirewallInstaller installer = new ClientPacketFirewallInstaller(guard, (manager, channel) -> {
            opened[0]++;
            return bridge;
        });
        NetworkManager manager = new NetworkManager(true);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(ClientPacketFirewallInstaller.State.WAITING_FOR_PIPELINE, installer.getState());
        assertEquals(0, opened[0]);

        channel.pipeline()
            .addLast("packet_handler", new ChannelInboundHandlerAdapter());
        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(ClientPacketFirewallInstaller.State.INSTALLED, installer.getState());
        assertEquals(1, opened[0]);

        channel.pipeline()
            .remove("horizonwright_action_firewall");
        channel.runPendingTasks();
        assertTrue(channel.isOpen());
        assertEquals(ClientPacketFirewallInstaller.State.FAILED, installer.getState());
        assertEquals(1, bridge.boundaryUnavailableCalls);
        assertFalse(guard.isReadyForSession());

        Object unknown = new Object();
        assertTrue(channel.writeOutbound(unknown));
        assertSame(unknown, channel.readOutbound());
        channel.finish();
    }

    @Test
    public void bridgeFactoryFailureDisablesAutomationWithoutOwningTheTransport() {
        ActionSessionGuard guard = new ActionSessionGuard();
        ClientPacketFirewallInstaller installer = new ClientPacketFirewallInstaller(
            guard,
            (manager, channel) -> { throw new IllegalStateException("simulated bridge factory failure"); });
        NetworkManager manager = new NetworkManager(true);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.pipeline()
            .addLast("packet_handler", new ChannelInboundHandlerAdapter());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();

        assertEquals(ClientPacketFirewallInstaller.State.FAILED, installer.getState());
        assertFalse(installer.isReady());
        assertTrue(channel.isOpen());
        Object unknown = new Object();
        assertTrue(channel.writeOutbound(unknown));
        assertSame(unknown, channel.readOutbound());
        channel.finish();
    }

    @Test
    public void installedBridgeFailureStaysDisabledWhileUnknownTrafficKeepsFlowing() {
        RecordingBridge bridge = new RecordingBridge(new ArrayList<>()) {

            @Override
            public boolean tryAuthorizeRespawnPacket(Runnable finalWriteContinuation) {
                return true;
            }
        };
        ActionSessionGuard guard = new ActionSessionGuard();
        ClientPacketFirewallInstaller installer = new ClientPacketFirewallInstaller(
            guard,
            (manager, channel) -> bridge);
        NetworkManager manager = new NetworkManager(true);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.pipeline()
            .addLast("packet_handler", new ChannelInboundHandlerAdapter());
        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(ClientPacketFirewallInstaller.State.INSTALLED, installer.getState());

        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("test", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        guard.begin(lease);
        try {
            channel.writeOutbound(respawnPacket());
        } catch (RuntimeException expected) {
            // The exact packet is denied with a failed promise; the transport remains healthy.
        }

        assertEquals(ClientPacketFirewallInstaller.State.FAILED, installer.getState());
        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());
        assertTrue(channel.isOpen());
        assertNull(channel.readOutbound());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(ClientPacketFirewallInstaller.State.FAILED, installer.getState());
        assertFalse(installer.isReady());

        Object unknown = new Object();
        assertTrue(channel.writeOutbound(unknown));
        assertSame(unknown, channel.readOutbound());
        assertEquals(0L, guard.getBlockedActionCount());
        channel.finish();
    }

    @Test
    public void unknownTrafficPassesByIdentityInEveryGuardStateWithoutPoisoning() {
        assertUnknownTrafficPasses(activeGuard(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK)));
        assertUnknownTrafficPasses(quarantinedGuard());
        assertUnknownTrafficPasses(lockdownGuard());
    }

    @Test
    public void positivelyClassifiedActionStillRequiresItsCapability() {
        ActionSessionGuard guard = activeGuard(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK));
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));

        channel.writeOutbound(new C07PacketPlayerDigging(0, 1, 64, 1, 1));

        assertNull(channel.readOutbound());
        assertEquals(1L, guard.getBlockedActionCount());
        assertEquals("block digging", guard.getLastBlockedAction());
        channel.finish();
    }

    private static void assertUnknownTrafficPasses(ActionSessionGuard guard) {
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundPacketFirewall(guard));
        Object nonPacket = new Object();
        S00PacketKeepAlive opaquePacket = new S00PacketKeepAlive();
        FMLProxyPacket arbitraryProxy = new FMLProxyPacket(Unpooled.wrappedBuffer(new byte[] { 7 }), "MutatingMod");
        C17PacketCustomPayload customPayload = new C17PacketCustomPayload("HW|MUTATE", new byte[] { 1 });

        assertTrue(channel.writeOutbound(nonPacket));
        assertSame(nonPacket, channel.readOutbound());
        assertTrue(channel.writeOutbound(opaquePacket));
        assertSame(opaquePacket, channel.readOutbound());
        assertTrue(channel.writeOutbound(arbitraryProxy));
        assertSame(arbitraryProxy, channel.readOutbound());
        assertTrue(channel.writeOutbound(customPayload));
        assertSame(customPayload, channel.readOutbound());
        assertEquals(0L, guard.getBlockedActionCount());

        arbitraryProxy.payload()
            .release();
        channel.finish();
    }

    private static ActionSessionGuard activeGuard(EnumSet<ActionCapability> capabilities) {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("test", capabilities)
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        return guard;
    }

    private static ActionSessionGuard quarantinedGuard() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("test", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        guard.quarantine(lease);
        guard.end(lease);
        return guard;
    }

    private static ActionSessionGuard lockdownGuard() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        broker.addRevocationListener(guard);
        broker.enterSafetyLockdown();
        return guard;
    }

    private static C16PacketClientStatus respawnPacket() {
        return new C16PacketClientStatus(C16PacketClientStatus.EnumState.PERFORM_RESPAWN);
    }

    private static C08PacketPlayerBlockPlacement usePacket(int x) {
        return new C08PacketPlayerBlockPlacement(x, 64, 10, 1, new ItemStack(Items.stick), 0.5F, 0.5F, 0.5F);
    }

    private static class RecordingBridge implements DeathSafetyPacketBridge {

        private final List<String> order;
        private boolean authorizeRespawn;
        private GraveActivationWriteDecision graveDecision = GraveActivationWriteDecision.NOT_APPLICABLE;
        private int respawnAttempts;
        private int graveAttempts;
        private int boundaryUnavailableCalls;

        private RecordingBridge(List<String> order) {
            this.order = order;
        }

        @Override
        public void beforeLethalHealthPacket(double health) {
            order.add("lethal-health");
        }

        @Override
        public boolean tryAuthorizeRespawnPacket(Runnable finalWriteContinuation) {
            respawnAttempts++;
            if (authorizeRespawn) {
                finalWriteContinuation.run();
            }
            return authorizeRespawn;
        }

        @Override
        public GraveActivationWriteDecision tryAuthorizeGraveActivationPacket(C08PacketPlayerBlockPlacement packet,
            Runnable finalWriteContinuation) {
            graveAttempts++;
            if (graveDecision == GraveActivationWriteDecision.AUTHORIZED) {
                finalWriteContinuation.run();
            }
            return graveDecision;
        }

        @Override
        public void onBoundaryUnavailable(boolean transportClosed) {
            boundaryUnavailableCalls++;
        }
    }

}
