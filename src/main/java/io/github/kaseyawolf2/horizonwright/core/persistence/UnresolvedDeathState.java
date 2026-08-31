package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;

/** Schema-v1 durable form of every restart-relevant unresolved-death safety fact. */
public final class UnresolvedDeathState {

    private final long deathEpoch;
    private final long deathConnectionEpoch;
    private final long lastObservedConnectionEpoch;
    private final long recordedAtClientTick;
    private final long recordedAtEpochMillis;
    private final String serverIdentity;
    private final String worldIdentity;
    private final DimensionPosition deathLocation;
    private final String oldPlayerIdentity;
    private final String activeTaskId;
    private final String preDeathInventoryFingerprint;
    private final PersistedInventoryManifest preDeathInventory;
    private final DeathSignal deathSignal;
    private final DeathSafetyState safetyState;
    private final RecoveryPhase recoveryPhase;
    private final boolean respawnRequestConsumed;
    private final ManualHoldReason manualHoldReason;
    private final boolean connectedAtCheckpoint;
    private final int healthyStableTicks;
    private final int respawnStableTicks;
    private final PersistedGraveState graveState;

    public UnresolvedDeathState(long deathEpoch, long deathConnectionEpoch, long lastObservedConnectionEpoch,
        long recordedAtClientTick, long recordedAtEpochMillis, String serverIdentity, String worldIdentity,
        DimensionPosition deathLocation, String oldPlayerIdentity, String activeTaskId,
        String preDeathInventoryFingerprint, DeathSignal deathSignal, DeathSafetyState safetyState,
        RecoveryPhase recoveryPhase, boolean respawnRequestConsumed, ManualHoldReason manualHoldReason,
        boolean connectedAtCheckpoint, int healthyStableTicks, int respawnStableTicks, PersistedGraveState graveState) {
        this(
            deathEpoch,
            deathConnectionEpoch,
            lastObservedConnectionEpoch,
            recordedAtClientTick,
            recordedAtEpochMillis,
            serverIdentity,
            worldIdentity,
            deathLocation,
            oldPlayerIdentity,
            activeTaskId,
            preDeathInventoryFingerprint,
            null,
            deathSignal,
            safetyState,
            recoveryPhase,
            respawnRequestConsumed,
            manualHoldReason,
            connectedAtCheckpoint,
            healthyStableTicks,
            respawnStableTicks,
            graveState);
    }

    public UnresolvedDeathState(long deathEpoch, long deathConnectionEpoch, long lastObservedConnectionEpoch,
        long recordedAtClientTick, long recordedAtEpochMillis, String serverIdentity, String worldIdentity,
        DimensionPosition deathLocation, String oldPlayerIdentity, String activeTaskId,
        String preDeathInventoryFingerprint, PersistedInventoryManifest preDeathInventory, DeathSignal deathSignal,
        DeathSafetyState safetyState, RecoveryPhase recoveryPhase, boolean respawnRequestConsumed,
        ManualHoldReason manualHoldReason, boolean connectedAtCheckpoint, int healthyStableTicks,
        int respawnStableTicks, PersistedGraveState graveState) {
        this.deathEpoch = deathEpoch;
        this.deathConnectionEpoch = deathConnectionEpoch;
        this.lastObservedConnectionEpoch = lastObservedConnectionEpoch;
        this.recordedAtClientTick = recordedAtClientTick;
        this.recordedAtEpochMillis = recordedAtEpochMillis;
        this.serverIdentity = PersistenceValidation.requireText(serverIdentity, "serverIdentity");
        this.worldIdentity = PersistenceValidation.requireText(worldIdentity, "worldIdentity");
        this.deathLocation = deathLocation;
        this.oldPlayerIdentity = PersistenceValidation.requireText(oldPlayerIdentity, "oldPlayerIdentity");
        this.activeTaskId = PersistenceValidation.normalizeOptionalText(activeTaskId);
        this.preDeathInventoryFingerprint = PersistenceValidation
            .requireText(preDeathInventoryFingerprint, "preDeathInventoryFingerprint");
        this.preDeathInventory = preDeathInventory;
        this.deathSignal = deathSignal;
        this.safetyState = safetyState;
        this.recoveryPhase = recoveryPhase;
        this.respawnRequestConsumed = respawnRequestConsumed;
        this.manualHoldReason = manualHoldReason;
        this.connectedAtCheckpoint = connectedAtCheckpoint;
        this.healthyStableTicks = healthyStableTicks;
        this.respawnStableTicks = respawnStableTicks;
        this.graveState = graveState;
        validate();
    }

    public long getDeathEpoch() {
        return deathEpoch;
    }

    public long getDeathConnectionEpoch() {
        return deathConnectionEpoch;
    }

    public long getLastObservedConnectionEpoch() {
        return lastObservedConnectionEpoch;
    }

    public long getRecordedAtClientTick() {
        return recordedAtClientTick;
    }

    public long getRecordedAtEpochMillis() {
        return recordedAtEpochMillis;
    }

    public String getServerIdentity() {
        return serverIdentity;
    }

    public String getWorldIdentity() {
        return worldIdentity;
    }

    public DimensionPosition getDeathLocation() {
        return deathLocation;
    }

    public String getOldPlayerIdentity() {
        return oldPlayerIdentity;
    }

    public String getActiveTaskId() {
        return activeTaskId;
    }

    public String getPreDeathInventoryFingerprint() {
        return preDeathInventoryFingerprint;
    }

    public PersistedInventoryManifest getPreDeathInventory() {
        return preDeathInventory;
    }

    public DeathSignal getDeathSignal() {
        return deathSignal;
    }

    public DeathSafetyState getSafetyState() {
        return safetyState;
    }

    public RecoveryPhase getRecoveryPhase() {
        return recoveryPhase;
    }

    public boolean isRespawnRequestConsumed() {
        return respawnRequestConsumed;
    }

    public ManualHoldReason getManualHoldReason() {
        return manualHoldReason;
    }

    public boolean isConnectedAtCheckpoint() {
        return connectedAtCheckpoint;
    }

    public int getHealthyStableTicks() {
        return healthyStableTicks;
    }

    public int getRespawnStableTicks() {
        return respawnStableTicks;
    }

    public PersistedGraveState getGraveState() {
        return graveState;
    }

    /** A restart must advance beyond every connection epoch whose events this checkpoint accepted. */
    public long minimumNextConnectionEpoch() {
        if (lastObservedConnectionEpoch == Long.MAX_VALUE) {
            throw new IllegalStateException("connection epoch exhausted");
        }
        return lastObservedConnectionEpoch + 1L;
    }

    /** Returns a copy whose connection-bound grave permit is explicitly invalidated for restart. */
    public UnresolvedDeathState invalidateTransientPermitForRestart() {
        return new UnresolvedDeathState(
            deathEpoch,
            deathConnectionEpoch,
            lastObservedConnectionEpoch,
            recordedAtClientTick,
            recordedAtEpochMillis,
            serverIdentity,
            worldIdentity,
            deathLocation,
            oldPlayerIdentity,
            activeTaskId,
            preDeathInventoryFingerprint,
            preDeathInventory,
            deathSignal,
            safetyState,
            recoveryPhase,
            respawnRequestConsumed,
            manualHoldReason,
            connectedAtCheckpoint,
            healthyStableTicks,
            respawnStableTicks,
            graveState.invalidatePermitForRestart());
    }

    void validate() {
        if (deathEpoch <= 0L) {
            throw new IllegalArgumentException("unresolved death deathEpoch must be positive");
        }
        if (deathConnectionEpoch <= 0L || lastObservedConnectionEpoch < deathConnectionEpoch) {
            throw new IllegalArgumentException(
                "unresolved death connection epochs must be positive and monotonically nondecreasing");
        }
        PersistenceValidation.requireNonNegative(recordedAtClientTick, "unresolved death recordedAtClientTick");
        PersistenceValidation.requireNonNegative(recordedAtEpochMillis, "unresolved death recordedAtEpochMillis");
        PersistenceValidation.requireText(serverIdentity, "unresolved death serverIdentity");
        PersistenceValidation.requireText(worldIdentity, "unresolved death worldIdentity");
        if (deathLocation == null) {
            throw new IllegalArgumentException("unresolved death deathLocation must not be null");
        }
        deathLocation.validate();
        PersistenceValidation.requireText(oldPlayerIdentity, "unresolved death oldPlayerIdentity");
        PersistenceValidation
            .requireText(preDeathInventoryFingerprint, "unresolved death preDeathInventoryFingerprint");
        if (preDeathInventory != null) {
            preDeathInventory.validate();
            if (!preDeathInventoryFingerprint.equals(
                preDeathInventory.toManifest()
                    .getContentFingerprint())) {
                throw new IllegalArgumentException("persisted pre-death inventory does not match its fingerprint");
            }
        }
        if (safetyState == null || recoveryPhase == null) {
            throw new IllegalArgumentException("unresolved death safetyState and recoveryPhase must not be null");
        }
        if (safetyState == DeathSafetyState.ACTIVE || safetyState == DeathSafetyState.CRITICAL
            || recoveryPhase == RecoveryPhase.NONE) {
            throw new IllegalArgumentException("unresolved death must remain safety latched");
        }
        boolean manualHold = manualHoldReason != null;
        if ((safetyState == DeathSafetyState.MANUAL_HOLD) != manualHold
            || (recoveryPhase == RecoveryPhase.MANUAL_HOLD) != manualHold) {
            throw new IllegalArgumentException("manual-hold safety state, recovery phase, and reason must agree");
        }
        if (safetyState == DeathSafetyState.RESPAWN_REQUESTED && !respawnRequestConsumed) {
            throw new IllegalArgumentException(
                "RESPAWN_REQUESTED requires its exactly-once respawn request to be consumed");
        }
        if (healthyStableTicks < 0 || respawnStableTicks < 0) {
            throw new IllegalArgumentException("persisted safety stability counters must not be negative");
        }
        if (graveState == null) {
            throw new IllegalArgumentException("unresolved death graveState must not be null");
        }
        graveState.validate();
        if (graveState.hasTransientActivationPermit()) {
            if (graveState.getActivationPermitDeathEpoch() != deathEpoch
                || graveState.getActivationPermitConnectionEpoch() != lastObservedConnectionEpoch) {
                throw new IllegalArgumentException(
                    "grave activation permit must match the death and last observed connection epochs");
            }
        }
        if (recoveryPhase == RecoveryPhase.VERIFYING_RECOVERY && !graveState.isActivationConsumed()) {
            throw new IllegalArgumentException("recovery verification requires a consumed grave activation");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnresolvedDeathState)) {
            return false;
        }
        UnresolvedDeathState that = (UnresolvedDeathState) other;
        return deathEpoch == that.deathEpoch && deathConnectionEpoch == that.deathConnectionEpoch
            && lastObservedConnectionEpoch == that.lastObservedConnectionEpoch
            && recordedAtClientTick == that.recordedAtClientTick
            && recordedAtEpochMillis == that.recordedAtEpochMillis
            && respawnRequestConsumed == that.respawnRequestConsumed
            && connectedAtCheckpoint == that.connectedAtCheckpoint
            && healthyStableTicks == that.healthyStableTicks
            && respawnStableTicks == that.respawnStableTicks
            && Objects.equals(serverIdentity, that.serverIdentity)
            && Objects.equals(worldIdentity, that.worldIdentity)
            && Objects.equals(deathLocation, that.deathLocation)
            && Objects.equals(oldPlayerIdentity, that.oldPlayerIdentity)
            && Objects.equals(activeTaskId, that.activeTaskId)
            && Objects.equals(preDeathInventoryFingerprint, that.preDeathInventoryFingerprint)
            && Objects.equals(preDeathInventory, that.preDeathInventory)
            && deathSignal == that.deathSignal
            && safetyState == that.safetyState
            && recoveryPhase == that.recoveryPhase
            && manualHoldReason == that.manualHoldReason
            && Objects.equals(graveState, that.graveState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            deathEpoch,
            deathConnectionEpoch,
            lastObservedConnectionEpoch,
            recordedAtClientTick,
            recordedAtEpochMillis,
            serverIdentity,
            worldIdentity,
            deathLocation,
            oldPlayerIdentity,
            activeTaskId,
            preDeathInventoryFingerprint,
            preDeathInventory,
            deathSignal,
            safetyState,
            recoveryPhase,
            respawnRequestConsumed,
            manualHoldReason,
            connectedAtCheckpoint,
            healthyStableTicks,
            respawnStableTicks,
            graveState);
    }
}
