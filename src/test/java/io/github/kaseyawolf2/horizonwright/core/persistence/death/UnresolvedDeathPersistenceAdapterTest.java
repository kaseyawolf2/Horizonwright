package io.github.kaseyawolf2.horizonwright.core.persistence.death;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.persistence.DimensionPosition;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistedGraveState;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileStatePaths;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ConnectionIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathContext;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyInterlock;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyPolicy;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyUpdate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;
import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;
import io.github.kaseyawolf2.horizonwright.core.safety.death.UnresolvedDeathProjection;

public class UnresolvedDeathPersistenceAdapterTest {

    private static final String PROFILE = "death-profile";
    private static final String SERVER = "server:25565";
    private static final String WORLD = "world-fingerprint";
    private static final String OLD_PLAYER = "old-player";
    private static final String NEW_PLAYER = "new-player";
    private static final DimensionBlockPosition DEATH_POSITION = new DimensionBlockPosition(0, 10, 64, -5);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void consumedRespawnSurvivesCheckpointJsonRoundTripAndControllerRestart() throws Exception {
        RecordingInterlock firstInterlock = new RecordingInterlock();
        DeathSafetyController controller = new DeathSafetyController(
            DeathSafetyPolicy.planDefaults(10),
            firstInterlock,
            new ConnectionIdentity(5L, SERVER, WORLD, OLD_PLAYER));
        InventoryManifest inventory = InventoryManifest.empty(36);
        DeathSafetyUpdate latched = controller.onDeathSignal(
            new SafetyEventStamp(5L, 1L, 100L),
            DeathSignal.LETHAL_HEALTH_PACKET,
            new DeathContext(DEATH_POSITION, OLD_PLAYER, "active-task", inventory));
        UnresolvedDeathState initial = UnresolvedDeathPersistenceAdapter
            .captureInitial(firstInterlock.lastLatch, latched.getSnapshot(), 1_000L);

        DeathSafetyUpdate respawn = controller.authorizeRespawnPacket(
            new SafetyEventStamp(5L, 2L, 101L),
            latched.getSnapshot()
                .getDeathEpoch());
        UnresolvedDeathState checkpoint = UnresolvedDeathPersistenceAdapter
            .captureCheckpoint(respawn.getSnapshot(), initial, 1_100L);
        assertTrue(checkpoint.isRespawnRequestConsumed());
        assertEquals(5L, checkpoint.getDeathConnectionEpoch());
        assertEquals(5L, checkpoint.getLastObservedConnectionEpoch());

        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile(PROFILE);
        store.saveRuntime(paths, new RuntimeEnvelope(1_200L, PROFILE, SERVER, WORLD, checkpoint));
        UnresolvedDeathState reloaded = store.loadRuntime(paths)
            .getValue()
            .getUnresolvedDeathState();
        assertEquals(checkpoint, reloaded);

        ConnectionIdentity restartConnection = new ConnectionIdentity(
            reloaded.minimumNextConnectionEpoch(),
            SERVER,
            WORLD,
            NEW_PLAYER);
        UnresolvedDeathProjection restartProjection = UnresolvedDeathPersistenceAdapter
            .prepareRestartProjection(reloaded, restartConnection);
        RecordingInterlock restartedInterlock = new RecordingInterlock();
        DeathSafetyController restarted = DeathSafetyController
            .restore(DeathSafetyPolicy.planDefaults(10), restartedInterlock, restartConnection, restartProjection);
        DeathSafetyUpdate repeated = restarted.authorizeRespawnPacket(
            new SafetyEventStamp(restartConnection.getConnectionEpoch(), 1L, 1L),
            reloaded.getDeathEpoch());

        assertFalse(repeated.hasDirective(DeathSafetyDirective.SEND_EXACTLY_ONE_RESPAWN));
        assertEquals(1, restartedInterlock.reaffirmations);
    }

    @Test
    public void manualHoldProjectionRoundTripPreservesItsExactReasonAndDetailedPhase() {
        UnresolvedDeathProjection projection = new UnresolvedDeathProjection(
            13L,
            900L,
            SERVER,
            WORLD,
            DEATH_POSITION,
            OLD_PLAYER,
            "active-task",
            "inventory-fingerprint",
            DeathSafetyState.MANUAL_HOLD,
            RecoveryPhase.MANUAL_HOLD,
            true,
            ManualHoldReason.GRAVE_MISSING);

        UnresolvedDeathState persisted = UnresolvedDeathPersistenceAdapter
            .fromProjection(projection, 2L, 4L, false, 1_000L);
        UnresolvedDeathProjection adapted = UnresolvedDeathPersistenceAdapter.toProjection(persisted);

        assertEquals(projection, adapted);
        assertEquals(ManualHoldReason.GRAVE_MISSING, persisted.getManualHoldReason());
        assertEquals(RecoveryPhase.MANUAL_HOLD, persisted.getRecoveryPhase());
        assertTrue(persisted.isRespawnRequestConsumed());
    }

    @Test
    public void gravePermitAuditAndConsumedActivationRemainLosslessButPermitIsNeverRestarted() throws Exception {
        PersistedGraveState permit = new PersistedGraveState(
            "grave-tile-41",
            new DimensionPosition(0, 11, 64, -5),
            40,
            41L,
            7L,
            41L,
            false);
        UnresolvedDeathState awaiting = state(
            300L,
            DeathSafetyState.RECOVERY_READY,
            RecoveryPhase.AWAITING_SCOPED_ACTIVATION,
            permit);
        PersistedGraveState consumedGrave = new PersistedGraveState(
            "grave-tile-41",
            new DimensionPosition(0, 11, 64, -5),
            40,
            0L,
            0L,
            0L,
            true);
        UnresolvedDeathState consumed = state(
            400L,
            DeathSafetyState.RECOVERY_READY,
            RecoveryPhase.VERIFYING_RECOVERY,
            consumedGrave);
        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile(PROFILE);

        store.saveRuntime(paths, new RuntimeEnvelope(350L, PROFILE, SERVER, WORLD, awaiting));
        store.saveRuntime(paths, new RuntimeEnvelope(450L, PROFILE, SERVER, WORLD, consumed));

        UnresolvedDeathState loadedConsumed = store.loadRuntime(paths)
            .getValue()
            .getUnresolvedDeathState();
        UnresolvedDeathState loadedAwaiting = store.loadRuntimeBackup(paths)
            .getValue()
            .getUnresolvedDeathState();
        assertEquals(consumed, loadedConsumed);
        assertTrue(
            loadedConsumed.getGraveState()
                .requiresActivationReplayBlock());
        assertEquals(awaiting, loadedAwaiting);
        assertTrue(
            loadedAwaiting.getGraveState()
                .hasTransientActivationPermit());

        UnresolvedDeathState restartState = loadedAwaiting.invalidateTransientPermitForRestart();
        assertFalse(
            restartState.getGraveState()
                .hasTransientActivationPermit());
        assertTrue(
            restartState.getGraveState()
                .hasGraveIdentity());
        assertEquals(
            "grave-tile-41",
            restartState.getGraveState()
                .getGraveTileIdentity());
    }

    private HorizonwrightPersistenceStore store() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        return new HorizonwrightPersistenceStore(root);
    }

    private static UnresolvedDeathState state(long recordedAtEpochMillis, DeathSafetyState safetyState,
        RecoveryPhase recoveryPhase, PersistedGraveState graveState) {
        return new UnresolvedDeathState(
            41L,
            7L,
            7L,
            200L,
            recordedAtEpochMillis,
            SERVER,
            WORLD,
            new DimensionPosition(0, 10, 64, -5),
            OLD_PLAYER,
            "active-task",
            "inventory-fingerprint",
            DeathSignal.LETHAL_HEALTH_PACKET,
            safetyState,
            recoveryPhase,
            true,
            null,
            true,
            0,
            20,
            graveState);
    }

    private static final class RecordingInterlock implements DeathSafetyInterlock {

        private DeathLatchRecord lastLatch;
        private int reaffirmations;

        @Override
        public void enterCriticalRestrictions() {}

        @Override
        public void releaseCriticalRestrictions() {}

        @Override
        public void latchDeath(DeathLatchRecord record) {
            lastLatch = record;
        }

        @Override
        public void reaffirmDeathLockdown(long deathEpoch) {
            reaffirmations++;
        }

        @Override
        public void releaseDeathLockdown(long deathEpoch) {}
    }
}
