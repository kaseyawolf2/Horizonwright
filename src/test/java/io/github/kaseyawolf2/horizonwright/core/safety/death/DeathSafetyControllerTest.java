package io.github.kaseyawolf2.horizonwright.core.safety.death;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.EnumSet;

import org.junit.Test;

public class DeathSafetyControllerTest {

    @Test
    public void criticalHealthImmediatelyBlocksDangerousActionsAndRequiresTwentyActuallyConsecutiveTicks() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();

        DeathSafetyUpdate critical = harness.controller.onHealthObservation(harness.nextTick(), 8.0D, 20.0D, null);

        assertEquals(
            DeathSafetyState.CRITICAL,
            critical.getSnapshot()
                .getState());
        assertTrue(critical.hasDirective(DeathSafetyDirective.ENTER_CRITICAL_RESTRICTIONS));
        assertFalse(
            critical.getSnapshot()
                .areDangerousActionsAllowed());
        assertTrue(
            critical.getSnapshot()
                .isMovementOnlyRetreatRequired());
        assertEquals(1, harness.interlock.criticalEntries);

        for (int i = 0; i < 9; i++) {
            harness.controller.onHealthObservation(harness.nextTick(), 13.0D, 20.0D, null);
        }
        int beforeDuplicate = harness.controller.snapshot()
            .getHealthyStableTicks();
        harness.controller.onHealthObservation(harness.sameTick(), 13.0D, 20.0D, null);
        assertEquals(
            beforeDuplicate,
            harness.controller.snapshot()
                .getHealthyStableTicks());

        harness.controller.onHealthObservation(harness.nextTick(), 12.0D, 20.0D, null);
        assertEquals(
            0,
            harness.controller.snapshot()
                .getHealthyStableTicks());
        for (int i = 0; i < 19; i++) {
            harness.controller.onHealthObservation(harness.nextTick(), 13.0D, 20.0D, null);
        }
        assertEquals(
            DeathSafetyState.CRITICAL,
            harness.controller.snapshot()
                .getState());

        DeathSafetyUpdate recovered = harness.controller.onHealthObservation(harness.nextTick(), 13.0D, 20.0D, null);
        assertEquals(
            DeathSafetyState.ACTIVE,
            recovered.getSnapshot()
                .getState());
        assertTrue(recovered.hasDirective(DeathSafetyDirective.RELEASE_CRITICAL_RESTRICTIONS));
        assertEquals(1, harness.interlock.criticalReleases);
    }

    @Test
    public void firstLethalHealthSignalStoresProjectionThenInvokesEverySynchronousStopObligation() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        final boolean[] stateWasLatchedInsideCallback = { false };
        harness.interlock.latchInspector = () -> stateWasLatchedInsideCallback[0] = harness.controller.snapshot()
            .getState() == DeathSafetyState.DEATH_LATCHED && harness.controller.unresolvedDeathProjection()
                .isPresent();

        DeathSafetyUpdate update = harness.controller
            .onHealthObservation(harness.nextTick(), 0.0D, 20.0D, harness.deathContext());

        assertTrue(stateWasLatchedInsideCallback[0]);
        assertEquals(1, harness.interlock.latches.size());
        DeathLatchRecord latch = harness.interlock.latches.get(0);
        assertEquals(DeathSignal.LETHAL_HEALTH_PACKET, latch.getSignal());
        assertEquals(EnumSet.allOf(EmergencyStopAction.class), latch.getRequiredActions());
        assertEquals(
            DeathSafetyState.DEATH_LATCHED,
            update.getSnapshot()
                .getState());
        assertTrue(
            update.getSnapshot()
                .areAllAutomationInputOwnersBlocked());
        assertFalse(
            update.getSnapshot()
                .isUnattendedOperationAllowed());
        assertTrue(
            update.getDirectives()
                .containsAll(
                    EnumSet.of(
                        DeathSafetyDirective.FORCE_CHECKPOINT_ACTIVE_TASK,
                        DeathSafetyDirective.CANCEL_ALL_NAVIGATION_AND_PENDING_WORK,
                        DeathSafetyDirective.REVOKE_ALL_ACTION_LEASES,
                        DeathSafetyDirective.CLEAR_ALL_INPUT_AND_KEYBINDINGS,
                        DeathSafetyDirective.CLEAR_NAVIGATION_PRIVATE_INPUT,
                        DeathSafetyDirective.RELEASE_ALL_HELD_USE,
                        DeathSafetyDirective.INVALIDATE_ACTION_AND_CONTAINER_EPOCHS,
                        DeathSafetyDirective.ENGAGE_DEATH_LOCKDOWN,
                        DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH)));

        UnresolvedDeathProjection projection = update.getSnapshot()
            .getUnresolvedDeathProjection()
            .orElse(null);
        assertNotNull(projection);
        assertEquals(DeathSafetyTestHarness.SERVER, projection.getServerIdentity());
        assertEquals(DeathSafetyTestHarness.WORLD, projection.getWorldIdentity());
        assertEquals(DeathSafetyTestHarness.DEATH_POSITION, projection.getDeathPosition());
        assertEquals(DeathSafetyTestHarness.OLD_PLAYER, projection.getOldPlayerIdentity());
        assertEquals(
            "active-excavation",
            projection.getActiveTaskId()
                .orElse(null));
        assertEquals(harness.preDeathInventory.getContentFingerprint(), projection.getPreDeathInventoryFingerprint());
    }

    @Test
    public void everyRedundantDeathSignalLatchesAndOnlyTheFirstSignalHasEffects() {
        for (DeathSignal signal : DeathSignal.values()) {
            DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
            DeathSafetyUpdate first = harness.controller
                .onDeathSignal(harness.nextTick(), signal, harness.deathContext());
            DeathSafetyUpdate duplicate = harness.controller
                .onDeathSignal(harness.nextTick(), DeathSignal.GAME_OVER_SCREEN, harness.deathContext());

            assertEquals(
                DeathSafetyState.DEATH_LATCHED,
                first.getSnapshot()
                    .getState());
            assertEquals(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE, duplicate.getDisposition());
            assertTrue(
                duplicate.getDirectives()
                    .isEmpty());
            assertEquals(1, harness.interlock.latches.size());
        }
    }

    @Test
    public void positiveHealthDelayedGameOverClosingAndReconnectCannotClearTheLatch() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        long deathEpoch = harness.latch();

        for (int i = 0; i < 100; i++) {
            harness.controller.onHealthObservation(harness.nextTick(), 20.0D, 20.0D, null);
        }
        harness.controller.onDeathSignal(harness.nextTick(), DeathSignal.GAME_OVER_SCREEN, harness.deathContext());
        assertEquals(
            DeathSafetyState.DEATH_LATCHED,
            harness.controller.snapshot()
                .getState());
        assertEquals(
            deathEpoch,
            harness.controller.snapshot()
                .getDeathEpoch());

        harness.disconnectAndReconnect(8L, DeathSafetyTestHarness.NEW_PLAYER);
        assertEquals(
            deathEpoch,
            harness.controller.snapshot()
                .getDeathEpoch());
        assertEquals(
            RecoveryPhase.REVALIDATING_RESPAWN,
            harness.controller.snapshot()
                .getRecoveryPhase());
        assertFalse(
            harness.controller.snapshot()
                .areDangerousActionsAllowed());
        assertEquals(1, harness.interlock.lockdownReaffirmations);
    }

    @Test
    public void respawnPacketIsAuthorizedExactlyOnceIncludingAcrossRestart() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        long deathEpoch = harness.latch();

        DeathSafetyUpdate first = harness.controller.authorizeRespawnPacket(harness.nextTick(), deathEpoch);
        DeathSafetyUpdate second = harness.controller.authorizeRespawnPacket(harness.nextTick(), deathEpoch);

        assertTrue(first.hasDirective(DeathSafetyDirective.SEND_EXACTLY_ONE_RESPAWN));
        assertEquals(
            DeathSafetyState.RESPAWN_REQUESTED,
            first.getSnapshot()
                .getState());
        assertFalse(second.hasDirective(DeathSafetyDirective.SEND_EXACTLY_ONE_RESPAWN));
        UnresolvedDeathProjection projection = second.getSnapshot()
            .getUnresolvedDeathProjection()
            .get();
        assertTrue(projection.isRespawnRequestConsumed());

        DeathSafetyTestHarness.RecordingInterlock restoredInterlock = new DeathSafetyTestHarness.RecordingInterlock();
        DeathSafetyController restored = DeathSafetyController.restore(
            harness.policy,
            restoredInterlock,
            DeathSafetyTestHarness.connection(99L, DeathSafetyTestHarness.NEW_PLAYER),
            projection);
        DeathSafetyUpdate afterRestart = restored.authorizeRespawnPacket(new SafetyEventStamp(99L, 1L, 1L), deathEpoch);
        assertFalse(afterRestart.hasDirective(DeathSafetyDirective.SEND_EXACTLY_ONE_RESPAWN));
        assertEquals(1, restoredInterlock.lockdownReaffirmations);
    }

    @Test
    public void rapidRespawnRequiresDifferentPlayerAndTwentyStableTicks() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        long deathEpoch = harness.latch();

        harness.controller.onRespawnObservation(
            harness.nextTick(),
            deathEpoch,
            new RespawnObservation(
                DeathSafetyTestHarness.OLD_PLAYER,
                20.0D,
                false,
                true,
                true,
                DeathSafetyTestHarness.DEATH_POSITION,
                harness.emptyRespawnInventory));
        assertEquals(
            0,
            harness.controller.snapshot()
                .getRespawnStableTicks());

        for (int i = 0; i < 19; i++) {
            harness.controller.onRespawnObservation(
                harness.nextTick(),
                deathEpoch,
                new RespawnObservation(
                    DeathSafetyTestHarness.NEW_PLAYER,
                    20.0D,
                    false,
                    true,
                    true,
                    DeathSafetyTestHarness.DEATH_POSITION,
                    harness.emptyRespawnInventory));
        }
        assertEquals(
            DeathSafetyState.POST_RESPAWN_QUARANTINE,
            harness.controller.snapshot()
                .getState());
        assertFalse(
            harness.controller.snapshot()
                .getRecoveryNavigationRequest()
                .isPresent());

        harness.controller.onDeathSignal(harness.sameTick(), DeathSignal.GAME_OVER_SCREEN, harness.deathContext());
        DeathSafetyUpdate ready = harness.controller.onRespawnObservation(
            harness.nextTick(),
            deathEpoch,
            new RespawnObservation(
                DeathSafetyTestHarness.NEW_PLAYER,
                20.0D,
                false,
                true,
                true,
                DeathSafetyTestHarness.DEATH_POSITION,
                harness.emptyRespawnInventory));
        assertEquals(
            DeathSafetyState.RECOVERY_READY,
            ready.getSnapshot()
                .getState());
        RecoveryNavigationRequest navigation = ready.getSnapshot()
            .getRecoveryNavigationRequest()
            .orElse(null);
        assertNotNull(navigation);
        assertFalse(navigation.areGenericInteractionsAllowed());
        assertEquals(DeathSafetyTestHarness.DEATH_POSITION, navigation.getTarget());
    }

    @Test
    public void staleConnectionSequenceTickAndDeathEpochEvidenceAreRejectedWithoutMutation() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        long deathEpoch = harness.latch();
        DeathSafetySnapshot before = harness.controller.snapshot();

        DeathSafetyUpdate staleConnection = harness.controller
            .onHealthObservation(new SafetyEventStamp(6L, 999L, 999L), 20.0D, 20.0D, null);
        assertEquals(DeathSafetyEventDisposition.STALE_CONNECTION_EPOCH, staleConnection.getDisposition());

        DeathSafetyUpdate staleSequence = harness.controller.onHealthObservation(
            new SafetyEventStamp(DeathSafetyTestHarness.FIRST_CONNECTION, harness.getSequence(), harness.getTick()),
            20.0D,
            20.0D,
            null);
        assertEquals(DeathSafetyEventDisposition.STALE_EVENT_SEQUENCE, staleSequence.getDisposition());

        DeathSafetyUpdate staleTick = harness.controller.onHealthObservation(
            new SafetyEventStamp(
                DeathSafetyTestHarness.FIRST_CONNECTION,
                harness.getSequence() + 10L,
                harness.getTick() - 1L),
            20.0D,
            20.0D,
            null);
        assertEquals(DeathSafetyEventDisposition.STALE_CLIENT_TICK, staleTick.getDisposition());

        DeathSafetyUpdate staleDeath = harness.controller.onRespawnObservation(
            new SafetyEventStamp(
                DeathSafetyTestHarness.FIRST_CONNECTION,
                harness.getSequence() + 11L,
                harness.getTick() + 1L),
            deathEpoch + 1L,
            new RespawnObservation(
                DeathSafetyTestHarness.NEW_PLAYER,
                20.0D,
                false,
                true,
                true,
                DeathSafetyTestHarness.DEATH_POSITION,
                harness.emptyRespawnInventory));
        assertEquals(DeathSafetyEventDisposition.STALE_DEATH_EPOCH, staleDeath.getDisposition());
        assertEquals(
            before.getDeathEpoch(),
            harness.controller.snapshot()
                .getDeathEpoch());
        assertEquals(
            before.getState(),
            harness.controller.snapshot()
                .getState());
    }

    @Test
    public void anInterlockFailureStillLeavesTheControllerLatchedFailClosed() {
        DeathSafetyInterlock throwing = new DeathSafetyInterlock() {

            @Override
            public void enterCriticalRestrictions() {}

            @Override
            public void releaseCriticalRestrictions() {}

            @Override
            public void latchDeath(DeathLatchRecord record) {
                throw new IllegalStateException("runtime cleanup failed");
            }

            @Override
            public void reaffirmDeathLockdown(long deathEpoch) {}

            @Override
            public void releaseDeathLockdown(long deathEpoch) {}
        };
        DeathSafetyController controller = new DeathSafetyController(
            DeathSafetyPolicy.planDefaults(6),
            throwing,
            DeathSafetyTestHarness.connection(1L, DeathSafetyTestHarness.OLD_PLAYER));
        try {
            controller.onDeathSignal(
                new SafetyEventStamp(1L, 1L, 1L),
                DeathSignal.LOCAL_DEATH_CALLBACK,
                new DeathSafetyTestHarness().deathContext());
            fail("expected interlock failure");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("cleanup failed"));
        }
        assertEquals(
            DeathSafetyState.DEATH_LATCHED,
            controller.snapshot()
                .getState());
        assertTrue(
            controller.unresolvedDeathProjection()
                .isPresent());
    }
}
