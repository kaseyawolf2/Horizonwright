package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Optional;

/** Immutable view suitable for controller, task, dashboard, and packet gates. */
public final class DeathSafetySnapshot {

    private final DeathSafetyState state;
    private final RecoveryPhase recoveryPhase;
    private final boolean connected;
    private final long connectionEpoch;
    private final long deathEpoch;
    private final int healthyStableTicks;
    private final int respawnStableTicks;
    private final int graveStableTicks;
    private final boolean respawnRequestConsumed;
    private final ManualHoldReason manualHoldReason;
    private final GraveActivationPermit graveActivationPermit;
    private final RecoveryNavigationRequest recoveryNavigationRequest;
    private final UnresolvedDeathProjection unresolvedDeathProjection;

    DeathSafetySnapshot(DeathSafetyState state, RecoveryPhase recoveryPhase, boolean connected, long connectionEpoch,
        long deathEpoch, int healthyStableTicks, int respawnStableTicks, int graveStableTicks,
        boolean respawnRequestConsumed, ManualHoldReason manualHoldReason, GraveActivationPermit graveActivationPermit,
        RecoveryNavigationRequest recoveryNavigationRequest, UnresolvedDeathProjection unresolvedDeathProjection) {
        this.state = state;
        this.recoveryPhase = recoveryPhase;
        this.connected = connected;
        this.connectionEpoch = connectionEpoch;
        this.deathEpoch = deathEpoch;
        this.healthyStableTicks = healthyStableTicks;
        this.respawnStableTicks = respawnStableTicks;
        this.graveStableTicks = graveStableTicks;
        this.respawnRequestConsumed = respawnRequestConsumed;
        this.manualHoldReason = manualHoldReason;
        this.graveActivationPermit = graveActivationPermit;
        this.recoveryNavigationRequest = recoveryNavigationRequest;
        this.unresolvedDeathProjection = unresolvedDeathProjection;
    }

    public DeathSafetyState getState() {
        return state;
    }

    public RecoveryPhase getRecoveryPhase() {
        return recoveryPhase;
    }

    public boolean isConnected() {
        return connected;
    }

    public long getConnectionEpoch() {
        return connectionEpoch;
    }

    public long getDeathEpoch() {
        return deathEpoch;
    }

    public int getHealthyStableTicks() {
        return healthyStableTicks;
    }

    public int getRespawnStableTicks() {
        return respawnStableTicks;
    }

    public int getGraveStableTicks() {
        return graveStableTicks;
    }

    public boolean isRespawnRequestConsumed() {
        return respawnRequestConsumed;
    }

    public Optional<ManualHoldReason> getManualHoldReason() {
        return Optional.ofNullable(manualHoldReason);
    }

    public Optional<GraveActivationPermit> getGraveActivationPermit() {
        return Optional.ofNullable(graveActivationPermit);
    }

    public Optional<RecoveryNavigationRequest> getRecoveryNavigationRequest() {
        return Optional.ofNullable(recoveryNavigationRequest);
    }

    public Optional<UnresolvedDeathProjection> getUnresolvedDeathProjection() {
        return Optional.ofNullable(unresolvedDeathProjection);
    }

    public boolean areDangerousActionsAllowed() {
        return connected && state == DeathSafetyState.ACTIVE;
    }

    public boolean isMovementOnlyRetreatRequired() {
        return connected && state == DeathSafetyState.CRITICAL;
    }

    public boolean areAllAutomationInputOwnersBlocked() {
        return state != DeathSafetyState.ACTIVE && state != DeathSafetyState.CRITICAL;
    }

    public boolean areGenericRecoveryInteractionsDisabled() {
        return unresolvedDeathProjection != null;
    }

    public boolean isUnattendedOperationAllowed() {
        return connected && state == DeathSafetyState.ACTIVE && unresolvedDeathProjection == null;
    }
}
