package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.network.NetworkManager;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

public class ClientPacketFirewallInstallerTest {

    @Test
    public void installsBeforePacketHandlerAndRecoversFromUnexpectedRemoval() {
        ActionSessionGuard guard = new ActionSessionGuard();
        ClientPacketFirewallInstaller installer = new ClientPacketFirewallInstaller(guard);
        NetworkManager manager = new NetworkManager(true);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.pipeline()
            .addLast("packet_handler", new ChannelInboundHandlerAdapter());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();

        assertEquals(ClientPacketFirewallInstaller.State.INSTALLED, installer.getState());
        assertTrue(installer.isReady());
        List<String> names = channel.pipeline()
            .names();
        assertTrue(names.indexOf("horizonwright_action_firewall") < names.indexOf("packet_handler"));

        channel.pipeline()
            .remove("horizonwright_action_firewall");
        assertEquals(ClientPacketFirewallInstaller.State.ABSENT, installer.getState());
        assertFalse(guard.isReadyForSession());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(ClientPacketFirewallInstaller.State.INSTALLED, installer.getState());
        channel.finish();
    }

    @Test
    public void retriesUntilPacketHandlerExistsAndRunsTheDrainBarrier() {
        ActionSessionGuard guard = new ActionSessionGuard();
        ClientPacketFirewallInstaller installer = new ClientPacketFirewallInstaller(guard);
        NetworkManager manager = new NetworkManager(true);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(ClientPacketFirewallInstaller.State.WAITING_FOR_PIPELINE, installer.getState());
        assertFalse(guard.isReadyForSession());

        channel.pipeline()
            .addLast("packet_handler", new ChannelInboundHandlerAdapter());
        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(ClientPacketFirewallInstaller.State.INSTALLED, installer.getState());

        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        guard.begin(lease);
        guard.quarantine(lease);
        guard.end(lease);
        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());
        assertTrue(installer.isInstalled());
        assertFalse(installer.isReady());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(ActionSessionGuard.Mode.PLAYER, guard.getMode());
        assertTrue(guard.isReadyForSession());
        channel.finish();
    }

    @Test
    public void handlerInstallConflictQuarantinesAutomationWithoutClosingTransport() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);
        ClientPacketFirewallInstaller installer = new ClientPacketFirewallInstaller(guard);
        NetworkManager manager = new NetworkManager(true);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.pipeline()
            .addLast("horizonwright_action_firewall", new ChannelInboundHandlerAdapter());
        channel.pipeline()
            .addLast("packet_handler", new ChannelInboundHandlerAdapter());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();

        assertEquals(ClientPacketFirewallInstaller.State.FAILED, installer.getState());
        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());
        assertFalse(installer.isReady());
        assertTrue(channel.isOpen());
        Object unknown = new Object();
        assertTrue(channel.writeOutbound(unknown));
        assertSame(unknown, channel.readOutbound());
        assertEquals(0L, guard.getBlockedActionCount());
        channel.finish();
    }

    @Test
    public void containerBridgeIsBoundAndRetiredWithTheExactPipelineRegistration() {
        ActionSessionGuard guard = new ActionSessionGuard();
        TrackingContainerBridgeFactory factory = new TrackingContainerBridgeFactory();
        ClientPacketFirewallInstaller installer = new ClientPacketFirewallInstaller(guard, null, factory);
        NetworkManager manager = new NetworkManager(true);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.pipeline()
            .addLast("packet_handler", new ChannelInboundHandlerAdapter());

        installer.ensureInstalled(manager, channel);
        channel.runPendingTasks();
        assertEquals(1, factory.openCount);
        assertFalse(factory.bridge.retired);

        channel.pipeline()
            .remove("horizonwright_action_firewall");
        assertTrue(factory.bridge.retired);
        assertFalse(factory.bridge.transportClosed);
        assertTrue(channel.isOpen());
        channel.finish();
    }

    private static final class TrackingContainerBridgeFactory implements ContainerTransactionPacketBridgeFactory {

        private int openCount;
        private TrackingContainerBridge bridge;

        @Override
        public ContainerTransactionPacketBridge open(NetworkManager manager, Channel channel) {
            openCount++;
            bridge = new TrackingContainerBridge();
            return bridge;
        }
    }

    private static final class TrackingContainerBridge implements ContainerTransactionPacketBridge {

        private boolean retired;
        private boolean transportClosed;

        @Override
        public ClickWriteDecision beforeClickWrite(net.minecraft.network.play.client.C0EPacketClickWindow packet) {
            return ClickWriteDecision.NOT_APPLICABLE;
        }

        @Override
        public void beforeConfirmationRead(net.minecraft.network.play.server.S32PacketConfirmTransaction packet) {}

        @Override
        public void onBoundaryUnavailable(boolean closed) {
            retired = true;
            transportClosed = closed;
        }
    }
}
