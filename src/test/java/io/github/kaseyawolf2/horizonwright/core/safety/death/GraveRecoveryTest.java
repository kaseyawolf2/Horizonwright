package io.github.kaseyawolf2.horizonwright.core.safety.death;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class GraveRecoveryTest {

    @Test
    public void delayedPlacementOneShotActivationAndArtificialLatencyRecoverOnlyAfterExactVerification() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        long deathEpoch = harness.latch();
        harness.stabilizeRespawn(deathEpoch);
        harness.arriveAtDeath(deathEpoch);

        for (int i = 0; i < 80; i++) {
            harness.controller.onGraveSearch(
                harness.nextTick(),
                deathEpoch,
                new GraveSearchObservation(
                    GraveSearchStatus.IN_PROGRESS,
                    Collections.<GraveCandidate>emptyList(),
                    harness.emptyRespawnInventory,
                    true));
        }
        assertEquals(
            RecoveryPhase.SEARCHING_FOR_GRAVE,
            harness.controller.snapshot()
                .getRecoveryPhase());

        GraveActivationPermit permit = harness.stabilizeGrave(deathEpoch);
        GraveActivationResult unsafePosture = harness.controller.authorizeGraveActivation(
            harness.nextTick(),
            new GraveActivationAttempt(permit.getPermitId(), deathEpoch, permit.getGraveIdentity(), false, true));
        assertEquals(GraveActivationDecision.REJECTED_UNSAFE_POSTURE, unsafePosture.getDecision());
        assertTrue(
            harness.controller.snapshot()
                .getGraveActivationPermit()
                .isPresent());

        GraveActivationResult authorized = harness.activate(permit);
        assertEquals(GraveActivationDecision.AUTHORIZED_AND_CONSUMED, authorized.getDecision());
        assertTrue(
            authorized.getUpdate()
                .hasDirective(DeathSafetyDirective.AUTHORIZE_EXACT_GRAVE_ACTIVATION));

        GraveActivationResult duplicate = harness.controller.authorizeGraveActivation(
            harness.nextTick(),
            new GraveActivationAttempt(permit.getPermitId(), deathEpoch, permit.getGraveIdentity(), true, true));
        assertEquals(GraveActivationDecision.REJECTED_ALREADY_CONSUMED, duplicate.getDecision());

        for (int i = 0; i < 100; i++) {
            DeathSafetyUpdate pending = harness.controller.onRecoveryVerification(
                harness.nextTick(),
                deathEpoch,
                new RecoveryVerificationObservation(
                    GraveResolution.PRESENT,
                    harness.grave,
                    harness.emptyRespawnInventory));
            assertEquals(
                DeathSafetyState.RECOVERY_READY,
                pending.getSnapshot()
                    .getState());
            assertEquals(
                RecoveryPhase.VERIFYING_RECOVERY,
                pending.getSnapshot()
                    .getRecoveryPhase());
        }

        DeathSafetyUpdate recovered = harness.controller.onRecoveryVerification(
            harness.nextTick(),
            deathEpoch,
            new RecoveryVerificationObservation(GraveResolution.REMOVED, null, harness.preDeathInventory));
        assertEquals(
            DeathSafetyState.ACTIVE,
            recovered.getSnapshot()
                .getState());
        assertTrue(recovered.hasDirective(DeathSafetyDirective.CLEAR_UNRESOLVED_DEATH));
        assertFalse(
            recovered.getSnapshot()
                .getUnresolvedDeathProjection()
                .isPresent());
        assertTrue(
            recovered.getSnapshot()
                .isUnattendedOperationAllowed());
        assertEquals(1, harness.interlock.lockdownReleases);

        DeathSafetyUpdate delayedOldGameOver = harness.controller
            .onDeathSignal(harness.nextTick(), DeathSignal.GAME_OVER_SCREEN, harness.deathContext());
        assertEquals(DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE, delayedOldGameOver.getDisposition());
        assertEquals(
            DeathSafetyState.ACTIVE,
            delayedOldGameOver.getSnapshot()
                .getState());
    }

    @Test
    public void activationIsBoundToCurrentConnectionEpochPermitAndExactGrave() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        GraveActivationPermit permit = harness.readyForActivation();
        GraveIdentity other = new GraveIdentity("other-grave", new DimensionBlockPosition(0, 12, 64, 10));

        GraveActivationResult wrongPermit = harness.controller.authorizeGraveActivation(
            harness.nextTick(),
            new GraveActivationAttempt(
                permit.getPermitId() + 1L,
                permit.getDeathEpoch(),
                permit.getGraveIdentity(),
                true,
                true));
        GraveActivationResult wrongGrave = harness.controller.authorizeGraveActivation(
            harness.nextTick(),
            new GraveActivationAttempt(permit.getPermitId(), permit.getDeathEpoch(), other, true, true));
        GraveActivationResult staleDeath = harness.controller.authorizeGraveActivation(
            harness.nextTick(),
            new GraveActivationAttempt(
                permit.getPermitId(),
                permit.getDeathEpoch() + 1L,
                permit.getGraveIdentity(),
                true,
                true));
        GraveActivationResult staleConnection = harness.controller.authorizeGraveActivation(
            new SafetyEventStamp(999L, 999L, 999L),
            new GraveActivationAttempt(
                permit.getPermitId(),
                permit.getDeathEpoch(),
                permit.getGraveIdentity(),
                true,
                true));

        assertEquals(GraveActivationDecision.REJECTED_WRONG_PERMIT, wrongPermit.getDecision());
        assertEquals(GraveActivationDecision.REJECTED_WRONG_GRAVE, wrongGrave.getDecision());
        assertEquals(GraveActivationDecision.REJECTED_STALE_DEATH_EPOCH, staleDeath.getDecision());
        assertEquals(GraveActivationDecision.REJECTED_STALE_CONNECTION, staleConnection.getDecision());
        assertTrue(
            harness.controller.snapshot()
                .getGraveActivationPermit()
                .isPresent());
        assertEquals(
            GraveActivationDecision.AUTHORIZED_AND_CONSUMED,
            harness.activate(permit)
                .getDecision());
    }

    @Test
    public void genericRecoveryInteractionsAndAnyRouteActionTargetingTheGraveFailClosed() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        long deathEpoch = harness.latch();
        harness.stabilizeRespawn(deathEpoch);
        RecoveryNavigationRequest request = harness.controller.snapshot()
            .getRecoveryNavigationRequest()
            .get();

        assertFalse(request.areGenericInteractionsAllowed());
        assertTrue(request.allowsRegisteredRouteAction(true, "minecraft:wooden_door"));
        assertFalse(request.allowsRegisteredRouteAction(false, "minecraft:wooden_door"));
        assertFalse(request.allowsRegisteredRouteAction(true, GraveProtectionPolicy.OPENBLOCKS_GRAVE));
        assertFalse(GraveProtectionPolicy.allowsGenericMining("openblocks:GRAVE"));
        assertFalse(GraveProtectionPolicy.allowsGenericUse("OpenBlocks:grave"));
        assertFalse(GraveProtectionPolicy.allowsGenericCombatTarget("OpenBlocks:grave"));
        assertFalse(GraveProtectionPolicy.allowsGenericScavenging("OpenBlocks:grave"));

        DeathSafetyUpdate held = harness.controller.onRecoveryNavigation(
            harness.nextTick(),
            deathEpoch,
            new RecoveryNavigationObservation(
                RecoveryNavigationStatus.IN_PROGRESS,
                new DimensionBlockPosition(-1, 0, 64, 0),
                harness.emptyRespawnInventory,
                true,
                false));
        assertManualHold(held, ManualHoldReason.UNSAFE_RECOVERY_NAVIGATION);
    }

    @Test
    public void dimensionRouteMayTravelButMustArriveAndFindTheGraveInTheRecordedDimension() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        long deathEpoch = harness.latch();
        harness.stabilizeRespawn(deathEpoch, harness.emptyRespawnInventory, new DimensionBlockPosition(-1, 0, 64, 0));

        DeathSafetyUpdate traveling = harness.controller.onRecoveryNavigation(
            harness.nextTick(),
            deathEpoch,
            new RecoveryNavigationObservation(
                RecoveryNavigationStatus.IN_PROGRESS,
                new DimensionBlockPosition(-1, 5, 70, 5),
                harness.emptyRespawnInventory,
                false,
                false));
        assertEquals(
            DeathSafetyState.RECOVERY_READY,
            traveling.getSnapshot()
                .getState());
        harness.arriveAtDeath(deathEpoch);

        GraveCandidate wrongDimension = DeathSafetyTestHarness.grave(
            "nether-grave",
            new DimensionBlockPosition(-1, 11, 64, 10),
            DeathSafetyTestHarness.OLD_PLAYER,
            harness.grave.getContents());
        DeathSafetyUpdate held = harness.controller.onGraveSearch(
            harness.nextTick(),
            deathEpoch,
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(wrongDimension),
                harness.emptyRespawnInventory,
                true));
        assertManualHold(held, ManualHoldReason.GRAVE_OUTSIDE_DEATH_DIMENSION);
    }

    @Test
    public void wrongOwnerMissingUnloadedEmptyAndMultipleGravesAllEnterIndefiniteHold() {
        assertSearchFailure(
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(
                    DeathSafetyTestHarness.grave(
                        "wrong-owner",
                        DeathSafetyTestHarness.GRAVE_POSITION,
                        "someone-else",
                        DeathSafetyTestHarness.inventory(9, DeathSafetyTestHarness.stack("minecraft:dirt", 1, 64)))),
                InventoryManifest.empty(36),
                true),
            ManualHoldReason.GRAVE_WRONG_OWNER);
        assertSearchFailure(
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.<GraveCandidate>emptyList(),
                InventoryManifest.empty(36),
                true),
            ManualHoldReason.GRAVE_MISSING);
        assertSearchFailure(
            new GraveSearchObservation(
                GraveSearchStatus.REGION_UNLOADED,
                Collections.<GraveCandidate>emptyList(),
                InventoryManifest.empty(36),
                true),
            ManualHoldReason.GRAVE_REGION_UNLOADED);
        assertSearchFailure(
            new GraveSearchObservation(
                GraveSearchStatus.EVIDENCE_UNAVAILABLE,
                Collections.<GraveCandidate>emptyList(),
                InventoryManifest.empty(36),
                true),
            ManualHoldReason.GRAVE_EVIDENCE_UNAVAILABLE);
        assertSearchFailure(
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(
                    DeathSafetyTestHarness.grave(
                        "empty",
                        DeathSafetyTestHarness.GRAVE_POSITION,
                        DeathSafetyTestHarness.OLD_PLAYER,
                        InventoryManifest.empty(9))),
                InventoryManifest.empty(36),
                true),
            ManualHoldReason.GRAVE_EMPTY);

        DeathSafetyTestHarness multipleHarness = preparedSearchHarness();
        long deathEpoch = multipleHarness.controller.snapshot()
            .getDeathEpoch();
        GraveCandidate second = DeathSafetyTestHarness.grave(
            "grave-b",
            new DimensionBlockPosition(0, 9, 64, 10),
            DeathSafetyTestHarness.OLD_PLAYER,
            multipleHarness.grave.getContents());
        DeathSafetyUpdate multiple = multipleHarness.controller.onGraveSearch(
            multipleHarness.nextTick(),
            deathEpoch,
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Arrays.asList(multipleHarness.grave, second),
                multipleHarness.emptyRespawnInventory,
                true));
        assertManualHold(multiple, ManualHoldReason.MULTIPLE_OWNED_GRAVES);
    }

    @Test
    public void capacityEmptyHandPreDeathMismatchAndChangingTileEvidenceFailClosed() {
        DeathSafetyTestHarness capacityHarness = preparedSearchHarness();
        long capacityEpoch = capacityHarness.controller.snapshot()
            .getDeathEpoch();
        InventoryManifest fullInventory = DeathSafetyTestHarness.inventory(
            2,
            DeathSafetyTestHarness.stack("minecraft:cobblestone", 64, 64),
            DeathSafetyTestHarness.stack("minecraft:dirt", 64, 64));
        DeathSafetyUpdate changedInventory = capacityHarness.controller.onGraveSearch(
            capacityHarness.nextTick(),
            capacityEpoch,
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(capacityHarness.grave),
                fullInventory,
                true));
        assertManualHold(changedInventory, ManualHoldReason.INVENTORY_CHANGED_DURING_RECOVERY);

        DeathSafetyPolicy fastPolicy = new DeathSafetyPolicy(0.4D, 0.6D, 1, 1, 2, 6);
        DeathSafetyTestHarness insufficient = new DeathSafetyTestHarness(fastPolicy);
        InventoryManifest nearlyFull = DeathSafetyTestHarness
            .inventory(2, DeathSafetyTestHarness.stack("minecraft:cobblestone", 64, 64));
        InventoryManifest preDeath = DeathSafetyTestHarness.inventory(
            3,
            DeathSafetyTestHarness.stack("minecraft:cobblestone", 64, 64),
            DeathSafetyTestHarness.stack("minecraft:diamond", 64, 64),
            DeathSafetyTestHarness.stack("gregtech:tool", 1, 1));
        DeathContext context = new DeathContext(
            DeathSafetyTestHarness.DEATH_POSITION,
            DeathSafetyTestHarness.OLD_PLAYER,
            null,
            preDeath);
        long insufficientEpoch = insufficient.controller
            .onDeathSignal(insufficient.nextTick(), DeathSignal.LOCAL_DEATH_CALLBACK, context)
            .getSnapshot()
            .getDeathEpoch();
        insufficient.stabilizeRespawn(insufficientEpoch, nearlyFull, DeathSafetyTestHarness.DEATH_POSITION);
        insufficient.controller.onRecoveryNavigation(
            insufficient.nextTick(),
            insufficientEpoch,
            new RecoveryNavigationObservation(
                RecoveryNavigationStatus.ARRIVED,
                DeathSafetyTestHarness.DEATH_POSITION,
                nearlyFull,
                false,
                false));
        GraveCandidate incoming = DeathSafetyTestHarness.grave(
            "capacity-grave",
            DeathSafetyTestHarness.GRAVE_POSITION,
            DeathSafetyTestHarness.OLD_PLAYER,
            DeathSafetyTestHarness.inventory(
                9,
                DeathSafetyTestHarness.stack("minecraft:diamond", 64, 64),
                DeathSafetyTestHarness.stack("gregtech:tool", 1, 1)));
        DeathSafetyUpdate noCapacity = insufficient.controller.onGraveSearch(
            insufficient.nextTick(),
            insufficientEpoch,
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(incoming),
                nearlyFull,
                true));
        assertManualHold(noCapacity, ManualHoldReason.INSUFFICIENT_INVENTORY_CAPACITY);

        DeathSafetyTestHarness noHand = preparedSearchHarness();
        DeathSafetyUpdate noHandUpdate = noHand.controller.onGraveSearch(
            noHand.nextTick(),
            noHand.controller.snapshot()
                .getDeathEpoch(),
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(noHand.grave),
                noHand.emptyRespawnInventory,
                false));
        assertManualHold(noHandUpdate, ManualHoldReason.NO_EMPTY_HOTBAR_HAND);

        DeathSafetyTestHarness mismatch = preparedSearchHarness();
        GraveCandidate missingTool = DeathSafetyTestHarness.grave(
            "grave-a",
            DeathSafetyTestHarness.GRAVE_POSITION,
            DeathSafetyTestHarness.OLD_PLAYER,
            DeathSafetyTestHarness
                .inventory(27, DeathSafetyTestHarness.stack("minecraft:diamond|meta=0|nbt=none", 12, 64)));
        DeathSafetyUpdate mismatchUpdate = mismatch.controller.onGraveSearch(
            mismatch.nextTick(),
            mismatch.controller.snapshot()
                .getDeathEpoch(),
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(missingTool),
                mismatch.emptyRespawnInventory,
                true));
        assertManualHold(mismatchUpdate, ManualHoldReason.PRE_DEATH_CONTENT_MISMATCH);

        DeathSafetyPolicy twoTickPolicy = new DeathSafetyPolicy(0.4D, 0.6D, 1, 1, 2, 6);
        DeathSafetyTestHarness changing = new DeathSafetyTestHarness(twoTickPolicy);
        long changingEpoch = changing.latch();
        changing.stabilizeRespawn(changingEpoch);
        changing.arriveAtDeath(changingEpoch);
        changing.controller.onGraveSearch(
            changing.nextTick(),
            changingEpoch,
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(changing.grave),
                changing.emptyRespawnInventory,
                true));
        GraveCandidate changedContents = DeathSafetyTestHarness.grave(
            "grave-a",
            DeathSafetyTestHarness.GRAVE_POSITION,
            DeathSafetyTestHarness.OLD_PLAYER,
            DeathSafetyTestHarness.inventory(
                27,
                DeathSafetyTestHarness.stack("minecraft:diamond|meta=0|nbt=none", 11, 64),
                DeathSafetyTestHarness.stack("gregtech:tool|meta=22|nbt=tool-a", 1, 1)));
        DeathSafetyUpdate changingUpdate = changing.controller.onGraveSearch(
            changing.nextTick(),
            changingEpoch,
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.singletonList(changedContents),
                changing.emptyRespawnInventory,
                true));
        assertManualHold(changingUpdate, ManualHoldReason.GRAVE_CHANGED);
    }

    @Test
    public void partialOrMismatchedRecoveryNeverResumesAutomation() {
        DeathSafetyTestHarness partial = new DeathSafetyTestHarness();
        GraveActivationPermit partialPermit = partial.readyForActivation();
        partial.activate(partialPermit);
        InventoryManifest onlyDiamonds = DeathSafetyTestHarness
            .inventory(36, DeathSafetyTestHarness.stack("minecraft:diamond|meta=0|nbt=none", 12, 64));
        DeathSafetyUpdate partialUpdate = partial.controller.onRecoveryVerification(
            partial.nextTick(),
            partialPermit.getDeathEpoch(),
            new RecoveryVerificationObservation(GraveResolution.REMOVED, null, onlyDiamonds));
        assertManualHold(partialUpdate, ManualHoldReason.PARTIAL_RECOVERY);

        DeathSafetyTestHarness mismatch = new DeathSafetyTestHarness();
        GraveActivationPermit mismatchPermit = mismatch.readyForActivation();
        mismatch.activate(mismatchPermit);
        InventoryManifest wrong = DeathSafetyTestHarness
            .inventory(36, DeathSafetyTestHarness.stack("minecraft:dirt", 1, 64));
        DeathSafetyUpdate mismatchUpdate = mismatch.controller.onRecoveryVerification(
            mismatch.nextTick(),
            mismatchPermit.getDeathEpoch(),
            new RecoveryVerificationObservation(GraveResolution.EMPTY, null, wrong));
        assertManualHold(mismatchUpdate, ManualHoldReason.RECOVERED_INVENTORY_MISMATCH);
    }

    @Test
    public void manualHoldPersistsIndefinitelyAcrossDisconnectReconnectAndRestartUntilHumanResolution() {
        DeathSafetyTestHarness harness = preparedSearchHarness();
        long deathEpoch = harness.controller.snapshot()
            .getDeathEpoch();
        DeathSafetyUpdate missing = harness.controller.onGraveSearch(
            harness.nextTick(),
            deathEpoch,
            new GraveSearchObservation(
                GraveSearchStatus.COMPLETE,
                Collections.<GraveCandidate>emptyList(),
                harness.emptyRespawnInventory,
                true));
        assertManualHold(missing, ManualHoldReason.GRAVE_MISSING);

        for (int i = 0; i < 500; i++) {
            harness.controller.onHealthObservation(harness.nextTick(), 20.0D, 20.0D, null);
        }
        assertEquals(
            DeathSafetyState.MANUAL_HOLD,
            harness.controller.snapshot()
                .getState());
        harness.disconnectAndReconnect(8L, DeathSafetyTestHarness.NEW_PLAYER);
        assertEquals(
            DeathSafetyState.MANUAL_HOLD,
            harness.controller.snapshot()
                .getState());

        UnresolvedDeathProjection projection = harness.controller.unresolvedDeathProjection()
            .get();
        DeathSafetyTestHarness.RecordingInterlock restoredInterlock = new DeathSafetyTestHarness.RecordingInterlock();
        DeathSafetyController restored = DeathSafetyController.restore(
            harness.policy,
            restoredInterlock,
            DeathSafetyTestHarness.connection(100L, DeathSafetyTestHarness.NEW_PLAYER),
            projection);
        assertEquals(
            DeathSafetyState.MANUAL_HOLD,
            restored.snapshot()
                .getState());
        assertEquals(
            ManualHoldReason.GRAVE_MISSING,
            restored.snapshot()
                .getManualHoldReason()
                .get());

        DeathSafetyUpdate refused = restored
            .resolveManualHoldByOperator(new SafetyEventStamp(100L, 1L, 1L), deathEpoch, false);
        assertEquals(
            DeathSafetyState.MANUAL_HOLD,
            refused.getSnapshot()
                .getState());
        DeathSafetyUpdate resolved = restored
            .resolveManualHoldByOperator(new SafetyEventStamp(100L, 2L, 2L), deathEpoch, true);
        assertEquals(
            DeathSafetyState.ACTIVE,
            resolved.getSnapshot()
                .getState());
        assertTrue(resolved.hasDirective(DeathSafetyDirective.CLEAR_UNRESOLVED_DEATH));
    }

    @Test
    public void restartDiscardsTransientGravePermitAndRequiresFullRespawnRevalidation() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        GraveActivationPermit oldPermit = harness.readyForActivation();
        UnresolvedDeathProjection projection = harness.controller.unresolvedDeathProjection()
            .get();
        DeathSafetyTestHarness.RecordingInterlock interlock = new DeathSafetyTestHarness.RecordingInterlock();
        DeathSafetyController restored = DeathSafetyController.restore(
            harness.policy,
            interlock,
            DeathSafetyTestHarness.connection(200L, DeathSafetyTestHarness.NEW_PLAYER),
            projection);

        assertEquals(
            RecoveryPhase.REVALIDATING_RESPAWN,
            restored.snapshot()
                .getRecoveryPhase());
        assertFalse(
            restored.snapshot()
                .getGraveActivationPermit()
                .isPresent());
        GraveActivationResult oldAttempt = restored.authorizeGraveActivation(
            new SafetyEventStamp(200L, 1L, 1L),
            new GraveActivationAttempt(
                oldPermit.getPermitId(),
                oldPermit.getDeathEpoch(),
                oldPermit.getGraveIdentity(),
                true,
                true));
        assertEquals(GraveActivationDecision.REJECTED_NO_PERMIT, oldAttempt.getDecision());

        for (int i = 0; i < harness.policy.getRespawnStableTicks(); i++) {
            restored.onRespawnObservation(
                new SafetyEventStamp(200L, i + 2L, i + 2L),
                oldPermit.getDeathEpoch(),
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
            RecoveryPhase.NAVIGATING_WITH_INTERACTIONS_DISABLED,
            restored.snapshot()
                .getRecoveryPhase());
    }

    private static DeathSafetyTestHarness preparedSearchHarness() {
        DeathSafetyTestHarness harness = new DeathSafetyTestHarness();
        long deathEpoch = harness.latch();
        harness.stabilizeRespawn(deathEpoch);
        harness.arriveAtDeath(deathEpoch);
        return harness;
    }

    private static void assertSearchFailure(GraveSearchObservation observation, ManualHoldReason reason) {
        DeathSafetyTestHarness harness = preparedSearchHarness();
        DeathSafetyUpdate update = harness.controller.onGraveSearch(
            harness.nextTick(),
            harness.controller.snapshot()
                .getDeathEpoch(),
            observation);
        assertManualHold(update, reason);
    }

    private static void assertManualHold(DeathSafetyUpdate update, ManualHoldReason reason) {
        assertEquals(
            DeathSafetyState.MANUAL_HOLD,
            update.getSnapshot()
                .getState());
        assertEquals(
            reason,
            update.getSnapshot()
                .getManualHoldReason()
                .orElse(null));
        assertFalse(
            update.getSnapshot()
                .isUnattendedOperationAllowed());
        assertTrue(
            update.getSnapshot()
                .areAllAutomationInputOwnersBlocked());
        assertTrue(
            update.getSnapshot()
                .getUnresolvedDeathProjection()
                .isPresent());
    }
}
