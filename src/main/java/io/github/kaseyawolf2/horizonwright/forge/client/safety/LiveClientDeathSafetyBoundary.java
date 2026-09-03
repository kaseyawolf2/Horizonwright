package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.Collections;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.persistence.death.UnresolvedDeathPersistenceAdapter;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ConnectionIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyPolicy;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationAttempt;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationPermit;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveCandidate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchStatus;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketBridge;
import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketBridgeFactory;
import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketContext;
import io.github.kaseyawolf2.horizonwright.forge.client.network.GateBackedDeathSafetyPacketBridge;
import io.github.kaseyawolf2.horizonwright.forge.client.network.RetirementAwareDeathSafetyPacketBridge;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceException;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.RuntimeSessionConnection;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.RuntimeSessionDeathStateBoundary;
import io.netty.channel.Channel;

/** Live, durable death-safety owner shared by one runtime session and its exact Netty packet boundary. */
final class LiveClientDeathSafetyBoundary implements RuntimeSessionDeathStateBoundary, DeathSafetyDurableState,
    DeathSafetyPacketBridgeFactory, DeathSafetyPacketContext {

    interface RetirementListener {

        void onRetired(LiveClientDeathSafetyBoundary boundary);
    }

    private final HorizonwrightRuntime runtime;
    private final RuntimeSessionConnection connection;
    private final TaskControllerPersistenceCoordinator persistence;
    private final LiveDeathSafetyControls controls;
    private final ClientDeathSafetyRuntime deathSafety;
    private final RetirementListener retirementListener;
    private final int gravePlacementRadius;
    private final OpenBlocksGraveTileReader graveTileReader = new OpenBlocksGraveTileReader();
    private final GraveActivationPacketMatcher graveActivationPacketMatcher = new GraveActivationPacketMatcher();
    private final GravePreparationPacketMatcher gravePreparationPacketMatcher = new GravePreparationPacketMatcher();

    private UnresolvedDeathState unresolvedDeath;
    private long clientTick;
    private volatile double maximumHealth = 20.0D;
    private volatile GraveActivationPacketSnapshot graveActivationPacketSnapshot;
    private volatile GravePreparationPacketSnapshot gravePreparationPacketSnapshot;
    private GraveScanner graveScanner;
    private boolean graveAdapterFailureLogged;
    private boolean restored;
    private boolean disconnected;
    private boolean closed;

    LiveClientDeathSafetyBoundary(HorizonwrightRuntime runtime, RuntimeSessionConnection connection,
        HorizonwrightPersistenceStore store, DeathSafetyPolicy policy, RetirementListener retirementListener) {
        if (runtime == null || connection == null || store == null || policy == null || retirementListener == null) {
            throw new IllegalArgumentException("live death-safety boundary dependencies must not be null");
        }
        this.runtime = runtime;
        this.connection = connection;
        this.retirementListener = retirementListener;
        gravePlacementRadius = policy.getGravePlacementRadius();
        persistence = new TaskControllerPersistenceCoordinator(store, connection.getIdentity());
        controls = new LiveDeathSafetyControls(runtime);
        deathSafety = new ClientDeathSafetyRuntime(runtime, policy, this, controls.createEffect());
    }

    @Override
    public synchronized void restore(UnresolvedDeathState state) {
        ensureOpen();
        if (restored) {
            throw new IllegalStateException("live death-safety state has already been restored");
        }
        Minecraft minecraft = requireJoinedClientThread();
        MinecraftClientDeathContextSource source = new MinecraftClientDeathContextSource(minecraft, runtime);
        EntityClientPlayerMP player = minecraft.thePlayer;
        graveScanner = new MinecraftOpenBlocksGraveScanner(minecraft);
        maximumHealth = positiveMaximumHealth(MinecraftRuntimeAccess.maximumHealth(player));
        ConnectionIdentity identity = connectionIdentity(source.getPlayerIdentity());
        if (state == null) {
            deathSafety.openFresh(identity);
        } else {
            deathSafety.restore(state, identity);
        }
        unresolvedDeath = state;
        restored = true;
    }

    @Override
    public synchronized void clientTick() {
        ensureActive();
        Minecraft minecraft = requireJoinedClientThread();
        maximumHealth = positiveMaximumHealth(MinecraftRuntimeAccess.maximumHealth(minecraft.thePlayer));
        long tick = advanceClientTick();
        DeathSafetySnapshot snapshot = deathSafety.clientTick(tick);
        if (snapshot.getRecoveryPhase() == RecoveryPhase.NAVIGATING_WITH_INTERACTIONS_DISABLED) {
            Optional<io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationStatus> status = controls
                .pollRecoveryNavigation(snapshot.getDeathEpoch());
            if (status.isPresent()) {
                MinecraftClientDeathContextSource source = new MinecraftClientDeathContextSource(minecraft, runtime);
                snapshot = deathSafety
                    .observeRecoveryNavigation(
                        tick,
                        status.get(),
                        source.getPlayerPosition(),
                        source.getInventorySnapshot())
                    .getSnapshot();
            }
        }
        snapshot = advanceGraveRecovery(minecraft, tick, snapshot);
        gravePreparationPacketSnapshot = controls.prepareGraveActivation(minecraft, snapshot)
            .orElse(null);
        controls.sendGravePreparation(minecraft);
        refreshGraveActivationPacketSnapshot(minecraft, snapshot);
        controls.sendGraveActivation(minecraft, snapshot, graveActivationPacketSnapshot != null);
    }

    @Override
    public synchronized void disconnect() {
        ensureRestored();
        if (disconnected) {
            return;
        }
        try {
            deathSafety.disconnect(advanceClientTick());
        } finally {
            graveActivationPacketSnapshot = null;
            gravePreparationPacketSnapshot = null;
            disconnected = true;
        }
    }

    @Override
    public synchronized UnresolvedDeathState snapshot() {
        ensureRestored();
        return unresolvedDeath;
    }

    @Override
    public synchronized void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("death-safety snapshot must not be null");
        }
        ensureOpen();
        UnresolvedDeathState replacement = UnresolvedDeathPersistenceAdapter
            .captureCheckpoint(snapshot, unresolvedDeath, System.currentTimeMillis());
        save(replacement);
        unresolvedDeath = replacement;
    }

    @Override
    public synchronized void clearResolvedDeath() {
        ensureOpen();
        save(null);
        unresolvedDeath = null;
    }

    @Override
    public synchronized DeathSafetyPacketBridge open(NetworkManager manager, Channel channel) {
        if (manager == null || channel == null) {
            throw new IllegalArgumentException("network manager and channel must not be null");
        }
        ensureActive();
        return new RetirementAwareDeathSafetyPacketBridge(
            new GateBackedDeathSafetyPacketBridge(
                deathSafety.getInboundHealthHook(),
                deathSafety.getRespawnWriteGate(),
                deathSafety.getGraveActivationWriteGate(),
                this),
            this::isPacketBoundaryActive);
    }

    @Override
    public synchronized long getClientTick() {
        return clientTick;
    }

    @Override
    public double getMaximumHealth() {
        return maximumHealth;
    }

    @Override
    public synchronized long getActiveDeathEpoch() {
        return deathSafety.hasActiveSession() ? deathSafety.snapshot()
            .getDeathEpoch() : 0L;
    }

    @Override
    public Optional<GraveActivationAttempt> matchGraveActivation(C08PacketPlayerBlockPlacement packet) {
        if (packet == null) {
            throw new IllegalArgumentException("grave activation packet must not be null");
        }
        return graveActivationPacketMatcher.match(packet, graveActivationPacketSnapshot);
    }

    @Override
    public boolean matchesGravePreparation(Object packet) {
        return gravePreparationPacketMatcher.matches(packet, gravePreparationPacketSnapshot);
    }

    @Override
    public synchronized void onBoundaryUnavailable(boolean transportClosed) {
        if (closed || disconnected) {
            return;
        }
        if (transportClosed) {
            disconnect();
            return;
        }
        runtime.getActionBroker()
            .revokeAll();
        HorizonwrightMod.LOG.error(
            "Death-safety packet boundary became unavailable; Horizonwright automation stopped while unrelated "
                + "network traffic remains untouched");
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        RuntimeException failure = null;
        if (restored && !disconnected) {
            try {
                disconnect();
            } catch (RuntimeException disconnectFailure) {
                failure = disconnectFailure;
            }
        }
        closed = true;
        graveActivationPacketSnapshot = null;
        gravePreparationPacketSnapshot = null;
        retirementListener.onRetired(this);
        if (failure != null) {
            throw failure;
        }
    }

    private ConnectionIdentity connectionIdentity(String playerIdentity) {
        WorldProfileIdentity identity = connection.getIdentity();
        return new ConnectionIdentity(
            connection.getConnectionEpoch(),
            identity.getServerAddress(),
            identity.getWorldFingerprint(),
            playerIdentity);
    }

    private void save(UnresolvedDeathState state) {
        try {
            persistence
                .save(System.currentTimeMillis(), connection.getConnectionEpoch(), state, runtime.getController());
        } catch (TaskControllerPersistenceException failure) {
            throw new IllegalStateException("could not atomically persist live death-safety state", failure);
        }
    }

    private long advanceClientTick() {
        if (clientTick == Long.MAX_VALUE) {
            throw new IllegalStateException("death-safety client tick overflow");
        }
        return ++clientTick;
    }

    private void refreshGraveActivationPacketSnapshot(Minecraft minecraft, DeathSafetySnapshot snapshot) {
        Optional<GraveActivationPermit> activePermit = snapshot.getGraveActivationPermit();
        if (!activePermit.isPresent()) {
            graveActivationPacketSnapshot = null;
            return;
        }
        GraveActivationPermit permit = activePermit.get();
        GraveIdentity expected = permit.getGraveIdentity();
        if (minecraft.theWorld.provider.dimensionId != expected.getPosition()
            .getDimensionId()) {
            graveActivationPacketSnapshot = null;
            return;
        }
        try {
            Optional<OpenBlocksGraveTileEvidence> evidence = graveTileReader.read(
                MinecraftRuntimeAccess.tileEntity(
                    minecraft.theWorld,
                    expected.getPosition()
                        .getX(),
                    expected.getPosition()
                        .getY(),
                    expected.getPosition()
                        .getZ()),
                expected.getPosition());
            if (!evidence.isPresent() || !expected.equals(
                evidence.get()
                    .getIdentity())) {
                graveActivationPacketSnapshot = null;
                return;
            }
            graveActivationPacketSnapshot = new GraveActivationPacketSnapshot(
                permit,
                evidence.get()
                    .getIdentity(),
                minecraft.theWorld.provider.dimensionId,
                MinecraftRuntimeAccess.heldItem(minecraft.thePlayer) == null,
                MinecraftRuntimeAccess.isSneaking(minecraft.thePlayer));
        } catch (IllegalStateException unsupportedAdapter) {
            graveActivationPacketSnapshot = null;
            if (!graveAdapterFailureLogged) {
                graveAdapterFailureLogged = true;
                HorizonwrightMod.LOG.error(
                    "OpenBlocks grave adapter became unavailable; exact grave activation remains denied",
                    unsupportedAdapter);
            }
        }
    }

    private DeathSafetySnapshot advanceGraveRecovery(Minecraft minecraft, long tick, DeathSafetySnapshot snapshot) {
        RecoveryPhase phase = snapshot.getRecoveryPhase();
        if (phase != RecoveryPhase.SEARCHING_FOR_GRAVE && phase != RecoveryPhase.STABILIZING_GRAVE
            && phase != RecoveryPhase.VERIFYING_RECOVERY) {
            return snapshot;
        }
        MinecraftClientDeathContextSource source = new MinecraftClientDeathContextSource(minecraft, runtime);
        Optional<InventoryManifest> preDeathInventory = snapshot.getPreDeathInventory();
        if (!preDeathInventory.isPresent()) {
            if (phase == RecoveryPhase.SEARCHING_FOR_GRAVE || phase == RecoveryPhase.STABILIZING_GRAVE) {
                GraveRegionScan unavailable = new GraveRegionScan(
                    GraveSearchStatus.EVIDENCE_UNAVAILABLE,
                    Collections.<GraveCandidate>emptyList());
                return deathSafety
                    .observeGraveSearch(
                        tick,
                        RecoveryObservationFactory.graveSearch(
                            unavailable,
                            source.getInventorySnapshot(),
                            hasEmptyHotbarHand(minecraft.thePlayer)))
                    .getSnapshot();
            }
            return snapshot;
        }
        String oldPlayerIdentity = snapshot.getUnresolvedDeathProjection()
            .get()
            .getOldPlayerIdentity();
        String username = MinecraftRuntimeAccess.commandSenderName(minecraft.thePlayer);
        if (phase == RecoveryPhase.SEARCHING_FOR_GRAVE || phase == RecoveryPhase.STABILIZING_GRAVE) {
            Optional<InventoryManifest> expectedGraveContents = preDeathInventory.get()
                .subtractContents(
                    source.getInventorySnapshot()
                        .toManifest());
            if (!expectedGraveContents.isPresent()) {
                GraveRegionScan unavailable = new GraveRegionScan(
                    GraveSearchStatus.EVIDENCE_UNAVAILABLE,
                    Collections.<GraveCandidate>emptyList());
                return deathSafety
                    .observeGraveSearch(
                        tick,
                        RecoveryObservationFactory.graveSearch(
                            unavailable,
                            source.getInventorySnapshot(),
                            hasEmptyHotbarHand(minecraft.thePlayer)))
                    .getSnapshot();
            }
            GraveScanRequest request = new GraveScanRequest(
                snapshot.getUnresolvedDeathProjection()
                    .get()
                    .getDeathPosition(),
                gravePlacementRadius,
                oldPlayerIdentity,
                username,
                expectedGraveContents.get());
            GraveRegionScan scan = graveScanner.scanRegion(request);
            return deathSafety
                .observeGraveSearch(
                    tick,
                    RecoveryObservationFactory
                        .graveSearch(scan, source.getInventorySnapshot(), hasEmptyHotbarHand(minecraft.thePlayer)))
                .getSnapshot();
        }
        Optional<GraveCandidate> stableGrave = snapshot.getStableGrave();
        if (!stableGrave.isPresent()) {
            return snapshot;
        }
        GraveInspection inspection = graveScanner.inspectExact(
            new GraveInspectionRequest(
                stableGrave.get()
                    .getIdentity(),
                oldPlayerIdentity,
                username,
                stableGrave.get()
                    .getContents()));
        return deathSafety
            .observeRecoveryVerification(
                tick,
                RecoveryObservationFactory.verification(inspection, source.getInventorySnapshot()))
            .getSnapshot();
    }

    private static boolean hasEmptyHotbarHand(EntityClientPlayerMP player) {
        int hotbarSlots = Math.min(9, player.inventory.mainInventory.length);
        for (int slot = 0; slot < hotbarSlots; slot++) {
            if (player.inventory.mainInventory[slot] == null) {
                return true;
            }
        }
        return false;
    }

    private static Minecraft requireJoinedClientThread() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.func_152345_ab() || minecraft.theWorld == null || minecraft.thePlayer == null) {
            throw new IllegalStateException("live death safety requires a joined Minecraft client thread");
        }
        return minecraft;
    }

    private static double positiveMaximumHealth(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 20.0D;
    }

    private void ensureActive() {
        ensureRestored();
        if (disconnected) {
            throw new IllegalStateException("live death-safety connection is retired");
        }
    }

    private synchronized boolean isPacketBoundaryActive() {
        return restored && !disconnected && !closed;
    }

    private void ensureRestored() {
        ensureOpen();
        if (!restored) {
            throw new IllegalStateException("live death-safety state has not been restored");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("live death-safety boundary is closed");
        }
    }
}
