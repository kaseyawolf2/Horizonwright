package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Objects;
import java.util.Optional;

/**
 * Persistence-safe projection of the fail-closed state.
 *
 * <p>
 * The persistence adapter must store this projection before allowing respawn or recovery progress. Raw inventory
 * NBT is intentionally omitted; only the canonical pre-death content fingerprint is retained.
 */
public final class UnresolvedDeathProjection {

    private final long deathEpoch;
    private final long recordedAtClientTick;
    private final String serverIdentity;
    private final String worldIdentity;
    private final DimensionBlockPosition deathPosition;
    private final String oldPlayerIdentity;
    private final String activeTaskId;
    private final String preDeathInventoryFingerprint;
    private final DeathSafetyState state;
    private final RecoveryPhase recoveryPhase;
    private final boolean respawnRequestConsumed;
    private final ManualHoldReason manualHoldReason;

    public UnresolvedDeathProjection(long deathEpoch, long recordedAtClientTick, String serverIdentity,
        String worldIdentity, DimensionBlockPosition deathPosition, String oldPlayerIdentity, String activeTaskId,
        String preDeathInventoryFingerprint, DeathSafetyState state, RecoveryPhase recoveryPhase,
        boolean respawnRequestConsumed, ManualHoldReason manualHoldReason) {
        if (deathEpoch <= 0L || recordedAtClientTick < 0L) {
            throw new IllegalArgumentException("deathEpoch must be positive and recorded tick nonnegative");
        }
        if (deathPosition == null || state == null || recoveryPhase == null) {
            throw new IllegalArgumentException("death position, state, and recovery phase must not be null");
        }
        if (state == DeathSafetyState.ACTIVE || state == DeathSafetyState.CRITICAL
            || recoveryPhase == RecoveryPhase.NONE) {
            throw new IllegalArgumentException("an unresolved death projection must remain safety latched");
        }
        if ((state == DeathSafetyState.MANUAL_HOLD) != (manualHoldReason != null)
            || (recoveryPhase == RecoveryPhase.MANUAL_HOLD) != (manualHoldReason != null)) {
            throw new IllegalArgumentException("manual hold state, phase, and reason must agree");
        }
        this.deathEpoch = deathEpoch;
        this.recordedAtClientTick = recordedAtClientTick;
        this.serverIdentity = ConnectionIdentity.requireText(serverIdentity, "serverIdentity");
        this.worldIdentity = ConnectionIdentity.requireText(worldIdentity, "worldIdentity");
        this.deathPosition = deathPosition;
        this.oldPlayerIdentity = ConnectionIdentity.requireText(oldPlayerIdentity, "oldPlayerIdentity");
        this.activeTaskId = activeTaskId == null || activeTaskId.trim()
            .isEmpty() ? null : activeTaskId.trim();
        this.preDeathInventoryFingerprint = ConnectionIdentity
            .requireText(preDeathInventoryFingerprint, "preDeathInventoryFingerprint");
        this.state = state;
        this.recoveryPhase = recoveryPhase;
        this.respawnRequestConsumed = respawnRequestConsumed;
        this.manualHoldReason = manualHoldReason;
    }

    public long getDeathEpoch() {
        return deathEpoch;
    }

    public long getRecordedAtClientTick() {
        return recordedAtClientTick;
    }

    public String getServerIdentity() {
        return serverIdentity;
    }

    public String getWorldIdentity() {
        return worldIdentity;
    }

    public DimensionBlockPosition getDeathPosition() {
        return deathPosition;
    }

    public String getOldPlayerIdentity() {
        return oldPlayerIdentity;
    }

    public Optional<String> getActiveTaskId() {
        return Optional.ofNullable(activeTaskId);
    }

    public String getPreDeathInventoryFingerprint() {
        return preDeathInventoryFingerprint;
    }

    public DeathSafetyState getState() {
        return state;
    }

    public RecoveryPhase getRecoveryPhase() {
        return recoveryPhase;
    }

    public boolean isRespawnRequestConsumed() {
        return respawnRequestConsumed;
    }

    public Optional<ManualHoldReason> getManualHoldReason() {
        return Optional.ofNullable(manualHoldReason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnresolvedDeathProjection)) {
            return false;
        }
        UnresolvedDeathProjection that = (UnresolvedDeathProjection) other;
        return deathEpoch == that.deathEpoch && recordedAtClientTick == that.recordedAtClientTick
            && respawnRequestConsumed == that.respawnRequestConsumed
            && serverIdentity.equals(that.serverIdentity)
            && worldIdentity.equals(that.worldIdentity)
            && deathPosition.equals(that.deathPosition)
            && oldPlayerIdentity.equals(that.oldPlayerIdentity)
            && Objects.equals(activeTaskId, that.activeTaskId)
            && preDeathInventoryFingerprint.equals(that.preDeathInventoryFingerprint)
            && state == that.state
            && recoveryPhase == that.recoveryPhase
            && manualHoldReason == that.manualHoldReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            deathEpoch,
            recordedAtClientTick,
            serverIdentity,
            worldIdentity,
            deathPosition,
            oldPlayerIdentity,
            activeTaskId,
            preDeathInventoryFingerprint,
            state,
            recoveryPhase,
            respawnRequestConsumed,
            manualHoldReason);
    }
}
