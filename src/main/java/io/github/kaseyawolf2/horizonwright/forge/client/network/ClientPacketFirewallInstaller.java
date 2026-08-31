package io.github.kaseyawolf2.horizonwright.forge.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;

import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.netty.channel.Channel;

public final class ClientPacketFirewallInstaller implements OutboundPacketFirewall.LifecycleListener {

    private static final String HANDLER_NAME = "horizonwright_action_firewall";
    private static final int MAX_INSTALL_ATTEMPTS = 100;

    public enum State {
        ABSENT,
        SCHEDULED,
        WAITING_FOR_PIPELINE,
        INSTALLED,
        FAILED,
        CLOSED
    }

    private final ActionSessionGuard actionSessionGuard;
    private final DeathSafetyPacketBridgeFactory deathSafetyBridgeFactory;
    private final ContainerTransactionPacketBridgeFactory containerTransactionBridgeFactory;
    private Registration current;
    private long nextRegistrationId = 1L;
    private volatile State state = State.ABSENT;
    private volatile String diagnostic = "No client play connection";

    public ClientPacketFirewallInstaller(ActionSessionGuard actionSessionGuard) {
        this(actionSessionGuard, null, null);
    }

    public ClientPacketFirewallInstaller(ActionSessionGuard actionSessionGuard,
        DeathSafetyPacketBridgeFactory deathSafetyBridgeFactory) {
        this(actionSessionGuard, deathSafetyBridgeFactory, null);
    }

    public ClientPacketFirewallInstaller(ActionSessionGuard actionSessionGuard,
        DeathSafetyPacketBridgeFactory deathSafetyBridgeFactory,
        ContainerTransactionPacketBridgeFactory containerTransactionBridgeFactory) {
        if (actionSessionGuard == null) {
            throw new IllegalArgumentException("actionSessionGuard must not be null");
        }
        this.actionSessionGuard = actionSessionGuard;
        this.deathSafetyBridgeFactory = deathSafetyBridgeFactory;
        this.containerTransactionBridgeFactory = containerTransactionBridgeFactory;
    }

    public void ensureInstalled() {
        NetHandlerPlayClient netHandler = Minecraft.getMinecraft()
            .getNetHandler();
        if (netHandler == null) {
            Registration registration;
            synchronized (this) {
                registration = current;
                state = State.ABSENT;
                diagnostic = "No client play connection";
            }
            if (registration == null || !registration.channel.isOpen()) {
                retirePacketBridges(registration, true);
                actionSessionGuard.markTransportClosed();
            } else {
                actionSessionGuard.markFirewallUnavailable();
                retirePacketBridges(registration, false);
            }
            return;
        }
        final NetworkManager manager = netHandler.getNetworkManager();
        final Channel channel = manager.channel();
        if (channel == null) {
            synchronized (this) {
                state = State.WAITING_FOR_PIPELINE;
                diagnostic = "Current NetworkManager has no channel yet";
            }
            actionSessionGuard.markFirewallUnavailable();
            return;
        }
        ensureInstalled(manager, channel);
    }

    void ensureInstalled(NetworkManager manager, Channel channel) {
        if (manager == null || channel == null) {
            throw new IllegalArgumentException("manager and channel are required");
        }
        Registration registration;
        Registration staleRequiredRegistration = null;
        boolean retireMissingSafetyBoundary = false;
        synchronized (this) {
            if (current == null || current.manager != manager || current.channel != channel) {
                if (current != null && current.channel.isOpen()
                    && (actionSessionGuard.isGuarding() || current.safetyBridge != null
                        || current.containerTransactionBridge != null)) {
                    staleRequiredRegistration = current;
                }
                current = new Registration(nextRegistrationId++, manager, channel);
                registration = current;
                state = State.SCHEDULED;
                diagnostic = "Outbound action firewall installation scheduled";
                actionSessionGuard.markFirewallUnavailable();
            } else {
                registration = current;
                if (state == State.INSTALLED && channel.pipeline()
                    .get(HANDLER_NAME) != registration.firewall) {
                    state = State.ABSENT;
                    diagnostic = "Outbound action firewall handler disappeared";
                    actionSessionGuard.markFirewallUnavailable();
                    retireMissingSafetyBoundary = deathSafetyBridgeFactory != null
                        || containerTransactionBridgeFactory != null;
                }
            }
            if (state != State.INSTALLED && !registration.installScheduled && !registration.boundaryFailed) {
                scheduleInstallLocked(registration);
            }
        }
        if (staleRequiredRegistration != null) {
            HorizonwrightMod.LOG
                .warn("Retiring Horizonwright's stale packet boundary without closing its client connection");
            retirePacketBridges(staleRequiredRegistration, false);
        }
        if (retireMissingSafetyBoundary) {
            retirePacketBridges(registration, false);
        }
        scheduleDrainBarrier(registration);
    }

    public State getState() {
        return state;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public boolean isReady() {
        return state == State.INSTALLED && actionSessionGuard.isReadyForSession();
    }

    /**
     * Returns whether the packet boundary is installed, including while an old automation session is draining.
     * Runtime cleanup must keep ticking during that drain or the producer and firewall would wait on each other.
     */
    public boolean isInstalled() {
        return state == State.INSTALLED;
    }

    private void scheduleInstallLocked(Registration registration) {
        if (registration.installAttempts >= MAX_INSTALL_ATTEMPTS) {
            state = State.FAILED;
            diagnostic = "Outbound action firewall did not become installable after " + MAX_INSTALL_ATTEMPTS
                + " attempts";
            actionSessionGuard.markFirewallUnavailable();
            return;
        }
        registration.installAttempts++;
        registration.installScheduled = true;
        try {
            registration.channel.eventLoop()
                .execute(() -> installOnEventLoop(registration));
        } catch (RuntimeException failure) {
            registration.installScheduled = false;
            state = State.FAILED;
            diagnostic = "Could not schedule outbound action firewall installation: " + failure.getMessage();
            actionSessionGuard.markFirewallUnavailable();
            HorizonwrightMod.LOG.error(diagnostic, failure);
        }
    }

    private void installOnEventLoop(Registration registration) {
        synchronized (this) {
            if (current != registration) {
                return;
            }
            registration.installScheduled = false;
        }
        Channel channel = registration.channel;
        if (!channel.isOpen()) {
            markClosed(registration);
            return;
        }
        Object existing = channel.pipeline()
            .get(HANDLER_NAME);
        if (existing != null) {
            if (existing == registration.firewall) {
                markInstalled(registration);
            } else {
                markFailed(registration, "Another handler already uses " + HANDLER_NAME, null);
            }
            return;
        }
        if (channel.pipeline()
            .get("packet_handler") == null) {
            synchronized (this) {
                if (current == registration) {
                    state = State.WAITING_FOR_PIPELINE;
                    diagnostic = "Waiting for packet_handler before installing outbound action firewall";
                }
            }
            actionSessionGuard.markFirewallUnavailable();
            return;
        }
        DeathSafetyPacketBridge safetyBridge = null;
        ContainerTransactionPacketBridge containerTransactionBridge = null;
        try {
            safetyBridge = deathSafetyBridgeFactory == null ? null
                : deathSafetyBridgeFactory.open(registration.manager, channel);
            if (deathSafetyBridgeFactory != null && safetyBridge == null) {
                throw new IllegalStateException("death-safety packet bridge factory returned null");
            }
            containerTransactionBridge = containerTransactionBridgeFactory == null ? null
                : containerTransactionBridgeFactory.open(registration.manager, channel);
            if (containerTransactionBridgeFactory != null && containerTransactionBridge == null) {
                throw new IllegalStateException("container transaction packet bridge factory returned null");
            }
            boolean staleRegistration;
            synchronized (this) {
                staleRegistration = current != registration;
                if (!staleRegistration) {
                    registration.safetyBridge = safetyBridge;
                    registration.safetyBridgeRetired = false;
                    registration.containerTransactionBridge = containerTransactionBridge;
                    registration.containerTransactionBridgeRetired = false;
                    registration.firewall = new OutboundPacketFirewall(
                        actionSessionGuard,
                        ClientPacketFirewallInstaller.this,
                        safetyBridge,
                        containerTransactionBridge);
                }
            }
            if (staleRegistration) {
                retireSafetyBridge(safetyBridge, false);
                retireContainerTransactionBridge(containerTransactionBridge, false);
                return;
            }
            channel.pipeline()
                .addBefore("packet_handler", HANDLER_NAME, registration.firewall);
            markInstalled(registration);
        } catch (RuntimeException failure) {
            retireSafetyBridge(safetyBridge, false);
            retireContainerTransactionBridge(containerTransactionBridge, false);
            markFailed(registration, "Could not install outbound action firewall", failure);
        }
    }

    private void markInstalled(Registration registration) {
        synchronized (this) {
            if (current != registration) {
                return;
            }
            state = State.INSTALLED;
            diagnostic = "Outbound action firewall installed";
        }
        actionSessionGuard.markFirewallInstalled();
        HorizonwrightMod.LOG.info("Outbound action firewall installed on client connection");
        scheduleDrainBarrier(registration);
    }

    private void markFailed(Registration registration, String message, RuntimeException failure) {
        synchronized (this) {
            if (current != registration) {
                return;
            }
            state = State.FAILED;
            diagnostic = message;
        }
        actionSessionGuard.markFirewallUnavailable();
        retirePacketBridges(registration, false);
        if (failure == null) {
            HorizonwrightMod.LOG.error(message);
        } else {
            HorizonwrightMod.LOG.error(message, failure);
        }
    }

    private void markClosed(Registration registration) {
        synchronized (this) {
            if (current != registration) {
                return;
            }
            state = State.CLOSED;
            diagnostic = "Client connection closed";
        }
        retirePacketBridges(registration, true);
        actionSessionGuard.markTransportClosed();
    }

    private void scheduleDrainBarrier(Registration registration) {
        long token = actionSessionGuard.drainGenerationOrZero();
        if (token == 0L) {
            return;
        }
        synchronized (this) {
            if (current != registration || state != State.INSTALLED || registration.drainScheduled == token) {
                return;
            }
            registration.drainScheduled = token;
        }
        try {
            registration.channel.eventLoop()
                .execute(() -> completeDrainOnEventLoop(registration, token));
        } catch (RuntimeException failure) {
            synchronized (this) {
                if (current == registration && registration.drainScheduled == token) {
                    registration.drainScheduled = 0L;
                }
            }
            markFailed(registration, "Could not schedule outbound action firewall drain barrier", failure);
        }
    }

    private void completeDrainOnEventLoop(Registration registration, long token) {
        synchronized (this) {
            if (current != registration || state != State.INSTALLED
                || registration.channel.pipeline()
                    .get(HANDLER_NAME) != registration.firewall) {
                if (current == registration && registration.drainScheduled == token) {
                    registration.drainScheduled = 0L;
                }
                return;
            }
        }
        boolean completed = actionSessionGuard.completeDrain(token);
        synchronized (this) {
            if (current == registration && registration.drainScheduled == token) {
                registration.drainScheduled = 0L;
            }
        }
        if (completed) {
            HorizonwrightMod.LOG.debug("Outbound action quarantine drained at generation {}", token);
        }
    }

    @Override
    public void onFirewallUnavailable(io.netty.channel.ChannelHandlerContext context, boolean transportClosed) {
        Registration unavailable;
        synchronized (this) {
            if (current == null || current.channel != context.channel() || current.firewall != context.handler()) {
                return;
            }
            unavailable = current;
            if (transportClosed) {
                state = State.CLOSED;
                diagnostic = "Client connection closed";
            } else if (deathSafetyBridgeFactory != null || containerTransactionBridgeFactory != null) {
                current = new Registration(nextRegistrationId++, current.manager, current.channel);
                state = State.FAILED;
                diagnostic = "Integrated packet boundary was removed";
            } else {
                current = new Registration(nextRegistrationId++, current.manager, current.channel);
                state = State.ABSENT;
                diagnostic = "Outbound action firewall handler removed";
            }
        }
        retirePacketBridges(unavailable, transportClosed);
        if (transportClosed) {
            actionSessionGuard.markTransportClosed();
        } else {
            actionSessionGuard.markFirewallUnavailable();
        }
    }

    @Override
    public void onFirewallFailure(io.netty.channel.ChannelHandlerContext context, RuntimeException failure) {
        Registration failed;
        synchronized (this) {
            if (current == null || current.channel != context.channel() || current.firewall != context.handler()) {
                return;
            }
            failed = current;
            failed.boundaryFailed = true;
            state = State.FAILED;
            diagnostic = "Integrated packet boundary failed: " + failure.getMessage();
        }
        retirePacketBridges(failed, false);
        actionSessionGuard.markFirewallUnavailable();
    }

    private void retirePacketBridges(Registration registration, boolean transportClosed) {
        retireSafetyBridge(registration, transportClosed);
        retireContainerTransactionBridge(registration, transportClosed);
    }

    private void retireSafetyBridge(Registration registration, boolean transportClosed) {
        if (registration == null) {
            return;
        }
        DeathSafetyPacketBridge safetyBridge;
        synchronized (this) {
            if (registration.safetyBridgeRetired || registration.safetyBridge == null) {
                return;
            }
            registration.safetyBridgeRetired = true;
            safetyBridge = registration.safetyBridge;
        }
        retireSafetyBridge(safetyBridge, transportClosed);
    }

    private void retireSafetyBridge(DeathSafetyPacketBridge safetyBridge, boolean transportClosed) {
        if (safetyBridge == null) {
            return;
        }
        try {
            safetyBridge.onBoundaryUnavailable(transportClosed);
        } catch (RuntimeException failure) {
            actionSessionGuard.markFirewallUnavailable();
            HorizonwrightMod.LOG.error("Death-safety packet bridge retirement failed", failure);
        }
    }

    private void retireContainerTransactionBridge(Registration registration, boolean transportClosed) {
        if (registration == null) {
            return;
        }
        ContainerTransactionPacketBridge bridge;
        synchronized (this) {
            if (registration.containerTransactionBridgeRetired || registration.containerTransactionBridge == null) {
                return;
            }
            registration.containerTransactionBridgeRetired = true;
            bridge = registration.containerTransactionBridge;
        }
        retireContainerTransactionBridge(bridge, transportClosed);
    }

    private void retireContainerTransactionBridge(ContainerTransactionPacketBridge bridge, boolean transportClosed) {
        if (bridge == null) {
            return;
        }
        try {
            bridge.onBoundaryUnavailable(transportClosed);
        } catch (RuntimeException failure) {
            actionSessionGuard.markFirewallUnavailable();
            HorizonwrightMod.LOG.error("Container transaction packet bridge retirement failed", failure);
        }
    }

    private final class Registration {

        private final long id;
        private final NetworkManager manager;
        private final Channel channel;
        private OutboundPacketFirewall firewall;
        private DeathSafetyPacketBridge safetyBridge;
        private ContainerTransactionPacketBridge containerTransactionBridge;
        private boolean safetyBridgeRetired;
        private boolean containerTransactionBridgeRetired;
        private boolean boundaryFailed;
        private boolean installScheduled;
        private int installAttempts;
        private long drainScheduled;

        private Registration(long id, NetworkManager manager, Channel channel) {
            this.id = id;
            this.manager = manager;
            this.channel = channel;
        }

        @Override
        public String toString() {
            return "FirewallRegistration{" + id + '}';
        }
    }
}
