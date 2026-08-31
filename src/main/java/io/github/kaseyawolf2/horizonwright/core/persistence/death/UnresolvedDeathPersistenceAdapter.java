package io.github.kaseyawolf2.horizonwright.core.persistence.death;

import io.github.kaseyawolf2.horizonwright.core.persistence.DimensionPosition;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistedGraveState;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistedInventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ConnectionIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationPermit;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveCandidate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;
import io.github.kaseyawolf2.horizonwright.core.safety.death.UnresolvedDeathProjection;

/** Lossless, pure-core adaptation between the death kernel and additive schema-v1 persistence. */
public final class UnresolvedDeathPersistenceAdapter {

    private UnresolvedDeathPersistenceAdapter() {}

    /** Captures the first persisted state and binds it to the connection which observed the death. */
    public static UnresolvedDeathState captureInitial(DeathLatchRecord latch, DeathSafetySnapshot snapshot,
        long recordedAtEpochMillis) {
        if (latch == null || snapshot == null) {
            throw new IllegalArgumentException("latch and snapshot must not be null");
        }
        UnresolvedDeathProjection projection = requireProjection(snapshot);
        if (latch.getDeathEpoch() != projection.getDeathEpoch() || !latch.getServerIdentity()
            .equals(projection.getServerIdentity())
            || !latch.getWorldIdentity()
                .equals(projection.getWorldIdentity())
            || !latch.getDeathPosition()
                .equals(projection.getDeathPosition())) {
            throw new IllegalArgumentException("death latch and unresolved projection identify different deaths");
        }
        return create(
            projection,
            latch.getConnectionEpoch(),
            snapshot,
            latch.getSignal(),
            PersistedGraveState.none(),
            recordedAtEpochMillis);
    }

    /**
     * Captures a later checkpoint while carrying the original death-connection binding and grave replay history.
     */
    public static UnresolvedDeathState captureCheckpoint(DeathSafetySnapshot snapshot, UnresolvedDeathState previous,
        long recordedAtEpochMillis) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        UnresolvedDeathProjection projection = requireProjection(snapshot);
        if (previous != null && previous.getDeathEpoch() != projection.getDeathEpoch()) {
            throw new IllegalArgumentException("previous persistence state belongs to a different death epoch");
        }
        long deathConnectionEpoch = previous == null ? snapshot.getConnectionEpoch()
            : previous.getDeathConnectionEpoch();
        PersistedGraveState graveState = captureGraveState(snapshot, previous);
        return create(
            projection,
            deathConnectionEpoch,
            snapshot,
            previous == null ? null : previous.getDeathSignal(),
            graveState,
            recordedAtEpochMillis);
    }

    /** Adapts a projection exactly when no richer snapshot or latch evidence is available. */
    public static UnresolvedDeathState fromProjection(UnresolvedDeathProjection projection, long deathConnectionEpoch,
        long lastObservedConnectionEpoch, boolean connectedAtCheckpoint, long recordedAtEpochMillis) {
        if (projection == null) {
            throw new IllegalArgumentException("projection must not be null");
        }
        return new UnresolvedDeathState(
            projection.getDeathEpoch(),
            deathConnectionEpoch,
            lastObservedConnectionEpoch,
            projection.getRecordedAtClientTick(),
            recordedAtEpochMillis,
            projection.getServerIdentity(),
            projection.getWorldIdentity(),
            toPersistencePosition(projection.getDeathPosition()),
            projection.getOldPlayerIdentity(),
            projection.getActiveTaskId()
                .orElse(null),
            projection.getPreDeathInventoryFingerprint(),
            projection.getPreDeathInventory()
                .map(PersistedInventoryManifest::fromManifest)
                .orElse(null),
            null,
            projection.getState(),
            projection.getRecoveryPhase(),
            projection.isRespawnRequestConsumed(),
            projection.getManualHoldReason()
                .orElse(null),
            connectedAtCheckpoint,
            0,
            0,
            projection.getRecoveryPhase() == RecoveryPhase.VERIFYING_RECOVERY
                ? new PersistedGraveState(null, null, 0, 0L, 0L, 0L, true)
                : PersistedGraveState.none());
    }

    /** Reconstructs every field represented by the death kernel's persistence-safe projection. */
    public static UnresolvedDeathProjection toProjection(UnresolvedDeathState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        boolean missingConsumedEvidence = state.getGraveState()
            .isActivationConsumed()
            && !state.getGraveState()
                .hasStableEvidence();
        return new UnresolvedDeathProjection(
            state.getDeathEpoch(),
            state.getRecordedAtClientTick(),
            state.getServerIdentity(),
            state.getWorldIdentity(),
            toDeathPosition(state.getDeathLocation()),
            state.getOldPlayerIdentity(),
            state.getActiveTaskId(),
            state.getPreDeathInventoryFingerprint(),
            state.getPreDeathInventory() == null ? null
                : state.getPreDeathInventory()
                    .toManifest(),
            missingConsumedEvidence ? null : toGraveCandidate(state.getGraveState()),
            state.getGraveState()
                .isActivationConsumed() && !missingConsumedEvidence,
            missingConsumedEvidence ? io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyState.MANUAL_HOLD
                : state.getSafetyState(),
            missingConsumedEvidence ? RecoveryPhase.MANUAL_HOLD : state.getRecoveryPhase(),
            state.isRespawnRequestConsumed(),
            missingConsumedEvidence
                ? io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason.GRAVE_EVIDENCE_UNAVAILABLE
                : state.getManualHoldReason());
    }

    /**
     * Validates the reconnect boundary and returns a projection for the death controller.
     *
     * <p>
     * The caller must additionally enforce {@link PersistedGraveState#requiresActivationReplayBlock()} at its
     * packet/action boundary because the current death projection intentionally does not carry grave activation
     * history.
     * </p>
     */
    public static UnresolvedDeathProjection prepareRestartProjection(UnresolvedDeathState state,
        ConnectionIdentity reconnectIdentity) {
        if (state == null || reconnectIdentity == null) {
            throw new IllegalArgumentException("state and reconnectIdentity must not be null");
        }
        if (reconnectIdentity.getConnectionEpoch() < state.minimumNextConnectionEpoch()) {
            throw new IllegalArgumentException("restart connection epoch must advance beyond persisted event history");
        }
        if (!state.getServerIdentity()
            .equals(reconnectIdentity.getServerIdentity())
            || !state.getWorldIdentity()
                .equals(reconnectIdentity.getWorldIdentity())) {
            throw new IllegalArgumentException("restart connection does not match the persisted death profile");
        }
        return toProjection(state.invalidateTransientPermitForRestart());
    }

    private static UnresolvedDeathState create(UnresolvedDeathProjection projection, long deathConnectionEpoch,
        DeathSafetySnapshot snapshot, DeathSignal deathSignal, PersistedGraveState graveState,
        long recordedAtEpochMillis) {
        return new UnresolvedDeathState(
            projection.getDeathEpoch(),
            deathConnectionEpoch,
            snapshot.getConnectionEpoch(),
            projection.getRecordedAtClientTick(),
            recordedAtEpochMillis,
            projection.getServerIdentity(),
            projection.getWorldIdentity(),
            toPersistencePosition(projection.getDeathPosition()),
            projection.getOldPlayerIdentity(),
            projection.getActiveTaskId()
                .orElse(null),
            projection.getPreDeathInventoryFingerprint(),
            projection.getPreDeathInventory()
                .map(PersistedInventoryManifest::fromManifest)
                .orElse(null),
            deathSignal,
            projection.getState(),
            projection.getRecoveryPhase(),
            projection.isRespawnRequestConsumed(),
            projection.getManualHoldReason()
                .orElse(null),
            snapshot.isConnected(),
            snapshot.getHealthyStableTicks(),
            snapshot.getRespawnStableTicks(),
            graveState);
    }

    private static PersistedGraveState captureGraveState(DeathSafetySnapshot snapshot, UnresolvedDeathState previous) {
        if (snapshot.getGraveActivationPermit()
            .isPresent()) {
            GraveActivationPermit permit = snapshot.getGraveActivationPermit()
                .get();
            GraveIdentity identity = permit.getGraveIdentity();
            return new PersistedGraveState(
                identity.getTileIdentity(),
                toPersistencePosition(identity.getPosition()),
                snapshot.getGraveStableTicks(),
                snapshot.getStableGrave()
                    .map(GraveCandidate::getOwnerIdentity)
                    .orElse(null),
                snapshot.getStableGrave()
                    .map(GraveCandidate::getContents)
                    .map(PersistedInventoryManifest::fromManifest)
                    .orElse(null),
                permit.getPermitId(),
                permit.getConnectionEpoch(),
                permit.getDeathEpoch(),
                false);
        }

        PersistedGraveState previousGrave = previous == null ? PersistedGraveState.none() : previous.getGraveState();
        boolean activationConsumed = previousGrave.isActivationConsumed()
            || snapshot.getRecoveryPhase() == RecoveryPhase.VERIFYING_RECOVERY;
        GraveCandidate currentGrave = snapshot.getStableGrave()
            .orElse(null);
        return new PersistedGraveState(
            currentGrave == null ? previousGrave.getGraveTileIdentity()
                : currentGrave.getIdentity()
                    .getTileIdentity(),
            currentGrave == null ? previousGrave.getGravePosition()
                : toPersistencePosition(
                    currentGrave.getIdentity()
                        .getPosition()),
            snapshot.getGraveStableTicks(),
            snapshot.getStableGrave()
                .map(GraveCandidate::getOwnerIdentity)
                .orElse(previousGrave.getOwnerIdentity()),
            snapshot.getStableGrave()
                .map(GraveCandidate::getContents)
                .map(PersistedInventoryManifest::fromManifest)
                .orElse(previousGrave.getContents()),
            0L,
            0L,
            0L,
            activationConsumed);
    }

    private static UnresolvedDeathProjection requireProjection(DeathSafetySnapshot snapshot) {
        return snapshot.getUnresolvedDeathProjection()
            .orElseThrow(() -> new IllegalArgumentException("snapshot has no unresolved death projection"));
    }

    private static DimensionPosition toPersistencePosition(DimensionBlockPosition position) {
        return new DimensionPosition(position.getDimensionId(), position.getX(), position.getY(), position.getZ());
    }

    private static DimensionBlockPosition toDeathPosition(DimensionPosition position) {
        return new DimensionBlockPosition(position.getDimensionId(), position.getX(), position.getY(), position.getZ());
    }

    private static GraveCandidate toGraveCandidate(PersistedGraveState state) {
        if (state == null || !state.hasStableEvidence()) {
            return null;
        }
        return new GraveCandidate(
            new GraveIdentity(state.getGraveTileIdentity(), toDeathPosition(state.getGravePosition())),
            state.getOwnerIdentity(),
            state.getContents()
                .toManifest());
    }
}
