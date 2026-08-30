package io.github.kaseyawolf2.horizonwright.core.safety.death;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class DeathSafetyTestHarness {

    static final long FIRST_CONNECTION = 7L;
    static final String SERVER = "gtnh.example.test:25565";
    static final String WORLD = "world-identity-a";
    static final String OLD_PLAYER = "player-object-old";
    static final String NEW_PLAYER = "player-object-new";
    static final DimensionBlockPosition DEATH_POSITION = new DimensionBlockPosition(0, 10, 64, 10);
    static final DimensionBlockPosition GRAVE_POSITION = new DimensionBlockPosition(0, 11, 64, 10);

    final RecordingInterlock interlock;
    final DeathSafetyController controller;
    final DeathSafetyPolicy policy;
    final InventoryManifest preDeathInventory;
    final InventoryManifest emptyRespawnInventory;
    final GraveCandidate grave;

    private long connectionEpoch;
    private long sequence;
    private long tick;

    DeathSafetyTestHarness() {
        this(DeathSafetyPolicy.planDefaults(6));
    }

    DeathSafetyTestHarness(DeathSafetyPolicy policy) {
        this.policy = policy;
        interlock = new RecordingInterlock();
        connectionEpoch = FIRST_CONNECTION;
        preDeathInventory = inventory(
            36,
            stack("minecraft:diamond|meta=0|nbt=none", 12, 64),
            stack("gregtech:tool|meta=22|nbt=tool-a", 1, 1));
        emptyRespawnInventory = InventoryManifest.empty(36);
        grave = grave(
            "grave-a",
            GRAVE_POSITION,
            OLD_PLAYER,
            inventory(
                27,
                stack("minecraft:diamond|meta=0|nbt=none", 12, 64),
                stack("gregtech:tool|meta=22|nbt=tool-a", 1, 1)));
        controller = new DeathSafetyController(policy, interlock, connection(FIRST_CONNECTION, OLD_PLAYER));
    }

    static ConnectionIdentity connection(long epoch, String playerIdentity) {
        return new ConnectionIdentity(epoch, SERVER, WORLD, playerIdentity);
    }

    static InventoryStack stack(String key, int count, int maximum) {
        return new InventoryStack(key, count, maximum);
    }

    static InventoryManifest inventory(int slots, InventoryStack... stacks) {
        return new InventoryManifest(slots, Arrays.asList(stacks));
    }

    static GraveCandidate grave(String id, DimensionBlockPosition position, String owner, InventoryManifest contents) {
        return new GraveCandidate(new GraveIdentity(id, position), owner, contents);
    }

    DeathContext deathContext() {
        return new DeathContext(DEATH_POSITION, OLD_PLAYER, "active-excavation", preDeathInventory);
    }

    long latch() {
        DeathSafetyUpdate update = controller
            .onDeathSignal(nextTick(), DeathSignal.LOCAL_DEATH_CALLBACK, deathContext());
        assertEquals(
            DeathSafetyState.DEATH_LATCHED,
            update.getSnapshot()
                .getState());
        return update.getSnapshot()
            .getDeathEpoch();
    }

    void stabilizeRespawn(long deathEpoch) {
        stabilizeRespawn(deathEpoch, emptyRespawnInventory, new DimensionBlockPosition(0, 0, 64, 0));
    }

    void stabilizeRespawn(long deathEpoch, InventoryManifest inventory, DimensionBlockPosition position) {
        for (int i = 0; i < policy.getRespawnStableTicks(); i++) {
            controller.onRespawnObservation(
                nextTick(),
                deathEpoch,
                new RespawnObservation(NEW_PLAYER, 20.0D, false, true, true, position, inventory));
        }
        assertEquals(
            DeathSafetyState.RECOVERY_READY,
            controller.snapshot()
                .getState());
        assertEquals(
            RecoveryPhase.NAVIGATING_WITH_INTERACTIONS_DISABLED,
            controller.snapshot()
                .getRecoveryPhase());
    }

    void arriveAtDeath(long deathEpoch) {
        controller.onRecoveryNavigation(
            nextTick(),
            deathEpoch,
            new RecoveryNavigationObservation(
                RecoveryNavigationStatus.ARRIVED,
                DEATH_POSITION,
                emptyRespawnInventory,
                false,
                false));
        assertEquals(
            RecoveryPhase.SEARCHING_FOR_GRAVE,
            controller.snapshot()
                .getRecoveryPhase());
    }

    GraveActivationPermit stabilizeGrave(long deathEpoch) {
        return stabilizeGrave(deathEpoch, grave, emptyRespawnInventory, true);
    }

    GraveActivationPermit stabilizeGrave(long deathEpoch, GraveCandidate candidate, InventoryManifest currentInventory,
        boolean emptyHotbar) {
        for (int i = 0; i < policy.getGraveStableTicks(); i++) {
            controller.onGraveSearch(
                nextTick(),
                deathEpoch,
                new GraveSearchObservation(
                    GraveSearchStatus.COMPLETE,
                    Collections.singletonList(candidate),
                    currentInventory,
                    emptyHotbar));
        }
        assertEquals(
            RecoveryPhase.AWAITING_SCOPED_ACTIVATION,
            controller.snapshot()
                .getRecoveryPhase());
        return controller.snapshot()
            .getGraveActivationPermit()
            .orElseThrow(() -> new AssertionError("missing grave activation permit"));
    }

    GraveActivationPermit readyForActivation() {
        long epoch = latch();
        stabilizeRespawn(epoch);
        arriveAtDeath(epoch);
        return stabilizeGrave(epoch);
    }

    GraveActivationResult activate(GraveActivationPermit permit) {
        return controller.authorizeGraveActivation(
            nextTick(),
            new GraveActivationAttempt(
                permit.getPermitId(),
                permit.getDeathEpoch(),
                permit.getGraveIdentity(),
                true,
                true));
    }

    SafetyEventStamp nextTick() {
        tick++;
        return new SafetyEventStamp(connectionEpoch, ++sequence, tick);
    }

    SafetyEventStamp sameTick() {
        return new SafetyEventStamp(connectionEpoch, ++sequence, tick);
    }

    SafetyEventStamp stamp(long epoch, long eventSequence, long clientTick) {
        return new SafetyEventStamp(epoch, eventSequence, clientTick);
    }

    long getSequence() {
        return sequence;
    }

    long getTick() {
        return tick;
    }

    void disconnectAndReconnect(long nextConnectionEpoch, String playerIdentity) {
        controller.onDisconnect(nextTick());
        connectionEpoch = nextConnectionEpoch;
        sequence = 0L;
        tick = 0L;
        controller.reconnect(connection(nextConnectionEpoch, playerIdentity));
    }

    static final class RecordingInterlock implements DeathSafetyInterlock {

        int criticalEntries;
        int criticalReleases;
        int lockdownReaffirmations;
        int lockdownReleases;
        final List<DeathLatchRecord> latches = new ArrayList<>();
        Runnable latchInspector;

        @Override
        public void enterCriticalRestrictions() {
            criticalEntries++;
        }

        @Override
        public void releaseCriticalRestrictions() {
            criticalReleases++;
        }

        @Override
        public void latchDeath(DeathLatchRecord record) {
            latches.add(record);
            if (latchInspector != null) {
                latchInspector.run();
            }
        }

        @Override
        public void reaffirmDeathLockdown(long deathEpoch) {
            lockdownReaffirmations++;
        }

        @Override
        public void releaseDeathLockdown(long deathEpoch) {
            lockdownReleases++;
        }
    }
}
