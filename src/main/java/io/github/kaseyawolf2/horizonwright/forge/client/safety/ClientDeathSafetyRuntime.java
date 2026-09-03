package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiGameOver;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ConnectionIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathContext;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyPolicy;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyUpdate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationStatus;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryVerificationObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RespawnObservation;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;

/** Live per-connection composition for client snapshots, redundant death signals, and exact packet gates. */
public final class ClientDeathSafetyRuntime {

    private final HorizonwrightRuntime runtime;
    private final AutomationInputGate inputGate;
    private final MinecraftClientDeathInterlockDelegate interlockDelegate;
    private final BrokerBackedDeathSafetyInterlock interlock;
    private final DeathSafetyConnectionCoordinator connections;
    private final ClientDeathContextPublisher contextPublisher;
    private final DeathSafetyDirectiveProcessor directives;
    private final DeathSafetyDirectiveEffect effects;

    private DeathSafetyConnectionCoordinator.Session session;
    private InboundLethalHealthHook inboundHealthHook;
    private RespawnPacketWriteGate respawnWriteGate;
    private GraveActivationPacketWriteGate graveActivationWriteGate;

    public ClientDeathSafetyRuntime(HorizonwrightRuntime runtime, DeathSafetyPolicy policy,
        DeathSafetyDurableState durableState, DeathSafetyDirectiveEffect effects) {
        this(
            runtime,
            policy,
            durableState,
            effects,
            new MinecraftClientThreadVerifier(),
            new MinecraftClientDeathInterlockDelegate(runtime));
    }

    ClientDeathSafetyRuntime(HorizonwrightRuntime runtime, DeathSafetyPolicy policy,
        DeathSafetyDurableState durableState, DeathSafetyDirectiveEffect effects,
        ClientThreadVerifier clientThreadVerifier, ClientDeathInterlockDelegate interlockDelegate) {
        if (runtime == null || policy == null || durableState == null || effects == null) {
            throw new IllegalArgumentException("death-safety runtime dependencies must not be null");
        }
        if (clientThreadVerifier == null || interlockDelegate == null) {
            throw new IllegalArgumentException("death-safety client adapters must not be null");
        }
        this.runtime = runtime;
        this.effects = effects;
        inputGate = new AutomationInputGate();
        this.interlockDelegate = interlockDelegate instanceof MinecraftClientDeathInterlockDelegate
            ? (MinecraftClientDeathInterlockDelegate) interlockDelegate
            : null;
        interlock = new BrokerBackedDeathSafetyInterlock(runtime.getActionBroker(), inputGate, interlockDelegate);
        connections = new DeathSafetyConnectionCoordinator(policy, interlock);
        contextPublisher = new ClientDeathContextPublisher(clientThreadVerifier);
        directives = new DeathSafetyDirectiveProcessor(durableState);
    }

    public synchronized void openFresh(ConnectionIdentity identity) {
        installSession(connections.openFresh(identity));
    }

    public synchronized void restore(UnresolvedDeathState persisted, ConnectionIdentity identity) {
        if (persisted == null) {
            throw new IllegalArgumentException("persisted death state must not be null");
        }
        installSession(connections.restore(persisted, identity));
    }

    public synchronized DeathSafetySnapshot clientTick(long clientTick) {
        DeathSafetyConnectionCoordinator.Session active = requireSession();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.func_152345_ab()) {
            throw new IllegalStateException("death-safety clientTick must run on the Minecraft client thread");
        }
        EntityClientPlayerMP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null) {
            return active.getController()
                .snapshot();
        }

        MinecraftClientDeathContextSource source = new MinecraftClientDeathContextSource(minecraft, runtime);
        ClientDeathContextSnapshot context = contextPublisher.captureAndPublish(
            active.getIdentity()
                .getConnectionEpoch(),
            clientTick,
            source);
        double health = MinecraftRuntimeAccess.health(player);
        double maximumHealth = MinecraftRuntimeAccess.maximumHealth(player);
        DeathSafetyUpdate healthUpdate = active.getController()
            .onHealthObservation(
                active.getStamps()
                    .next(clientTick),
                health,
                maximumHealth,
                health <= 0.0D ? context.toDeathContext() : null);
        directives.process(healthUpdate, effects);

        if (player.isDead) {
            processDeathSignal(active, clientTick, DeathSignal.PLAYER_IS_DEAD, context.toDeathContext());
        }
        if (player.deathTime > 0) {
            processDeathSignal(active, clientTick, DeathSignal.POSITIVE_DEATH_TIME, context.toDeathContext());
        }
        if (minecraft.currentScreen instanceof GuiGameOver) {
            processDeathSignal(active, clientTick, DeathSignal.GAME_OVER_SCREEN, context.toDeathContext());
        }

        DeathSafetySnapshot current = active.getController()
            .snapshot();
        if (current.getDeathEpoch() > 0L) {
            RespawnObservation observation = RecoveryObservationFactory.respawn(
                context.getPlayerIdentity(),
                health,
                player.isDead,
                minecraft.theWorld != null,
                player.openContainer == player.inventoryContainer,
                context.getPlayerPosition(),
                source.getInventorySnapshot());
            DeathSafetyUpdate respawnUpdate = active.getController()
                .onRespawnObservation(
                    active.getStamps()
                        .next(clientTick),
                    current.getDeathEpoch(),
                    observation);
            directives.process(respawnUpdate, effects);
        }
        return active.getController()
            .snapshot();
    }

    public synchronized DeathSafetyUpdate disconnect(long clientTick) {
        DeathSafetyConnectionCoordinator.Session active = requireSession();
        try {
            DeathSafetyUpdate update = connections.disconnect(active, clientTick);
            directives.process(update, effects);
            return update;
        } finally {
            try {
                contextPublisher.clear(
                    active.getIdentity()
                        .getConnectionEpoch());
            } finally {
                session = null;
                inboundHealthHook = null;
                respawnWriteGate = null;
                graveActivationWriteGate = null;
            }
        }
    }

    /** Fails safely into manual hold when no tested recovery-navigation adapter is available. */
    public synchronized DeathSafetyUpdate failUnavailableRecoveryNavigation(long clientTick) {
        DeathSafetyConnectionCoordinator.Session active = requireSession();
        DeathSafetySnapshot current = active.getController()
            .snapshot();
        if (!current.getRecoveryNavigationRequest()
            .isPresent()) {
            throw new IllegalStateException("no recovery-navigation request is active");
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null || minecraft.theWorld == null) {
            throw new IllegalStateException("recovery-navigation fallback requires a joined client thread");
        }
        MinecraftClientDeathContextSource source = new MinecraftClientDeathContextSource(minecraft, runtime);
        DeathSafetyUpdate update = active.getController()
            .onRecoveryNavigation(
                active.getStamps()
                    .next(clientTick),
                current.getDeathEpoch(),
                RecoveryObservationFactory.navigation(
                    RecoveryNavigationStatus.FAILED,
                    source.getPlayerPosition(),
                    source.getInventorySnapshot(),
                    false,
                    false));
        directives.process(update, effects);
        return update;
    }

    public synchronized DeathSafetyUpdate observeRecoveryNavigation(long clientTick, RecoveryNavigationStatus status,
        DimensionBlockPosition playerPosition, ClientInventorySnapshot inventorySnapshot) {
        if (status == null || playerPosition == null || inventorySnapshot == null) {
            throw new IllegalArgumentException("recovery navigation observation must be complete");
        }
        DeathSafetyConnectionCoordinator.Session active = requireSession();
        DeathSafetySnapshot current = active.getController()
            .snapshot();
        DeathSafetyUpdate update = active.getController()
            .onRecoveryNavigation(
                active.getStamps()
                    .next(clientTick),
                current.getDeathEpoch(),
                RecoveryObservationFactory.navigation(status, playerPosition, inventorySnapshot, false, false));
        directives.process(update, effects);
        return update;
    }

    public synchronized DeathSafetyUpdate observeGraveSearch(long clientTick, GraveSearchObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("grave search observation must not be null");
        }
        DeathSafetyConnectionCoordinator.Session active = requireSession();
        DeathSafetySnapshot current = active.getController()
            .snapshot();
        DeathSafetyUpdate update = active.getController()
            .onGraveSearch(
                active.getStamps()
                    .next(clientTick),
                current.getDeathEpoch(),
                observation);
        directives.process(update, effects);
        return update;
    }

    public synchronized DeathSafetyUpdate observeRecoveryVerification(long clientTick,
        RecoveryVerificationObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("recovery verification observation must not be null");
        }
        DeathSafetyConnectionCoordinator.Session active = requireSession();
        DeathSafetySnapshot current = active.getController()
            .snapshot();
        DeathSafetyUpdate update = active.getController()
            .onRecoveryVerification(
                active.getStamps()
                    .next(clientTick),
                current.getDeathEpoch(),
                observation);
        directives.process(update, effects);
        return update;
    }

    public synchronized boolean hasActiveSession() {
        return session != null && connections.isActive(session);
    }

    public synchronized DeathSafetySnapshot snapshot() {
        return requireSession().getController()
            .snapshot();
    }

    public synchronized InboundLethalHealthHook getInboundHealthHook() {
        requireSession();
        return inboundHealthHook;
    }

    public synchronized RespawnPacketWriteGate getRespawnWriteGate() {
        requireSession();
        return respawnWriteGate;
    }

    public synchronized GraveActivationPacketWriteGate getGraveActivationWriteGate() {
        requireSession();
        return graveActivationWriteGate;
    }

    public AutomationInputGate getInputGate() {
        return inputGate;
    }

    public MinecraftClientDeathInterlockDelegate getInterlockDelegate() {
        if (interlockDelegate == null) {
            throw new IllegalStateException("the runtime is using a non-Minecraft death-interlock delegate");
        }
        return interlockDelegate;
    }

    private synchronized DeathSafetyConnectionCoordinator.Session requireSession() {
        if (session == null || !connections.isActive(session)) {
            throw new IllegalStateException("no live death-safety connection session");
        }
        return session;
    }

    private synchronized void installSession(DeathSafetyConnectionCoordinator.Session opened) {
        if (session != null) {
            throw new IllegalStateException("the previous death-safety session is still active");
        }
        session = opened;
        DeathSafetyController controller = opened.getController();
        inboundHealthHook = new InboundLethalHealthHook(
            controller,
            opened.getStamps(),
            contextPublisher,
            directives,
            effects);
        respawnWriteGate = new RespawnPacketWriteGate(controller, opened.getStamps(), directives);
        graveActivationWriteGate = new GraveActivationPacketWriteGate(
            controller,
            opened.getStamps(),
            directives,
            opened.getReplayBlock());
    }

    private void processDeathSignal(DeathSafetyConnectionCoordinator.Session active, long clientTick,
        DeathSignal signal, DeathContext context) {
        DeathSafetyUpdate update = active.getController()
            .onDeathSignal(
                active.getStamps()
                    .next(clientTick),
                signal,
                context);
        directives.process(update, effects);
    }
}
