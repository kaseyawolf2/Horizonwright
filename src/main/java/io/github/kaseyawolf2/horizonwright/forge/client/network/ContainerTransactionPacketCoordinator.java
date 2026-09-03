package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerClickCorrelation;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerClickCorrelation.ConfirmationObservation;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerClickCorrelation.WriteObservation;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ContainerTransactionPacketBridge.ClickWriteDecision;
import io.netty.channel.Channel;

/**
 * Owns at most one live container mutation and binds it to one client channel.
 * Packets are ignored unless a caller has explicitly activated a correlation.
 */
public final class ContainerTransactionPacketCoordinator implements ContainerTransactionPacketBridgeFactory {

    public interface NanoClock {

        long nanoTime();
    }

    private final NanoClock clock;
    private Boundary boundary;
    private ContainerClickCorrelation active;

    public ContainerTransactionPacketCoordinator() {
        this(System::nanoTime);
    }

    public ContainerTransactionPacketCoordinator(NanoClock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    @Override
    public synchronized ContainerTransactionPacketBridge open(NetworkManager manager, Channel channel) {
        if (manager == null || channel == null) {
            throw new IllegalArgumentException("manager and channel are required");
        }
        if (boundary != null && !boundary.retired) {
            throw new IllegalStateException("a container packet boundary is already open");
        }
        boundary = new Boundary(manager, channel);
        return boundary;
    }

    public synchronized void activate(ContainerClickCorrelation correlation) {
        if (correlation == null || correlation.isTerminal()) {
            throw new IllegalArgumentException("a non-terminal correlation is required");
        }
        if (boundary == null || boundary.retired) {
            throw new IllegalStateException("container packet boundary is unavailable");
        }
        if (active != null && active != correlation && !active.isTerminal()) {
            throw new IllegalStateException("another container transaction is already active");
        }
        active = correlation;
    }

    public synchronized void release(ContainerClickCorrelation correlation) {
        if (active == correlation) {
            active = null;
        }
    }

    public synchronized boolean isBoundaryReady() {
        return boundary != null && !boundary.retired;
    }

    private synchronized ClickWriteDecision observeWrite(Boundary source, C0EPacketClickWindow packet) {
        if (boundary != source || source.retired || active == null) {
            return ClickWriteDecision.NOT_APPLICABLE;
        }
        WriteObservation observation = active.observeWrite(
            packet.func_149548_c(),
            packet.func_149544_d(),
            packet.func_149543_e(),
            packet.func_149542_h(),
            packet.func_149547_f(),
            clock.nanoTime());
        if (active.isTerminal()) {
            active = null;
        }
        if (observation == WriteObservation.ACTIVE_MISMATCH) {
            throw new IllegalStateException("outbound container click did not match Horizonwright's prepared click");
        }
        return observation == WriteObservation.MATCHED ? ClickWriteDecision.AUTHORIZED
            : ClickWriteDecision.NOT_APPLICABLE;
    }

    private synchronized void observeConfirmation(Boundary source, S32PacketConfirmTransaction packet) {
        if (boundary != source || source.retired || active == null) {
            return;
        }
        ConfirmationObservation observation = active.observeConfirmation(
            packet.func_148889_c(),
            packet.func_148890_d(),
            packet.func_148888_e(),
            clock.nanoTime());
        if (active.isTerminal()) {
            active = null;
        }
    }

    private synchronized void observeWindowItems(Boundary source, S30PacketWindowItems packet) {
        if (boundary != source || source.retired || active == null) {
            return;
        }
        active.observeAuthoritativeResync(packet.func_148911_c(), clock.nanoTime());
        if (active.isTerminal()) {
            active = null;
        }
    }

    private synchronized void observeSetSlot(Boundary source, S2FPacketSetSlot packet) {
        if (boundary != source || source.retired || active == null) {
            return;
        }
        if (packet.func_149175_c() == -1 && packet.func_149173_d() == -1) {
            active.observeAuthoritativeCursorResync(clock.nanoTime());
        }
        if (active.isTerminal()) {
            active = null;
        }
    }

    private synchronized void retire(Boundary source, boolean transportClosed) {
        if (boundary != source || source.retired) {
            return;
        }
        source.retired = true;
        if (active != null) {
            active.cancel(
                transportClosed ? "client connection closed during container transaction"
                    : "container packet boundary became unavailable");
            active = null;
        }
    }

    private final class Boundary implements ContainerTransactionPacketBridge {

        @SuppressWarnings("unused")
        private final NetworkManager manager;
        @SuppressWarnings("unused")
        private final Channel channel;
        private boolean retired;

        private Boundary(NetworkManager manager, Channel channel) {
            this.manager = manager;
            this.channel = channel;
        }

        @Override
        public ClickWriteDecision beforeClickWrite(C0EPacketClickWindow packet) {
            if (packet == null) {
                throw new IllegalArgumentException("packet must not be null");
            }
            return observeWrite(this, packet);
        }

        @Override
        public void beforeConfirmationRead(S32PacketConfirmTransaction packet) {
            if (packet == null) {
                throw new IllegalArgumentException("packet must not be null");
            }
            observeConfirmation(this, packet);
        }

        @Override
        public void beforeWindowItemsRead(S30PacketWindowItems packet) {
            if (packet == null) {
                throw new IllegalArgumentException("packet must not be null");
            }
            observeWindowItems(this, packet);
        }

        @Override
        public void beforeSetSlotRead(S2FPacketSetSlot packet) {
            if (packet == null) {
                throw new IllegalArgumentException("packet must not be null");
            }
            observeSetSlot(this, packet);
        }

        @Override
        public void onBoundaryUnavailable(boolean transportClosed) {
            retire(this, transportClosed);
        }
    }
}
