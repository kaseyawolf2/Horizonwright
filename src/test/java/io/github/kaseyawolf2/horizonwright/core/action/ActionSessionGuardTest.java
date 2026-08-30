package io.github.kaseyawolf2.horizonwright.core.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.EnumSet;

import org.junit.Test;

public class ActionSessionGuardTest {

    @Test
    public void playerControlPassesThroughOutsideAnAutomationSession() {
        ActionSessionGuard guard = new ActionSessionGuard();

        assertEquals(ActionAuthorizationDecision.PLAYER_PASSTHROUGH, guard.authorize(ActionCapability.DIG));
        assertFalse(guard.isGuarding());
    }

    @Test
    public void activeSessionAllowsOnlyCapabilitiesOnItsLease() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker
            .tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        guard.begin(lease);

        assertEquals(ActionAuthorizationDecision.AUTHORIZED, guard.authorize(ActionCapability.MOVEMENT));
        assertEquals(ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY, guard.authorize(ActionCapability.DIG));
        assertEquals(
            ActionAuthorizationDecision.AUTHORIZED,
            guard.authorizeAny(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.DIG)));
        assertEquals(
            ActionAuthorizationDecision.BLOCKED_MISSING_CAPABILITY,
            guard.authorizeAll(EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.DIG)));
        guard.recordBlockedAction("block digging");
        assertEquals(1L, guard.getBlockedActionCount());
        assertEquals("block digging", guard.getLastBlockedAction());
    }

    @Test
    public void revokedEpochRemainsBlockedUntilProducerCleanupAndTheNettyBarrier() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        broker.addRevocationListener(guard);
        guard.begin(lease);

        broker.revokeAll();

        assertEquals(ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH, guard.authorize(ActionCapability.MOVEMENT));
        assertTrue(guard.isGuarding());
        guard.end(lease);
        assertEquals(ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH, guard.authorize(ActionCapability.MOVEMENT));
        long drainGeneration = guard.drainGenerationOrZero();
        assertTrue(drainGeneration > 0L);
        assertTrue(guard.completeDrain(drainGeneration));
        assertEquals(ActionAuthorizationDecision.PLAYER_PASSTHROUGH, guard.authorize(ActionCapability.MOVEMENT));
    }

    @Test
    public void aNewSessionCannotStartBeforeThePreviousGenerationDrains() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        ActionLease first = broker.tryAcquire("first", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        guard.begin(first);
        guard.quarantine(first);
        guard.end(first);
        first.close();
        ActionLease second = broker.tryAcquire("second", EnumSet.of(ActionCapability.MOVEMENT))
            .get();

        try {
            guard.begin(second);
            fail("a quarantined generation must reject the next session");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("has not drained"));
        }
        assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
        guard.begin(second);
        assertEquals(ActionSessionGuard.Mode.ACTIVE, guard.getMode());
    }

    @Test
    public void safetyLockdownCannotBeReleasedByAnOrdinaryDrainBarrier() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        broker.addRevocationListener(guard);

        broker.enterSafetyLockdown();

        assertEquals(ActionSessionGuard.Mode.SAFETY_LOCKDOWN, guard.getMode());
        assertEquals(0L, guard.drainGenerationOrZero());
        assertFalse(guard.completeDrain(1L));
        assertEquals(ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH, guard.authorizeUnknownAction());

        broker.leaveSafetyLockdown();
        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());
        assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
        assertTrue(guard.isReadyForSession());
    }

    @Test
    public void aLateOrdinaryRevocationCannotDowngradeSafetyLockdown() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        guard.begin(lease);

        guard.onActionEpochRevoked(new ActionRevocation(2L, 3L, ActionRevocationReason.SAFETY_LOCKDOWN));
        guard.onActionEpochRevoked(new ActionRevocation(1L, 2L, ActionRevocationReason.EXPLICIT_REVOCATION));
        guard.end(lease);

        assertEquals(ActionSessionGuard.Mode.SAFETY_LOCKDOWN, guard.getMode());
        assertEquals(0L, guard.drainGenerationOrZero());
        assertEquals(ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH, guard.authorizeUnknownAction());
    }

    @Test
    public void aLateSafetyCallbackCannotUndoANewerLockdownRelease() {
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();

        guard.onActionEpochRevoked(new ActionRevocation(1L, 2L, ActionRevocationReason.SAFETY_LOCKDOWN));
        guard.onActionEpochRevoked(new ActionRevocation(2L, 3L, ActionRevocationReason.SAFETY_LOCKDOWN_RELEASED));
        guard.onActionEpochRevoked(new ActionRevocation(1L, 2L, ActionRevocationReason.SAFETY_LOCKDOWN));

        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());
        assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
        assertTrue(guard.isReadyForSession());
    }

    @Test
    public void losingTheFirewallQuarantinesAnActiveSession() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        guard.begin(lease);

        guard.markFirewallUnavailable();

        assertEquals(ActionSessionGuard.Mode.QUARANTINED, guard.getMode());
        assertFalse(guard.isReadyForSession());
        assertEquals(ActionAuthorizationDecision.BLOCKED_REVOKED_EPOCH, guard.authorize(ActionCapability.MOVEMENT));
    }
}
