package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.Set;

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
        if (mode != Mode.PLAYER) {
            throw new IllegalStateException("the previous action session has not drained");
        }
        activeLease = lease;
        sessionEpoch = lease.getEpoch();
        mode = Mode.ACTIVE;
        cleanupComplete = false;
        blockedActionCount = 0L;
        lastBlockedAction = "";
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
    }

    /** For disconnect teardown only; an open unguarded channel must never call this. */
    public synchronized void clear() {
        if (!transportClosed || mode == Mode.SAFETY_LOCKDOWN) {
            throw new IllegalStateException("an open or safety-locked action session cannot be cleared");
        }
        resetToPlayer();
    }

    public synchronized void markFirewallInstalled() {
        firewallReady = true;
        transportClosed = false;
    }

    public synchronized void markFirewallUnavailable() {
        firewallReady = false;
        transportClosed = false;
        if (mode == Mode.ACTIVE) {
            mode = Mode.QUARANTINED;
            cleanupComplete = false;
            advanceTransitionGeneration();
        }
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
    }

    public synchronized boolean isReadyForSession() {
        return firewallReady && mode == Mode.PLAYER;
    }

    public synchronized boolean isActiveLease(ActionLease lease) {
        return mode == Mode.ACTIVE && activeLease == lease && lease != null && lease.isValid();
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
            return false;
        }
        resetToPlayer();
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
        if (sessionDecision != null) {
            return sessionDecision;
        }
        return activeLease.getCapabilities()
            .contains(capability) ? ActionAuthorizationDecision.AUTHORIZED
                : ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY;
    }

    public synchronized ActionAuthorizationDecision authorizeAny(Set<ActionCapability> capabilities) {
        requireCapabilities(capabilities);
        ActionAuthorizationDecision sessionDecision = sessionDecision();
        if (sessionDecision != null) {
            return sessionDecision;
        }
        for (ActionCapability capability : capabilities) {
            if (activeLease.getCapabilities()
                .contains(capability)) {
                return ActionAuthorizationDecision.AUTHORIZED;
            }
        }
        return ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY;
    }

    public synchronized ActionAuthorizationDecision authorizeAll(Set<ActionCapability> capabilities) {
        requireCapabilities(capabilities);
        ActionAuthorizationDecision sessionDecision = sessionDecision();
        if (sessionDecision != null) {
            return sessionDecision;
        }
        return activeLease.getCapabilities()
            .containsAll(capabilities) ? ActionAuthorizationDecision.AUTHORIZED
                : ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY;
    }

    /** Unknown action-bearing packets are allowed only during direct player control. */
    public synchronized ActionAuthorizationDecision authorizeUnknownAction() {
        if (mode == Mode.PLAYER) {
            return ActionAuthorizationDecision.PLAYER_PASSTHROUGH;
        }
        if (mode == Mode.ACTIVE && activeLease != null && activeLease.isValid()) {
            return ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY;
        }
        return ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH;
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
        if (mode != Mode.ACTIVE || activeLease == null || !activeLease.isValid()) {
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

    private static void requireCapabilities(Set<ActionCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty() || capabilities.contains(null)) {
            throw new IllegalArgumentException("capabilities must not be null or empty");
        }
    }
}
