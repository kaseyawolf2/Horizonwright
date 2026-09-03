package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.Set;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;

/**
 * Linearizes an automation lease with the final outbound packet boundary.
 *
 * <p>
 * A completed or revoked session enters quarantine instead of immediately
 * returning to player pass-through. The Netty event loop releases quarantine
 * only after the client-side producer has stopped and a FIFO barrier has run.
 */
public final class ActionSessionGuard implements ActionRevocationListener {

    public enum Mode {
        PLAYER,
        ACTIVE,
        QUARANTINED,
        SAFETY_LOCKDOWN
    }

    private Mode mode = Mode.PLAYER;
    private ActionLease activeLease;
    private long sessionEpoch;
    private long latestRevocationEpoch = 1L;
    private long transitionGeneration = 1L;
    private boolean cleanupComplete;
    private boolean firewallReady;
    private boolean transportClosed = true;
    private long blockedActionCount;
    private String lastBlockedAction = "";

    public synchronized void begin(ActionLease lease) {
        if (lease == null || !lease.isValid()) {
            throw new IllegalArgumentException("an active action lease is required");
        }
        if (!firewallReady) {
            throw new IllegalStateException("outbound action firewall is not ready");
        }
        if (lease.isSafetyRecoveryLease()) {
            if (mode != Mode.SAFETY_LOCKDOWN || activeLease != null) {
                throw new IllegalStateException("death recovery movement authority is not ready");
            }
        } else if (mode != Mode.PLAYER) {
            throw new IllegalStateException("the previous action session has not drained");
        }
        activeLease = lease;
        sessionEpoch = lease.getEpoch();
        if (!lease.isSafetyRecoveryLease()) {
            mode = Mode.ACTIVE;
        }
        cleanupComplete = false;
        blockedActionCount = 0L;
        lastBlockedAction = "";
        trace(
            "session-begin",
            "owner",
            lease.getOwner(),
            "capabilities",
            lease.getCapabilities(),
            "recovery",
            lease.isSafetyRecoveryLease());
    }

    /** Immediately blocks action packets while the producer is being stopped. */
    public synchronized void quarantine(ActionLease lease) {
        if (activeLease != lease) {
            return;
        }
        if (mode == Mode.ACTIVE) {
            mode = Mode.QUARANTINED;
            advanceTransitionGeneration();
        }
        cleanupComplete = false;
        trace("session-quarantine", "leaseMatched", activeLease == lease);
    }

    /** Marks producer cleanup complete; Netty must still run the drain barrier. */
    public synchronized void end(ActionLease lease) {
        if (activeLease != lease) {
            return;
        }
        if (mode == Mode.ACTIVE) {
            mode = Mode.QUARANTINED;
            advanceTransitionGeneration();
        }
        activeLease = null;
        cleanupComplete = true;
        releaseWithoutTransportIfSafe();
        trace("session-end", "leaseOwner", lease.getOwner());
    }

    /** For disconnect teardown only; an open unguarded channel must never call this. */
    public synchronized void clear() {
        if (!transportClosed || mode == Mode.SAFETY_LOCKDOWN) {
            throw new IllegalStateException("an open or safety-locked action session cannot be cleared");
        }
        resetToPlayer();
        trace("cleared");
    }

    public synchronized void markFirewallInstalled() {
        firewallReady = true;
        transportClosed = false;
        trace("firewall-installed");
    }

    public synchronized void markFirewallUnavailable() {
        firewallReady = false;
        transportClosed = false;
        if (mode == Mode.ACTIVE) {
            mode = Mode.QUARANTINED;
            cleanupComplete = false;
            advanceTransitionGeneration();
        }
        trace("firewall-unavailable");
    }

    public synchronized void markTransportClosed() {
        firewallReady = false;
        transportClosed = true;
        if (mode == Mode.ACTIVE) {
            mode = Mode.QUARANTINED;
            cleanupComplete = false;
            advanceTransitionGeneration();
        }
        releaseWithoutTransportIfSafe();
        trace("transport-closed");
    }

    public synchronized boolean isReadyForSession() {
        return firewallReady && mode == Mode.PLAYER;
    }

    public synchronized boolean isReadyForSafetyRecoverySession() {
        return firewallReady && mode == Mode.SAFETY_LOCKDOWN && activeLease == null;
    }

    public synchronized boolean isActiveLease(ActionLease lease) {
        if (lease == null || activeLease != lease || !lease.isValid()) {
            return false;
        }
        return lease.isSafetyRecoveryLease() ? mode == Mode.SAFETY_LOCKDOWN : mode == Mode.ACTIVE;
    }

    public synchronized String readinessDiagnostic() {
        if (!firewallReady) {
            return "outbound action firewall is not installed on the current connection";
        }
        if (mode == Mode.SAFETY_LOCKDOWN) {
            return "action safety lockdown is latched";
        }
        if (mode == Mode.QUARANTINED) {
            return cleanupComplete ? "previous action packets are draining" : "previous action producer is stopping";
        }
        if (mode == Mode.ACTIVE) {
            return "an action session is already active";
        }
        return "ready";
    }

    /** Returns a token for a FIFO event-loop barrier, or zero when none is ready. */
    public synchronized long drainGenerationOrZero() {
        return firewallReady && mode == Mode.QUARANTINED && cleanupComplete ? transitionGeneration : 0L;
    }

    /** Called only by the current channel's event loop after its FIFO barrier. */
    public synchronized boolean completeDrain(long generation) {
        if (generation == 0L || generation != transitionGeneration
            || !firewallReady
            || mode != Mode.QUARANTINED
            || !cleanupComplete) {
            trace("drain-rejected", "requestedGeneration", generation);
            return false;
        }
        resetToPlayer();
        trace("drain-complete", "requestedGeneration", generation);
        return true;
    }

    public synchronized boolean isGuarding() {
        return mode != Mode.PLAYER;
    }

    public synchronized Mode getMode() {
        return mode;
    }

    public synchronized long activeEpochOrZero() {
        return mode == Mode.PLAYER ? 0L : sessionEpoch;
    }

    public synchronized void recordBlockedAction(String description) {
        if (description == null || description.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("blocked action description must not be blank");
        }
        blockedActionCount++;
        lastBlockedAction = description.trim();
        trace("action-blocked", "description", lastBlockedAction, "blockedCount", blockedActionCount);
    }

    public synchronized long getBlockedActionCount() {
        return blockedActionCount;
    }

    public synchronized String getLastBlockedAction() {
        return lastBlockedAction;
    }

    public synchronized ActionAuthorizationDecision authorize(ActionCapability capability) {
        if (capability == null) {
            throw new IllegalArgumentException("capability must not be null");
        }
        ActionAuthorizationDecision sessionDecision = sessionDecision();
        ActionAuthorizationDecision decision = sessionDecision != null ? sessionDecision
            : (activeLease.getCapabilities()
                .contains(capability) ? ActionAuthorizationDecision.AUTHORIZED
                    : ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY);
        trace("authorize", "capability", capability, "decision", decision);
        return decision;
    }

    public synchronized ActionAuthorizationDecision authorizeAny(Set<ActionCapability> capabilities) {
        requireCapabilities(capabilities);
        ActionAuthorizationDecision sessionDecision = sessionDecision();
        if (sessionDecision != null) {
            trace("authorize-any", "capabilities", capabilities, "decision", sessionDecision);
            return sessionDecision;
        }
        for (ActionCapability capability : capabilities) {
            if (activeLease.getCapabilities()
                .contains(capability)) {
                trace(
                    "authorize-any",
                    "capabilities",
                    capabilities,
                    "decision",
                    ActionAuthorizationDecision.AUTHORIZED);
                return ActionAuthorizationDecision.AUTHORIZED;
            }
        }
        trace(
            "authorize-any",
            "capabilities",
            capabilities,
            "decision",
            ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY);
        return ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY;
    }

    public synchronized ActionAuthorizationDecision authorizeAll(Set<ActionCapability> capabilities) {
        requireCapabilities(capabilities);
        ActionAuthorizationDecision sessionDecision = sessionDecision();
        ActionAuthorizationDecision decision = sessionDecision != null ? sessionDecision
            : (activeLease.getCapabilities()
                .containsAll(capabilities) ? ActionAuthorizationDecision.AUTHORIZED
                    : ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY);
        trace("authorize-all", "capabilities", capabilities, "decision", decision);
        return decision;
    }

    /**
     * Unknown or unintegrated traffic is outside Horizonwright's authority and
     * must always pass through without affecting action-session state.
     */
    public synchronized ActionAuthorizationDecision authorizeUnknownAction() {
        trace("authorize-unknown", "decision", ActionAuthorizationDecision.PLAYER_PASSTHROUGH);
        return ActionAuthorizationDecision.PLAYER_PASSTHROUGH;
    }

    @Override
    public synchronized void onActionEpochRevoked(ActionRevocation revocation) {
        if (revocation == null) {
            throw new IllegalArgumentException("revocation must not be null");
        }
        if (revocation.getNewEpoch() < latestRevocationEpoch) {
            return;
        }
        latestRevocationEpoch = revocation.getNewEpoch();
        trace(
            "epoch-revoked",
            "revokedEpoch",
            revocation.getRevokedEpoch(),
            "newEpoch",
            revocation.getNewEpoch(),
            "reason",
            revocation.getReason());
        if (revocation.getReason() == ActionRevocationReason.SAFETY_LOCKDOWN) {
            if (activeLease != null) {
                sessionEpoch = activeLease.getEpoch();
                cleanupComplete = false;
            }
            mode = Mode.SAFETY_LOCKDOWN;
            advanceTransitionGeneration();
            return;
        }
        if (revocation.getReason() == ActionRevocationReason.SAFETY_LOCKDOWN_RELEASED) {
            if (mode == Mode.SAFETY_LOCKDOWN) {
                mode = Mode.QUARANTINED;
                cleanupComplete = activeLease == null;
                advanceTransitionGeneration();
                releaseWithoutTransportIfSafe();
            }
            return;
        }
        if (mode == Mode.SAFETY_LOCKDOWN) {
            return;
        }
        if (activeLease != null && activeLease.getEpoch() == revocation.getRevokedEpoch()) {
            sessionEpoch = revocation.getRevokedEpoch();
            mode = Mode.QUARANTINED;
            cleanupComplete = false;
            advanceTransitionGeneration();
        }
    }

    private ActionAuthorizationDecision sessionDecision() {
        if (mode == Mode.PLAYER) {
            return ActionAuthorizationDecision.PLAYER_PASSTHROUGH;
        }
        boolean activeNormalSession = mode == Mode.ACTIVE && activeLease != null
            && !activeLease.isSafetyRecoveryLease();
        boolean activeRecoverySession = mode == Mode.SAFETY_LOCKDOWN && activeLease != null
            && activeLease.isSafetyRecoveryLease();
        if ((!activeNormalSession && !activeRecoverySession) || !activeLease.isValid()) {
            return ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH;
        }
        return null;
    }

    private void releaseWithoutTransportIfSafe() {
        if (transportClosed && mode == Mode.QUARANTINED && cleanupComplete) {
            resetToPlayer();
        }
    }

    private void resetToPlayer() {
        mode = Mode.PLAYER;
        activeLease = null;
        sessionEpoch = 0L;
        cleanupComplete = false;
    }

    private void advanceTransitionGeneration() {
        if (transitionGeneration == Long.MAX_VALUE) {
            throw new IllegalStateException("action session generation exhausted");
        }
        transitionGeneration++;
    }

    private void trace(String event, Object... extraFields) {
        Object[] fields = new Object[14 + extraFields.length];
        fields[0] = "mode";
        fields[1] = mode;
        fields[2] = "sessionEpoch";
        fields[3] = sessionEpoch;
        fields[4] = "generation";
        fields[5] = transitionGeneration;
        fields[6] = "firewallReady";
        fields[7] = firewallReady;
        fields[8] = "transportClosed";
        fields[9] = transportClosed;
        fields[10] = "cleanupComplete";
        fields[11] = cleanupComplete;
        fields[12] = "activeOwner";
        fields[13] = activeLease == null ? "none" : activeLease.getOwner();
        System.arraycopy(extraFields, 0, fields, 14, extraFields.length);
        DevelopmentTrace.event("action-session", event, fields);
    }

    private static void requireCapabilities(Set<ActionCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty() || capabilities.contains(null)) {
            throw new IllegalArgumentException("capabilities must not be null or empty");
        }
    }
}
