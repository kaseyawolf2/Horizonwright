package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic, per-connection item-preservation authority.
 *
 * <p>
 * All methods are synchronized so an inbound lethal-health hook, client tick, disconnect callback, and outbound
 * activation boundary cannot interleave state transitions. Async producers must carry both the current connection
 * epoch and their originating death epoch; stale evidence is rejected without mutation.
 */
public final class DeathSafetyController {

    private static final DimensionBlockPosition UNKNOWN_DEATH_POSITION = new DimensionBlockPosition(
        Integer.MIN_VALUE,
        Integer.MIN_VALUE,
        Integer.MIN_VALUE,
        Integer.MIN_VALUE);
    private static final InventoryManifest UNKNOWN_PRE_DEATH_INVENTORY = InventoryManifest.empty(0);

    private static final Set<DeathSafetyDirective> FIRST_LATCH_DIRECTIVES = EnumSet.of(
        DeathSafetyDirective.FORCE_CHECKPOINT_ACTIVE_TASK,
        DeathSafetyDirective.CANCEL_ALL_NAVIGATION_AND_PENDING_WORK,
        DeathSafetyDirective.REVOKE_ALL_ACTION_LEASES,
        DeathSafetyDirective.CLEAR_ALL_INPUT_AND_KEYBINDINGS,
        DeathSafetyDirective.CLEAR_NAVIGATION_PRIVATE_INPUT,
        DeathSafetyDirective.RELEASE_ALL_HELD_USE,
        DeathSafetyDirective.INVALIDATE_ACTION_AND_CONTAINER_EPOCHS,
        DeathSafetyDirective.ENGAGE_DEATH_LOCKDOWN,
        DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH);

    private final DeathSafetyPolicy policy;
    private final DeathSafetyInterlock interlock;

    private boolean connected = true;
    private long connectionEpoch;
    private String currentServerIdentity;
    private String currentWorldIdentity;
    private String currentPlayerIdentity;
    private long lastEventSequence;
    private long lastClientTick = -1L;

    private DeathSafetyState state = DeathSafetyState.ACTIVE;
    private RecoveryPhase recoveryPhase = RecoveryPhase.NONE;
    private long nextDeathEpoch = 1L;
    private long deathEpoch;
    private long deathRecordedAtTick;
    private String deathServerIdentity;
    private String deathWorldIdentity;
    private DimensionBlockPosition deathPosition;
    private String oldPlayerIdentity;
    private String activeTaskId;
    private String preDeathInventoryFingerprint;
    private InventoryManifest preDeathInventory;
    private boolean respawnRequestConsumed;
    private ManualHoldReason manualHoldReason;
    private String lastResolvedOldPlayerIdentity;

    private int healthyStableTicks;
    private long lastHealthyTick = -1L;
    private int respawnStableTicks;
    private long lastRespawnTick = -1L;
    private String respawnPlayerIdentity;
    private int respawnDimension;
    private InventoryManifest respawnInventoryAnchor;
    private InventoryManifest baselineRespawnInventory;
    private int graveStableTicks;
    private long lastGraveTick = -1L;
    private GraveCandidate stableGrave;
    private GraveActivationPermit graveActivationPermit;
    private boolean graveActivationConsumed;

    public DeathSafetyController(DeathSafetyPolicy policy, DeathSafetyInterlock interlock,
        ConnectionIdentity connection) {
        if (policy == null || interlock == null || connection == null) {
            throw new IllegalArgumentException("policy, interlock, and connection must not be null");
        }
        this.policy = policy;
        this.interlock = interlock;
        applyConnection(connection);
    }

    /** Restores unresolved state and synchronously reasserts lockdown before returning. */
    public static DeathSafetyController restore(DeathSafetyPolicy policy, DeathSafetyInterlock interlock,
        ConnectionIdentity connection, UnresolvedDeathProjection projection) {
        if (projection == null) {
            throw new IllegalArgumentException("projection must not be null");
        }
        DeathSafetyController controller = new DeathSafetyController(policy, interlock, connection);
        controller.deathEpoch = projection.getDeathEpoch();
        if (projection.getDeathEpoch() == Long.MAX_VALUE) {
            controller.nextDeathEpoch = Long.MAX_VALUE;
        } else {
            controller.nextDeathEpoch = projection.getDeathEpoch() + 1L;
        }
        controller.deathRecordedAtTick = projection.getRecordedAtClientTick();
        controller.deathServerIdentity = projection.getServerIdentity();
        controller.deathWorldIdentity = projection.getWorldIdentity();
        controller.deathPosition = projection.getDeathPosition();
        controller.oldPlayerIdentity = projection.getOldPlayerIdentity();
        controller.activeTaskId = projection.getActiveTaskId()
            .orElse(null);
        controller.preDeathInventoryFingerprint = projection.getPreDeathInventoryFingerprint();
        controller.preDeathInventory = projection.getPreDeathInventory()
            .orElse(null);
        controller.respawnRequestConsumed = projection.isRespawnRequestConsumed();
        controller.state = projection.getState();
        controller.manualHoldReason = projection.getManualHoldReason()
            .orElse(null);
        controller.stableGrave = projection.getStableGrave()
            .orElse(null);
        controller.graveActivationConsumed = projection.isGraveActivationConsumed();
        controller.recoveryPhase = controller.manualHoldReason != null ? RecoveryPhase.MANUAL_HOLD
            : controller.graveActivationConsumed ? RecoveryPhase.VERIFYING_RECOVERY
                : RecoveryPhase.REVALIDATING_RESPAWN;
        if (!controller.profileMatchesCurrentDeath()) {
            controller.state = DeathSafetyState.MANUAL_HOLD;
            controller.recoveryPhase = RecoveryPhase.MANUAL_HOLD;
            controller.manualHoldReason = ManualHoldReason.PROFILE_MISMATCH;
        }
        interlock.reaffirmDeathLockdown(controller.deathEpoch);
        return controller;
    }

    public synchronized DeathSafetyUpdate onHealthObservation(SafetyEventStamp stamp, double health,
        double maximumHealth, DeathContext lethalContext) {
        if (!Double.isFinite(health) || !Double.isFinite(maximumHealth) || maximumHealth <= 0.0D) {
            throw new IllegalArgumentException("health values must be finite and maximumHealth positive");
        }
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        if (health <= 0.0D) {
            if (lethalContext == null) {
                throw new IllegalArgumentException("lethal health requires synchronously captured death context");
            }
            return latchOrRedundant(stamp, DeathSignal.LETHAL_HEALTH_PACKET, lethalContext);
        }
        if (hasUnresolvedDeath()) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }

        double fraction = health / maximumHealth;
        if (fraction <= policy.getCriticalHealthFraction()) {
            resetHealthyStability();
            if (state == DeathSafetyState.ACTIVE) {
                interlock.enterCriticalRestrictions();
                state = DeathSafetyState.CRITICAL;
                return update(DeathSafetyEventDisposition.ACCEPTED, DeathSafetyDirective.ENTER_CRITICAL_RESTRICTIONS);
            }
            return update(DeathSafetyEventDisposition.ACCEPTED);
        }

        if (state == DeathSafetyState.CRITICAL && fraction > policy.getRecoveredHealthFraction()) {
            healthyStableTicks = consecutiveCount(healthyStableTicks, lastHealthyTick, stamp.getClientTick());
            lastHealthyTick = stamp.getClientTick();
            if (healthyStableTicks >= policy.getRecoveredHealthStableTicks()) {
                interlock.releaseCriticalRestrictions();
                state = DeathSafetyState.ACTIVE;
                resetHealthyStability();
                return update(DeathSafetyEventDisposition.ACCEPTED, DeathSafetyDirective.RELEASE_CRITICAL_RESTRICTIONS);
            }
            return update(DeathSafetyEventDisposition.ACCEPTED);
        }

        if (state == DeathSafetyState.CRITICAL) {
            resetHealthyStability();
        }
        return update(DeathSafetyEventDisposition.ACCEPTED);
    }

    /**
     * Fail-safe lethal observation used when the network boundary has no usable client-thread baseline.
     *
     * <p>
     * The placeholder position and inventory are never eligible for automatic recovery because this transition enters
     * manual hold immediately. Persisting the explicit reason is safer than either fabricating recoverable evidence or
     * throwing back into the inbound packet path.
     */
    public synchronized DeathSafetyUpdate onLethalHealthWithoutContext(SafetyEventStamp stamp) {
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        if (hasUnresolvedDeath()) {
            return state == DeathSafetyState.MANUAL_HOLD ? update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE)
                : enterManualHold(ManualHoldReason.PRE_DEATH_CONTEXT_UNAVAILABLE);
        }
        DeathContext unavailableContext = new DeathContext(
            UNKNOWN_DEATH_POSITION,
            currentPlayerIdentity,
            null,
            UNKNOWN_PRE_DEATH_INVENTORY);
        return latchFirstDeath(
            stamp,
            DeathSignal.LETHAL_HEALTH_PACKET,
            unavailableContext,
            ManualHoldReason.PRE_DEATH_CONTEXT_UNAVAILABLE);
    }

    public synchronized DeathSafetyUpdate onDeathSignal(SafetyEventStamp stamp, DeathSignal signal,
        DeathContext context) {
        if (signal == null || context == null) {
            throw new IllegalArgumentException("signal and context must not be null");
        }
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        return latchOrRedundant(stamp, signal, context);
    }

    /** Called by the actual outbound C16 write boundary; authorization is consumed here. */
    public synchronized DeathSafetyUpdate authorizeRespawnPacket(SafetyEventStamp stamp, long requestedDeathEpoch) {
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        if (!hasUnresolvedDeath() || state == DeathSafetyState.MANUAL_HOLD) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        if (requestedDeathEpoch != deathEpoch) {
            return update(DeathSafetyEventDisposition.STALE_DEATH_EPOCH);
        }
        if (respawnRequestConsumed) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        respawnRequestConsumed = true;
        if (state == DeathSafetyState.DEATH_LATCHED) {
            state = DeathSafetyState.RESPAWN_REQUESTED;
        }
        return update(
            DeathSafetyEventDisposition.ACCEPTED,
            DeathSafetyDirective.SEND_EXACTLY_ONE_RESPAWN,
            DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH);
    }

    public synchronized DeathSafetyUpdate onRespawnObservation(SafetyEventStamp stamp, long observedDeathEpoch,
        RespawnObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation must not be null");
        }
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        if (!hasUnresolvedDeath() || state == DeathSafetyState.MANUAL_HOLD) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        if (observedDeathEpoch != deathEpoch) {
            return update(DeathSafetyEventDisposition.STALE_DEATH_EPOCH);
        }
        if (recoveryPhase != RecoveryPhase.AWAITING_RESPAWN && recoveryPhase != RecoveryPhase.REVALIDATING_RESPAWN
            && recoveryPhase != RecoveryPhase.RESPAWN_STABILIZING) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }

        boolean valid = !oldPlayerIdentity.equals(observation.getPlayerIdentity()) && observation.getHealth() > 0.0D
            && !observation.isDead()
            && observation.isWorldLoaded()
            && observation.isNormalInventoryContainer();
        if (!valid) {
            resetRespawnStability();
            return update(DeathSafetyEventDisposition.ACCEPTED);
        }
        if (state == DeathSafetyState.DEATH_LATCHED || state == DeathSafetyState.RESPAWN_REQUESTED) {
            state = DeathSafetyState.POST_RESPAWN_QUARANTINE;
        }
        recoveryPhase = RecoveryPhase.RESPAWN_STABILIZING;

        boolean sameAnchor = observation.getPlayerIdentity()
            .equals(respawnPlayerIdentity)
            && observation.getPlayerPosition()
                .getDimensionId() == respawnDimension
            && respawnInventoryAnchor != null
            && respawnInventoryAnchor.hasSameContents(observation.getInventory());
        if (!sameAnchor) {
            respawnPlayerIdentity = observation.getPlayerIdentity();
            respawnDimension = observation.getPlayerPosition()
                .getDimensionId();
            respawnInventoryAnchor = observation.getInventory();
            respawnStableTicks = 1;
        } else {
            respawnStableTicks = consecutiveCount(respawnStableTicks, lastRespawnTick, stamp.getClientTick());
        }
        lastRespawnTick = stamp.getClientTick();

        if (respawnStableTicks < policy.getRespawnStableTicks()) {
            return update(DeathSafetyEventDisposition.ACCEPTED);
        }
        currentPlayerIdentity = observation.getPlayerIdentity();
        baselineRespawnInventory = observation.getInventory();
        state = DeathSafetyState.RECOVERY_READY;
        recoveryPhase = RecoveryPhase.NAVIGATING_WITH_INTERACTIONS_DISABLED;
        return update(
            DeathSafetyEventDisposition.ACCEPTED,
            DeathSafetyDirective.START_INTERACTION_DISABLED_RECOVERY_NAVIGATION,
            DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH);
    }

    public synchronized DeathSafetyUpdate onRecoveryNavigation(SafetyEventStamp stamp, long observedDeathEpoch,
        RecoveryNavigationObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation must not be null");
        }
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        DeathSafetyUpdate epochOrPhase = rejectDeathEpochOrPhase(
            observedDeathEpoch,
            RecoveryPhase.NAVIGATING_WITH_INTERACTIONS_DISABLED);
        if (epochOrPhase != null) {
            return epochOrPhase;
        }
        if (observation.isGenericInteractionsEnabled() || observation.isGraveTargetedByRouteAction()) {
            return enterManualHold(ManualHoldReason.UNSAFE_RECOVERY_NAVIGATION);
        }
        if (!baselineRespawnInventory.hasSameContents(observation.getInventory())) {
            return enterManualHold(ManualHoldReason.INVENTORY_CHANGED_DURING_RECOVERY);
        }
        if (observation.getStatus() == RecoveryNavigationStatus.FAILED) {
            return enterManualHold(ManualHoldReason.RECOVERY_NAVIGATION_FAILED);
        }
        if (observation.getStatus() == RecoveryNavigationStatus.ARRIVED) {
            if (!observation.getPlayerPosition()
                .isWithinRadius(deathPosition, policy.getGravePlacementRadius())) {
                return enterManualHold(ManualHoldReason.GRAVE_OUTSIDE_DEATH_DIMENSION);
            }
            recoveryPhase = RecoveryPhase.SEARCHING_FOR_GRAVE;
            return update(DeathSafetyEventDisposition.ACCEPTED, DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH);
        }
        return update(DeathSafetyEventDisposition.ACCEPTED);
    }

    public synchronized DeathSafetyUpdate onGraveSearch(SafetyEventStamp stamp, long observedDeathEpoch,
        GraveSearchObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation must not be null");
        }
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        if (observedDeathEpoch != deathEpoch) {
            return update(DeathSafetyEventDisposition.STALE_DEATH_EPOCH);
        }
        if (recoveryPhase != RecoveryPhase.SEARCHING_FOR_GRAVE && recoveryPhase != RecoveryPhase.STABILIZING_GRAVE) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        if (!baselineRespawnInventory.hasSameContents(observation.getCurrentInventory())) {
            return enterManualHold(ManualHoldReason.INVENTORY_CHANGED_DURING_RECOVERY);
        }
        if (observation.getStatus() == GraveSearchStatus.REGION_UNLOADED) {
            return enterManualHold(ManualHoldReason.GRAVE_REGION_UNLOADED);
        }
        if (observation.getStatus() == GraveSearchStatus.EVIDENCE_UNAVAILABLE) {
            return enterManualHold(ManualHoldReason.GRAVE_EVIDENCE_UNAVAILABLE);
        }
        if (observation.getStatus() == GraveSearchStatus.IN_PROGRESS) {
            if (recoveryPhase == RecoveryPhase.STABILIZING_GRAVE) {
                return enterManualHold(ManualHoldReason.GRAVE_CHANGED);
            }
            return update(DeathSafetyEventDisposition.ACCEPTED);
        }

        GraveSelection selection = selectGrave(observation.getCandidates());
        if (selection.failureReason != null) {
            return enterManualHold(selection.failureReason);
        }
        GraveCandidate candidate = selection.candidate;
        if (stableGrave != null && !stableGrave.hasSameStableEvidence(candidate)) {
            return enterManualHold(ManualHoldReason.GRAVE_CHANGED);
        }
        if (!baselineRespawnInventory.canAcceptAll(candidate.getContents())) {
            return enterManualHold(ManualHoldReason.INSUFFICIENT_INVENTORY_CAPACITY);
        }
        if (!observation.isEmptyHotbarHandAvailable()) {
            return enterManualHold(ManualHoldReason.NO_EMPTY_HOTBAR_HAND);
        }
        if (!baselineRespawnInventory.fingerprintWith(candidate.getContents())
            .equals(preDeathInventoryFingerprint)) {
            return enterManualHold(ManualHoldReason.PRE_DEATH_CONTENT_MISMATCH);
        }

        if (stableGrave == null) {
            stableGrave = candidate;
            graveStableTicks = 1;
            lastGraveTick = stamp.getClientTick();
            recoveryPhase = RecoveryPhase.STABILIZING_GRAVE;
        } else {
            graveStableTicks = consecutiveCount(graveStableTicks, lastGraveTick, stamp.getClientTick());
            lastGraveTick = stamp.getClientTick();
        }

        if (graveStableTicks < policy.getGraveStableTicks()) {
            return update(DeathSafetyEventDisposition.ACCEPTED);
        }
        graveActivationPermit = new GraveActivationPermit(
            deathEpoch,
            connectionEpoch,
            deathEpoch,
            stableGrave.getIdentity());
        graveActivationConsumed = false;
        recoveryPhase = RecoveryPhase.AWAITING_SCOPED_ACTIVATION;
        return update(DeathSafetyEventDisposition.ACCEPTED, DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH);
    }

    /** Called at the exact grave-use packet write boundary. */
    public synchronized GraveActivationResult authorizeGraveActivation(SafetyEventStamp stamp,
        GraveActivationAttempt attempt) {
        if (attempt == null) {
            throw new IllegalArgumentException("attempt must not be null");
        }
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            GraveActivationDecision decision = rejected == DeathSafetyEventDisposition.STALE_CONNECTION_EPOCH
                || rejected == DeathSafetyEventDisposition.DISCONNECTED
                    ? GraveActivationDecision.REJECTED_STALE_CONNECTION
                    : GraveActivationDecision.REJECTED_STALE_EVENT;
            return activationResult(decision, update(rejected));
        }
        if (attempt.getDeathEpoch() != deathEpoch) {
            return activationResult(
                GraveActivationDecision.REJECTED_STALE_DEATH_EPOCH,
                update(DeathSafetyEventDisposition.STALE_DEATH_EPOCH));
        }
        if (graveActivationConsumed) {
            return activationResult(
                GraveActivationDecision.REJECTED_ALREADY_CONSUMED,
                update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE));
        }
        if (recoveryPhase != RecoveryPhase.AWAITING_SCOPED_ACTIVATION || graveActivationPermit == null) {
            return activationResult(
                GraveActivationDecision.REJECTED_NO_PERMIT,
                update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE));
        }
        if (attempt.getPermitId() != graveActivationPermit.getPermitId()) {
            return activationResult(
                GraveActivationDecision.REJECTED_WRONG_PERMIT,
                update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE));
        }
        if (!graveActivationPermit.getGraveIdentity()
            .equals(attempt.getTarget())) {
            return activationResult(
                GraveActivationDecision.REJECTED_WRONG_GRAVE,
                update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE));
        }
        if (!attempt.isEmptyHand() || !attempt.isSneaking()) {
            return activationResult(
                GraveActivationDecision.REJECTED_UNSAFE_POSTURE,
                update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE));
        }

        graveActivationConsumed = true;
        graveActivationPermit = null;
        recoveryPhase = RecoveryPhase.VERIFYING_RECOVERY;
        return activationResult(
            GraveActivationDecision.AUTHORIZED_AND_CONSUMED,
            update(
                DeathSafetyEventDisposition.ACCEPTED,
                DeathSafetyDirective.AUTHORIZE_EXACT_GRAVE_ACTIVATION,
                DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH));
    }

    public synchronized DeathSafetyUpdate onRecoveryVerification(SafetyEventStamp stamp, long observedDeathEpoch,
        RecoveryVerificationObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation must not be null");
        }
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        DeathSafetyUpdate epochOrPhase = rejectDeathEpochOrPhase(observedDeathEpoch, RecoveryPhase.VERIFYING_RECOVERY);
        if (epochOrPhase != null) {
            return epochOrPhase;
        }
        if (observation.getGraveResolution() == GraveResolution.REGION_UNLOADED) {
            return enterManualHold(ManualHoldReason.GRAVE_REGION_UNLOADED);
        }
        if (observation.getGraveResolution() == GraveResolution.PRESENT) {
            GraveCandidate current = observation.getGraveCandidate();
            if (!stableGrave.getIdentity()
                .equals(current.getIdentity()) || !oldPlayerIdentity.equals(current.getOwnerIdentity())) {
                return enterManualHold(ManualHoldReason.GRAVE_CHANGED);
            }
            if (!stableGrave.getContents()
                .hasSameContents(current.getContents())) {
                return enterManualHold(ManualHoldReason.PARTIAL_RECOVERY);
            }
            return update(DeathSafetyEventDisposition.ACCEPTED);
        }

        if (!preDeathInventoryFingerprint.equals(
            observation.getCurrentInventory()
                .getContentFingerprint())) {
            ManualHoldReason reason = preDeathInventory != null && observation.getCurrentInventory()
                .isStrictContentSubsetOf(preDeathInventory) ? ManualHoldReason.PARTIAL_RECOVERY
                    : ManualHoldReason.RECOVERED_INVENTORY_MISMATCH;
            return enterManualHold(reason);
        }
        long resolvedEpoch = deathEpoch;
        interlock.releaseDeathLockdown(resolvedEpoch);
        clearResolvedDeath();
        return update(
            DeathSafetyEventDisposition.ACCEPTED,
            DeathSafetyDirective.RELEASE_DEATH_LOCKDOWN,
            DeathSafetyDirective.CLEAR_UNRESOLVED_DEATH);
    }

    public synchronized DeathSafetyUpdate onDisconnect(SafetyEventStamp stamp) {
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        connected = false;
        if (state == DeathSafetyState.CRITICAL) {
            interlock.releaseCriticalRestrictions();
            state = DeathSafetyState.ACTIVE;
            resetHealthyStability();
        } else if (hasUnresolvedDeath() && state != DeathSafetyState.MANUAL_HOLD) {
            recoveryPhase = RecoveryPhase.REVALIDATING_RESPAWN;
            resetRecoveryTransientEvidence();
        }
        return hasUnresolvedDeath()
            ? update(DeathSafetyEventDisposition.ACCEPTED, DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH)
            : update(DeathSafetyEventDisposition.ACCEPTED);
    }

    /** Reconnects after a transport close; old-connection events remain permanently stale. */
    public synchronized DeathSafetyUpdate reconnect(ConnectionIdentity connection) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        if (connected) {
            throw new IllegalStateException("disconnect must be observed before reconnect");
        }
        if (connection.getConnectionEpoch() <= connectionEpoch) {
            throw new IllegalArgumentException("a reconnect must advance the connection epoch");
        }
        applyConnection(connection);
        connected = true;
        lastEventSequence = 0L;
        lastClientTick = -1L;
        if (!hasUnresolvedDeath()) {
            return update(DeathSafetyEventDisposition.ACCEPTED);
        }
        resetRecoveryTransientEvidence();
        if (state != DeathSafetyState.MANUAL_HOLD) {
            recoveryPhase = RecoveryPhase.REVALIDATING_RESPAWN;
            if (!profileMatchesCurrentDeath()) {
                state = DeathSafetyState.MANUAL_HOLD;
                recoveryPhase = RecoveryPhase.MANUAL_HOLD;
                manualHoldReason = ManualHoldReason.PROFILE_MISMATCH;
            }
        }
        interlock.reaffirmDeathLockdown(deathEpoch);
        return update(
            DeathSafetyEventDisposition.ACCEPTED,
            state == DeathSafetyState.MANUAL_HOLD
                ? EnumSet.of(
                    DeathSafetyDirective.ENGAGE_DEATH_LOCKDOWN,
                    DeathSafetyDirective.ENTER_MANUAL_HOLD,
                    DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH)
                : EnumSet
                    .of(DeathSafetyDirective.ENGAGE_DEATH_LOCKDOWN, DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH));
    }

    /** The only way out of MANUAL_HOLD other than constructing a new profile after human repair. */
    public synchronized DeathSafetyUpdate resolveManualHoldByOperator(SafetyEventStamp stamp, long resolvedDeathEpoch,
        boolean operatorConfirmedItemsSafe) {
        DeathSafetyEventDisposition rejected = rejectStamp(stamp);
        if (rejected != null) {
            return update(rejected);
        }
        if (resolvedDeathEpoch != deathEpoch) {
            return update(DeathSafetyEventDisposition.STALE_DEATH_EPOCH);
        }
        if (state != DeathSafetyState.MANUAL_HOLD || !operatorConfirmedItemsSafe) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        interlock.releaseDeathLockdown(deathEpoch);
        clearResolvedDeath();
        return update(
            DeathSafetyEventDisposition.ACCEPTED,
            DeathSafetyDirective.RELEASE_DEATH_LOCKDOWN,
            DeathSafetyDirective.CLEAR_UNRESOLVED_DEATH);
    }

    public synchronized DeathSafetySnapshot snapshot() {
        return createSnapshot();
    }

    public synchronized Optional<UnresolvedDeathProjection> unresolvedDeathProjection() {
        return Optional.ofNullable(createProjection());
    }

    private DeathSafetyUpdate latchOrRedundant(SafetyEventStamp stamp, DeathSignal signal, DeathContext context) {
        if (hasUnresolvedDeath()) {
            if (oldPlayerIdentity.equals(context.getOldPlayerIdentity())) {
                return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
            }
            if (currentPlayerIdentity.equals(context.getOldPlayerIdentity())) {
                return enterManualHold(ManualHoldReason.REPEATED_DEATH_DURING_RECOVERY);
            }
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        if ((lastResolvedOldPlayerIdentity != null
            && lastResolvedOldPlayerIdentity.equals(context.getOldPlayerIdentity()))
            || !currentPlayerIdentity.equals(context.getOldPlayerIdentity())) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        return latchFirstDeath(stamp, signal, context, null);
    }

    private DeathSafetyUpdate latchFirstDeath(SafetyEventStamp stamp, DeathSignal signal, DeathContext context,
        ManualHoldReason initialManualHoldReason) {
        if (nextDeathEpoch == Long.MAX_VALUE) {
            throw new IllegalStateException("death epoch exhausted");
        }
        deathEpoch = nextDeathEpoch++;
        deathRecordedAtTick = stamp.getClientTick();
        deathServerIdentity = currentServerIdentity;
        deathWorldIdentity = currentWorldIdentity;
        deathPosition = context.getDeathPosition();
        oldPlayerIdentity = context.getOldPlayerIdentity();
        activeTaskId = context.getActiveTaskId()
            .orElse(null);
        preDeathInventory = context.getPreDeathInventory();
        preDeathInventoryFingerprint = preDeathInventory.getContentFingerprint();
        respawnRequestConsumed = false;
        manualHoldReason = initialManualHoldReason;
        state = initialManualHoldReason == null ? DeathSafetyState.DEATH_LATCHED : DeathSafetyState.MANUAL_HOLD;
        recoveryPhase = initialManualHoldReason == null ? RecoveryPhase.AWAITING_RESPAWN : RecoveryPhase.MANUAL_HOLD;
        resetHealthyStability();
        resetRecoveryTransientEvidence();

        DeathLatchRecord record = new DeathLatchRecord(
            deathEpoch,
            connectionEpoch,
            stamp.getClientTick(),
            signal,
            deathServerIdentity,
            deathWorldIdentity,
            context);
        interlock.latchDeath(record);
        Set<DeathSafetyDirective> firstLatch = EnumSet.copyOf(FIRST_LATCH_DIRECTIVES);
        if (initialManualHoldReason != null) {
            firstLatch.add(DeathSafetyDirective.ENTER_MANUAL_HOLD);
        }
        return update(DeathSafetyEventDisposition.ACCEPTED, firstLatch);
    }

    private DeathSafetyUpdate rejectDeathEpochOrPhase(long observedDeathEpoch, RecoveryPhase requiredPhase) {
        if (observedDeathEpoch != deathEpoch) {
            return update(DeathSafetyEventDisposition.STALE_DEATH_EPOCH);
        }
        if (state == DeathSafetyState.MANUAL_HOLD || recoveryPhase != requiredPhase) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        return null;
    }

    private GraveSelection selectGrave(List<GraveCandidate> candidates) {
        List<GraveCandidate> owned = new ArrayList<>();
        boolean sawWrongOwner = false;
        boolean sawOwnedEmpty = false;
        boolean sawOwnedInWrongDimension = false;
        for (GraveCandidate candidate : candidates) {
            boolean ownedByDeadPlayer = oldPlayerIdentity.equals(candidate.getOwnerIdentity());
            if (candidate.getIdentity()
                .getPosition()
                .getDimensionId() != deathPosition.getDimensionId()) {
                sawOwnedInWrongDimension |= ownedByDeadPlayer && !candidate.getContents()
                    .isEmpty();
                continue;
            }
            if (!candidate.getIdentity()
                .getPosition()
                .isWithinRadius(deathPosition, policy.getGravePlacementRadius())) {
                continue;
            }
            if (!ownedByDeadPlayer) {
                sawWrongOwner = true;
            } else if (candidate.getContents()
                .isEmpty()) {
                    sawOwnedEmpty = true;
                } else {
                    owned.add(candidate);
                }
        }
        if (owned.size() > 1) {
            return GraveSelection.failure(ManualHoldReason.MULTIPLE_OWNED_GRAVES);
        }
        if (owned.size() == 1) {
            return GraveSelection.success(owned.get(0));
        }
        if (sawOwnedInWrongDimension) {
            return GraveSelection.failure(ManualHoldReason.GRAVE_OUTSIDE_DEATH_DIMENSION);
        }
        if (sawOwnedEmpty) {
            return GraveSelection.failure(ManualHoldReason.GRAVE_EMPTY);
        }
        if (sawWrongOwner) {
            return GraveSelection.failure(ManualHoldReason.GRAVE_WRONG_OWNER);
        }
        return GraveSelection.failure(ManualHoldReason.GRAVE_MISSING);
    }

    private DeathSafetyUpdate enterManualHold(ManualHoldReason reason) {
        if (state == DeathSafetyState.MANUAL_HOLD) {
            return update(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE);
        }
        state = DeathSafetyState.MANUAL_HOLD;
        recoveryPhase = RecoveryPhase.MANUAL_HOLD;
        manualHoldReason = reason;
        graveActivationPermit = null;
        interlock.reaffirmDeathLockdown(deathEpoch);
        return update(
            DeathSafetyEventDisposition.ACCEPTED,
            DeathSafetyDirective.ENTER_MANUAL_HOLD,
            DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH);
    }

    private DeathSafetyEventDisposition rejectStamp(SafetyEventStamp stamp) {
        if (stamp == null) {
            throw new IllegalArgumentException("stamp must not be null");
        }
        if (!connected) {
            return DeathSafetyEventDisposition.DISCONNECTED;
        }
        if (stamp.getConnectionEpoch() != connectionEpoch) {
            return DeathSafetyEventDisposition.STALE_CONNECTION_EPOCH;
        }
        if (stamp.getEventSequence() <= lastEventSequence) {
            return DeathSafetyEventDisposition.STALE_EVENT_SEQUENCE;
        }
        if (stamp.getClientTick() < lastClientTick) {
            return DeathSafetyEventDisposition.STALE_CLIENT_TICK;
        }
        lastEventSequence = stamp.getEventSequence();
        lastClientTick = stamp.getClientTick();
        return null;
    }

    private boolean profileMatchesCurrentDeath() {
        return deathServerIdentity.equals(currentServerIdentity) && deathWorldIdentity.equals(currentWorldIdentity);
    }

    private boolean hasUnresolvedDeath() {
        return deathEpoch > 0L;
    }

    private void applyConnection(ConnectionIdentity connection) {
        connectionEpoch = connection.getConnectionEpoch();
        currentServerIdentity = connection.getServerIdentity();
        currentWorldIdentity = connection.getWorldIdentity();
        currentPlayerIdentity = connection.getPlayerIdentity();
    }

    private void resetHealthyStability() {
        healthyStableTicks = 0;
        lastHealthyTick = -1L;
    }

    private void resetRespawnStability() {
        respawnStableTicks = 0;
        lastRespawnTick = -1L;
        respawnPlayerIdentity = null;
        respawnDimension = 0;
        respawnInventoryAnchor = null;
    }

    private void resetRecoveryTransientEvidence() {
        resetRespawnStability();
        baselineRespawnInventory = null;
        graveStableTicks = 0;
        lastGraveTick = -1L;
        stableGrave = null;
        graveActivationPermit = null;
        graveActivationConsumed = false;
    }

    private void clearResolvedDeath() {
        lastResolvedOldPlayerIdentity = oldPlayerIdentity;
        state = DeathSafetyState.ACTIVE;
        recoveryPhase = RecoveryPhase.NONE;
        deathEpoch = 0L;
        deathRecordedAtTick = 0L;
        deathServerIdentity = null;
        deathWorldIdentity = null;
        deathPosition = null;
        oldPlayerIdentity = null;
        activeTaskId = null;
        preDeathInventoryFingerprint = null;
        preDeathInventory = null;
        respawnRequestConsumed = false;
        manualHoldReason = null;
        resetHealthyStability();
        resetRecoveryTransientEvidence();
    }

    private static int consecutiveCount(int currentCount, long previousTick, long currentTick) {
        if (currentTick == previousTick) {
            return currentCount;
        }
        if (previousTick < 0L || currentTick != previousTick + 1L) {
            return 1;
        }
        return currentCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : currentCount + 1;
    }

    private UnresolvedDeathProjection createProjection() {
        if (!hasUnresolvedDeath()) {
            return null;
        }
        return new UnresolvedDeathProjection(
            deathEpoch,
            deathRecordedAtTick,
            deathServerIdentity,
            deathWorldIdentity,
            deathPosition,
            oldPlayerIdentity,
            activeTaskId,
            preDeathInventoryFingerprint,
            preDeathInventory,
            stableGrave,
            graveActivationConsumed,
            state,
            recoveryPhase,
            respawnRequestConsumed,
            manualHoldReason);
    }

    private DeathSafetySnapshot createSnapshot() {
        RecoveryNavigationRequest navigationRequest = recoveryPhase
            == RecoveryPhase.NAVIGATING_WITH_INTERACTIONS_DISABLED
                ? new RecoveryNavigationRequest(
                    deathEpoch,
                    deathPosition,
                    Math.min(policy.getGravePlacementRadius(), 8))
                : null;
        return new DeathSafetySnapshot(
            state,
            recoveryPhase,
            connected,
            connectionEpoch,
            deathEpoch,
            healthyStableTicks,
            respawnStableTicks,
            graveStableTicks,
            respawnRequestConsumed,
            manualHoldReason,
            graveActivationPermit,
            navigationRequest,
            createProjection(),
            preDeathInventory,
            stableGrave);
    }

    private DeathSafetyUpdate update(DeathSafetyEventDisposition disposition, DeathSafetyDirective... directives) {
        Set<DeathSafetyDirective> values = EnumSet.noneOf(DeathSafetyDirective.class);
        for (DeathSafetyDirective directive : directives) {
            values.add(directive);
        }
        return update(disposition, values);
    }

    private DeathSafetyUpdate update(DeathSafetyEventDisposition disposition, Set<DeathSafetyDirective> directives) {
        return new DeathSafetyUpdate(disposition, directives, createSnapshot());
    }

    private GraveActivationResult activationResult(GraveActivationDecision decision, DeathSafetyUpdate update) {
        return new GraveActivationResult(decision, update);
    }

    private static final class GraveSelection {

        private final GraveCandidate candidate;
        private final ManualHoldReason failureReason;

        private GraveSelection(GraveCandidate candidate, ManualHoldReason failureReason) {
            this.candidate = candidate;
            this.failureReason = failureReason;
        }

        private static GraveSelection success(GraveCandidate candidate) {
            return new GraveSelection(candidate, null);
        }

        private static GraveSelection failure(ManualHoldReason reason) {
            return new GraveSelection(null, reason);
        }
    }
}
