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
    private Registration current;
    private long nextRegistrationId = 1L;
    private volatile State state = State.ABSENT;
    private volatile String diagnostic = "No client play connection";

    public ClientPacketFirewallInstaller(ActionSessionGuard actionSessionGuard) {
        this.actionSessionGuard = actionSessionGuard;
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
                actionSessionGuard.markTransportClosed();
            } else {
                actionSessionGuard.markFirewallUnavailable();
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
        Registration staleGuardedRegistration = null;
        synchronized (this) {
            if (current == null || current.manager != manager || current.channel != channel) {
                if (current != null && current.channel.isOpen() && actionSessionGuard.isGuarding()) {
                    staleGuardedRegistration = current;
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
                }
            }
            if (state != State.INSTALLED && !registration.installScheduled) {
                scheduleInstallLocked(registration);
            }
        }
        if (staleGuardedRegistration != null) {
            HorizonwrightMod.LOG.warn("Closing stale guarded client connection during NetworkManager replacement");
            staleGuardedRegistration.channel.close();
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
        try {
            channel.pipeline()
                .addBefore("packet_handler", HANDLER_NAME, registration.firewall);
            markInstalled(registration);
        } catch (RuntimeException failure) {
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
        synchronized (this) {
            if (current == null || current.channel != context.channel() || current.firewall != context.handler()) {
                return;
            }
            if (transportClosed) {
                state = State.CLOSED;
                diagnostic = "Client connection closed";
            } else {
                current = new Registration(nextRegistrationId++, current.manager, current.channel);
                state = State.ABSENT;
                diagnostic = "Outbound action firewall handler removed";
            }
        }
        if (transportClosed) {
            actionSessionGuard.markTransportClosed();
        } else {
            actionSessionGuard.markFirewallUnavailable();
        }
    }

    private final class Registration {

        private final long id;
        private final NetworkManager manager;
        private final Channel channel;
        private final OutboundPacketFirewall firewall;
        private boolean installScheduled;
        private int installAttempts;
        private long drainScheduled;

        private Registration(long id, NetworkManager manager, Channel channel) {
            this.id = id;
            this.manager = manager;
            this.channel = channel;
            this.firewall = new OutboundPacketFirewall(actionSessionGuard, ClientPacketFirewallInstaller.this);
        }

        @Override
        public String toString() {
            return "FirewallRegistration{" + id + '}';
        }
    }
}
