package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationGeometry;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetOutcome;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetResult;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryConfiguration;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedCause;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class ExcavationTaskRunnerTest {

    @Test
    public void pendingBreakReleasesLeaseAndNextBlockStartsBeforeAcknowledgement() {
        TaskSpec spec = beginAsyncTest("async-next", 3);
        ActionLease firstLease = harness.backend.lastLease;
        RecordingBackend.Handle first = markPending();
        harness.controller.tick();
        assertFalse(firstLease.isValid());
        assertEquals(0L, processed(spec));
        for (int i = 0; i < 12 && harness.backend.submissions < 2; i++) harness.controller.tick();
        assertEquals(2, harness.backend.submissions);
        assertEquals(ExcavationActionState.PENDING_CONFIRMATION, first.state);
        assertEquals(0L, processed(spec));
        assertNotEquals(
            first.request.getIntent()
                .getPosition(),
            harness.backend.lastRequest.getIntent()
                .getPosition());
        first.state = ExcavationActionState.CONFIRMED;
        harness.controller.tick();
        assertTrue(processed(spec) >= 1L);
    }

    @Test
    public void outOfOrderConfirmationsDoNotPersistPastAnEarlierPendingBlock() {
        TaskSpec spec = beginAsyncTest("async-order", 3);
        RecordingBackend.Handle first = markPending();
        harness.controller.tick();
        for (int i = 0; i < 12 && harness.backend.submissions < 2; i++) harness.controller.tick();
        RecordingBackend.Handle second = markPending();
        harness.controller.tick();
        second.state = ExcavationActionState.CONFIRMED;
        harness.controller.tick();
        assertEquals(0L, processed(spec));
        first.state = ExcavationActionState.CONFIRMED;
        harness.controller.tick();
        assertTrue(processed(spec) >= 2L);
    }

    @Test
    public void restoredPendingBlockCancelsCurrentActionAndRevisitsTheOriginalPosition() {
        TaskSpec spec = beginAsyncTest("async-restore", 3);
        RecordingBackend.Handle first = markPending();
        harness.controller.tick();
        for (int i = 0; i < 12 && harness.backend.submissions < 2; i++) harness.controller.tick();
        RecordingBackend.Handle second = harness.backend.active;
        harness.backend.confirmedClear.remove(
            first.request.getIntent()
                .getPosition());
        first.confirmation = null;
        first.state = ExcavationActionState.RETRY_REQUIRED;
        harness.controller.tick();
        assertEquals(ExcavationActionState.CANCELLED, second.state);
        assertEquals(0L, processed(spec));
        for (int i = 0; i < 12 && harness.backend.submissions < 3; i++) harness.controller.tick();
        assertEquals(3, harness.backend.submissions);
        assertEquals(
            first.request.getIntent()
                .getPosition(),
            harness.backend.lastRequest.getIntent()
                .getPosition());
    }

    @Test
    public void finalCompletionWaitsForPendingServerEvidence() {
        TaskSpec spec = beginAsyncTest("async-final", 0);
        RecordingBackend.Handle first = markPending();
        for (int i = 0; i < 12; i++) harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
        assertEquals(TaskState.RUNNING, task(harness.controller.snapshot(), spec.getId()).getState());
        assertEquals(0L, processed(spec));
        first.state = ExcavationActionState.CONFIRMED;
        for (int i = 0; i < 20; i++) harness.controller.tick();
        assertEquals(TaskState.COMPLETED, task(harness.controller.snapshot(), spec.getId()).getState());
    }

    @Test
    public void pendingQueueIsBoundedWhileTheServerIsSilent() {
        TaskSpec spec = beginAsyncTest("async-bound", 10);
        for (int i = 0; i < 250; i++) {
            if (harness.backend.active.state == ExcavationActionState.SUBMITTED) markPending();
            harness.controller.tick();
        }
        assertEquals(16, harness.backend.submissions);
        assertEquals(0L, processed(spec));
        assertEquals(TaskState.RUNNING, task(harness.controller.snapshot(), spec.getId()).getState());
    }

    @Test
    public void pauseAndRestartRetainTheFrontierBeforePendingBreaks() {
        TaskSpec spec = beginAsyncTest("async-restart", 3);
        RecordingBackend.Handle first = markPending();
        for (int i = 0; i < 8; i++) harness.controller.tick();
        TaskCheckpoint saved = task(harness.controller.snapshot(), spec.getId()).getCheckpoint();
        assertEquals(0L, processed(spec));
        harness.controller.pause(spec.getId());
        harness.controller.tick();
        assertEquals(ExcavationActionState.CANCELLED, first.state);
        harness.close();
        harness = new Harness();
        harness.controller.restore(spec, saved);
        for (int i = 0; i < 12 && harness.backend.submissions < 1; i++) harness.controller.tick();
        assertEquals(
            first.request.getIntent()
                .getPosition(),
            harness.backend.lastRequest.getIntent()
                .getPosition());
    }

    @Test
    public void missingServerAcknowledgementNeverCommitsSpeculativeProgress() {
        TaskSpec spec = beginAsyncTest("async-timeout", 3);
        RecordingBackend.Handle first = markPending();
        harness.controller.tick();
        harness.nowMillis = 5001L;
        TaskSnapshot result = task(harness.controller.tick(), spec.getId());
        assertEquals(ExcavationActionState.CANCELLED, first.state);
        assertEquals(0L, processed(spec));
        assertTrue(
            result.getDetail()
                .contains("Pending excavation confirmation failed"));
    }

    @Test
    public void unloadWaitsForPendingBreaksBeforeSuspendingExcavation() {
        TaskSpec spec = beginAsyncTest("async-service", 3);
        RecordingBackend.Handle first = markPending();
        harness.controller.tick();
        harness.backend.suspensionReason = ExcavationSuspensionReason.UNLOADING_REQUIRED;
        for (int i = 0; i < 8; i++) harness.controller.tick();
        assertEquals(TaskState.RUNNING, task(harness.controller.snapshot(), spec.getId()).getState());
        assertEquals(0L, processed(spec));
        first.state = ExcavationActionState.CONFIRMED;
        for (int i = 0; i < 8; i++) harness.controller.tick();
        assertEquals(TaskState.BLOCKED, task(harness.controller.snapshot(), spec.getId()).getState());
    }

    @Test
    public void backendReplacementCancelsPendingEvidenceAndRetainsSavedFrontier() {
        TaskSpec spec = beginAsyncTest("async-backend-change", 3);
        RecordingBackend.Handle first = markPending();
        harness.controller.tick();
        harness.access.backend = new RecordingBackend();
        harness.controller.tick();
        assertEquals(ExcavationActionState.CANCELLED, first.state);
        assertEquals(0L, processed(spec));
    }

    @Test
    public void repeatedRejectionOfSamePendingBlockIsBoundedWithoutCommittingIt() {
        TaskSpec spec = beginAsyncTest("async-repeat-rejection", 3);
        for (int rejection = 1; rejection <= 4; rejection++) {
            RecordingBackend.Handle pending = markPending();
            harness.controller.tick();
            harness.backend.confirmedClear.remove(
                pending.request.getIntent()
                    .getPosition());
            pending.state = ExcavationActionState.RETRY_REQUIRED;
            pending.confirmation = null;
            TaskSnapshot result = task(harness.controller.tick(), spec.getId());
            assertEquals(0L, processed(spec));
            if (rejection == 4) {
                assertTrue(
                    result.getDetail()
                        .contains("Repeated server rejection"));
            } else {
                int previous = harness.backend.submissions;
                for (int i = 0; i < 12 && harness.backend.submissions == previous; i++) harness.controller.tick();
                assertEquals(previous + 1, harness.backend.submissions);
            }
        }
    }

    private TaskSpec beginAsyncTest(String id, int radius) {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder(id, 0, 0, 0, radius, 12, 12);
        harness.controller.submit(spec);
        for (int i = 0; i < 12 && harness.backend.submissions < 1; i++) harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
        assertTrue(harness.backend.lastRequest.isAsyncConfirmation());
        return spec;
    }

    private RecordingBackend.Handle markPending() {
        harness.backend.confirm();
        harness.backend.active.confirmation = harness.backend.active.confirmation.withBrokenTargets(1);
        harness.backend.active.state = ExcavationActionState.PENDING_CONFIRMATION;
        return harness.backend.active;
    }

    private long processed(TaskSpec spec) {
        return ExcavationTaskCheckpointCodec
            .decode(ExcavationTask.parse(spec), task(harness.controller.snapshot(), spec.getId()).getCheckpoint())
            .getProgress()
            .getProcessed();
    }

    @Test
    public void periodicPickupWaitsTwoMinutesFromStartEvenAfterManyTargets() {
        harness = new Harness();
        harness.nowMillis = 1_000_000L;
        harness.backend.collectDrops = true;
        harness.controller.submit(ExcavationTask.cleanVolumeCylinder("pickup-cadence", 0, 0, 0, 10, 12, 12));
        for (int i = 0; i < 200 && harness.backend.submissions < 21; i++) {
            if (harness.backend.active != null) harness.backend.confirm();
            harness.controller.tick();
            assertTrue("Startup and 16 targets must not trigger pickup", harness.backend.collectionLease == null);
        }
        assertTrue(harness.backend.submissions >= 21);
        harness.nowMillis += 119_999L;
        harness.backend.confirm();
        for (int i = 0; i < 5; i++) harness.controller.tick();
        assertTrue(harness.backend.collectionLease == null);
        harness.nowMillis++;
        harness.backend.confirm();
        for (int i = 0; i < 10 && harness.backend.collectionLease == null; i++) harness.controller.tick();
        assertNotNull(harness.backend.collectionLease);
    }

    @Test
    public void finalCollectionRetainsCheckpointAndReleasesMovementBeforeCompletion() {
        harness = new Harness();
        harness.backend.collectDrops = true;
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("collect", 0, 0, 0, 0, 12, 12);
        harness.controller.submit(spec);
        for (int i = 0; i < 4; i++) harness.controller.tick();
        harness.backend.confirm();
        for (int i = 0; i < 25 && harness.backend.collectionLease == null; i++) harness.controller.tick();
        assertNotNull(harness.backend.collectionLease);
        assertTrue(harness.backend.collectionLease.isValid());
        assertFalse(task(harness.controller.snapshot(), spec.getId()).getState() == TaskState.COMPLETED);
        harness.backend.collectionDone = true;
        for (int i = 0; i < 25; i++) harness.controller.tick();
        assertTrue(harness.backend.collectionClosed);
        assertFalse(harness.backend.collectionLease.isValid());
        assertEquals(TaskState.COMPLETED, task(harness.controller.snapshot(), spec.getId()).getState());
    }

    @Test
    public void interruptingCollectionCancelsItsMovementAndLeavesExcavationResumable() {
        harness = new Harness();
        harness.backend.collectDrops = true;
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("collect-stop", 0, 0, 0, 0, 12, 12);
        harness.controller.submit(spec);
        for (int i = 0; i < 4; i++) harness.controller.tick();
        harness.backend.confirm();
        for (int i = 0; i < 25 && harness.backend.collectionLease == null; i++) harness.controller.tick();
        assertNotNull(harness.backend.collectionLease);
        harness.controller.pause(spec.getId());
        for (int i = 0; i < 3; i++) harness.controller.tick();
        assertTrue(harness.backend.collectionClosed);
        assertFalse(harness.backend.collectionLease.isValid());
        assertEquals(TaskState.SUSPENDED, task(harness.controller.snapshot(), spec.getId()).getState());
    }

    private Harness harness;

    @Test
    public void walkingModeSubmitsNextTargetOnCleanAuditTick() {
        harness = new Harness();
        TaskSpec base = ExcavationTask.cleanVolumeCylinder("walking", 0, 8, 8, 2, 12, 12);
        Map<String, String> parameters = new java.util.LinkedHashMap<>(base.getParameters());
        parameters.put("movingMining", "true");
        TaskSpec spec = new TaskSpec(base.getId(), base.getType(), base.getDisplayName(), base.getLane(), parameters);
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
        harness.backend.confirm();
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
        harness.controller.tick();
        assertEquals(2, harness.backend.submissions);
        assertTrue(harness.backend.lastRequest.isMovingMining());
        assertEquals(
            "1",
            task(harness.controller.snapshot(), spec.getId()).getCheckpoint()
                .getValues()
                .get("progress.completed"));
    }

    @After
    public void closeHarness() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    public void disabledDefaultBlocksWithoutBindingOrMutatingTheCheckpoint() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        TaskOrchestrator controller = new TaskOrchestrator(
            new FixedClock(),
            new RuntimeTaskRunnerFactory(UnusedNavigationAccess.INSTANCE),
            broker);
        try {
            TaskSpec spec = ExcavationTask.cleanVolumeCylinder("disabled", 0, 8, 8, 0, 12, 12);
            controller.submit(spec);

            TaskSnapshot blocked = task(controller.tick(), spec.getId());

            assertEquals(TaskState.BLOCKED, blocked.getState());
            assertEquals(TaskCheckpoint.empty(), blocked.getCheckpoint());
            assertEquals(
                BlockedCause.MISSING_REQUIREMENT,
                blocked.getBlockedReason()
                    .get()
                    .getCause());
            assertTrue(
                broker.snapshot()
                    .getActiveOwners()
                    .isEmpty());
        } finally {
            controller.close();
        }
    }

    @Test
    public void oneTargetDoesNotAdvanceUntilTheExactPostActionConfirmation() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("one", 0, 8, 8, 0, 12, 12);
        harness.controller.submit(spec);

        TaskSnapshot bound = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, bound.getState());
        assertEquals(
            1L,
            bound.getCheckpoint()
                .getRevision());
        assertEquals(
            "0",
            bound.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        assertEquals(0, harness.backend.observations);
        assertEquals(0, harness.backend.submissions);

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, submitted.getState());
        assertEquals(
            1L,
            submitted.getCheckpoint()
                .getRevision());
        assertEquals(1, harness.backend.observations);
        assertEquals(1, harness.backend.submissions);
        assertEquals(
            "0",
            submitted.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        assertTrue(
            harness.backend.lastLease.getCapabilities()
                .contains(ActionCapability.LOOK));
        assertTrue(
            harness.backend.lastLease.getCapabilities()
                .contains(ActionCapability.DIG));
        assertTrue(
            harness.backend.lastLease.getCapabilities()
                .contains(ActionCapability.MOVEMENT));
        assertTrue(
            harness.backend.lastLease.getCapabilities()
                .contains(ActionCapability.PLACE));
        assertTrue(
            harness.backend.lastLease.getCapabilities()
                .contains(ActionCapability.HELD_USE));

        TaskSnapshot waiting = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, waiting.getState());
        assertEquals(
            1L,
            waiting.getCheckpoint()
                .getRevision());
        assertEquals(1, harness.backend.observations);
        assertEquals(1, harness.backend.submissions);

        harness.backend.confirm();
        TaskSnapshot verifying = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, verifying.getState());
        assertTrue(
            verifying.getDetail()
                .contains("verifying the entire cleared volume"));

        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(
            2L,
            completed.getCheckpoint()
                .getRevision());
        assertEquals(
            "1",
            completed.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        assertEquals(
            "1",
            completed.getCheckpoint()
                .getValues()
                .get("progress.total"));
        assertEquals(
            "completed",
            completed.getCheckpoint()
                .getValues()
                .get("phase"));
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void rediscoveredBlockMustBeClearedBeforeACompletelyCleanVerificationPassCompletes() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("reconcile", 0, 8, 8, 0, 12, 12);
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirm();

        TaskSnapshot verifying = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, verifying.getState());
        harness.backend.confirmedClear.clear();

        TaskSnapshot rediscovered = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, rediscovered.getState());
        assertTrue(
            rediscovered.getDetail()
                .contains("Rediscovered block"));
        assertEquals(2, harness.backend.submissions);

        harness.backend.confirm();
        TaskSnapshot cleared = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, cleared.getState());
        assertTrue(
            cleared.getDetail()
                .contains("continuing cleared-area verification"));

        TaskSnapshot repeating = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, repeating.getState());
        assertTrue(
            repeating.getDetail()
                .contains("final clean verification pass"));

        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(
            "completed",
            completed.getCheckpoint()
                .getValues()
                .get("phase"));
    }

    @Test
    public void completedLayerIsRecheckedAndRepairedBeforeDescending() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("layer-reconcile", 0, 8, 8, 0, 11, 12);
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirm();

        TaskSnapshot layerVerification = task(harness.controller.tick(), spec.getId());
        assertTrue(
            layerVerification.getDetail()
                .contains("verifying completed layer 12"));
        harness.backend.confirmedClear.clear();

        TaskSnapshot rediscovered = task(harness.controller.tick(), spec.getId());
        assertTrue(
            rediscovered.getDetail()
                .contains("Rediscovered block"));
        assertEquals(2, harness.backend.submissions);

        harness.backend.confirm();
        harness.controller.tick();
        TaskSnapshot rechecking = task(harness.controller.tick(), spec.getId());
        assertTrue(
            rechecking.getDetail()
                .contains("rechecking completed layer 12"));

        TaskSnapshot layerClean = task(harness.controller.tick(), spec.getId());
        assertTrue(
            layerClean.getDetail()
                .contains("clean verification of layer 12"));

        harness.controller.tick();
        assertEquals(3, harness.backend.submissions);
        assertEquals(
            11,
            harness.backend.lastRequest.getIntent()
                .getPosition()
                .getY());
    }

    @Test
    public void everyBreakSystematicallyAuditsAndRediscoversProcessedBlocks() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("cache-reconcile", 0, 8, 8, 1, 64, 64);
        harness.controller.submit(spec);
        harness.controller.tick();

        harness.controller.tick();
        harness.backend.confirm();
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
        harness.backend.confirmedClear.clear();

        TaskSnapshot rediscovered = task(harness.controller.tick(), spec.getId());

        assertTrue(
            rediscovered.getDetail()
                .contains("reset primary excavation to layer 64"));
        CylinderExcavationSpec cylinder = ExcavationTask.parse(spec);
        ExcavationCheckpoint reset = ExcavationTaskCheckpointCodec.decode(cylinder, rediscovered.getCheckpoint());
        assertEquals(CylinderExcavationGeometry.layerStart(cylinder, 64), reset.getFrontier());
        assertEquals(1, harness.backend.submissions);

        harness.controller.tick();
        assertEquals(2, harness.backend.submissions);
        assertTrue(
            harness.backend.lastRequest.getIntent()
                .getPosition()
                .getY() == 64);
    }

    @Test
    public void rediscoveryResetsThePrimaryLayerAndNeverJumpsBackToTheObsoleteFrontier() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("layer-range-reconcile", 0, 8, 8, 1, 64, 65);
        CylinderExcavationSpec cylinder = ExcavationTask.parse(spec);
        harness.controller.submit(spec);
        harness.controller.tick();

        TaskSnapshot snapshot = null;
        for (int tick = 0; tick < 200; tick++) {
            if (harness.backend.active != null && harness.backend.active.state == ExcavationActionState.SUBMITTED) {
                harness.backend.confirm();
            }
            snapshot = task(harness.controller.tick(), spec.getId());
            if ("64".equals(
                snapshot.getCheckpoint()
                    .getValues()
                    .get("frontier.layerY"))) {
                break;
            }
        }
        assertNotNull(snapshot);
        assertEquals(
            "64",
            snapshot.getCheckpoint()
                .getValues()
                .get("frontier.layerY"));

        int submissionsBeforeLowerLayer = harness.backend.submissions;
        for (int tick = 0; tick < 20 && harness.backend.submissions == submissionsBeforeLowerLayer; tick++) {
            harness.controller.tick();
        }
        assertTrue(harness.backend.submissions > submissionsBeforeLowerLayer);
        assertEquals(
            64,
            harness.backend.lastRequest.getIntent()
                .getPosition()
                .getY());
        BlockPosition retainedLowerPosition = harness.backend.lastRequest.getIntent()
            .getPosition();
        harness.backend.confirm();
        TaskSnapshot lowerApplied = task(harness.controller.tick(), spec.getId());
        ExcavationFrontier obsoleteLowerFrontier = ExcavationTaskCheckpointCodec
            .decode(cylinder, lowerApplied.getCheckpoint())
            .getFrontier();
        harness.backend.confirmedClear.clear();
        harness.backend.confirmedClear.add(retainedLowerPosition);

        TaskSnapshot rediscovered = task(harness.controller.tick(), spec.getId());
        assertTrue(
            rediscovered.getDetail()
                .contains("reset primary excavation to layer 65"));
        ExcavationCheckpoint reset = ExcavationTaskCheckpointCodec.decode(cylinder, rediscovered.getCheckpoint());
        assertEquals(CylinderExcavationGeometry.layerStart(cylinder, 65), reset.getFrontier());

        ExcavationCheckpoint descended = reset;
        for (int tick = 0; tick < 300; tick++) {
            TaskSnapshot progress = task(harness.controller.tick(), spec.getId());
            if (harness.backend.active != null && harness.backend.active.state == ExcavationActionState.SUBMITTED) {
                harness.backend.confirm();
            }
            descended = ExcavationTaskCheckpointCodec.decode(cylinder, progress.getCheckpoint());
            if (!descended.getFrontier()
                .isComplete()
                && descended.getFrontier()
                    .getLayerY() == 64) {
                break;
            }
        }
        assertEquals(CylinderExcavationGeometry.layerStart(cylinder, 64), descended.getFrontier());
        assertTrue("reset unexpectedly restored the obsolete lower frontier", !obsoleteLowerFrontier.equals(descended));
    }

    @Test
    public void emptyMaximumRadiusLayerAdvancesByTheBoundedScanWithoutSubmittingActions() {
        harness = new Harness();
        harness.backend.classification = ExcavationBlockClassification.AIR;
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("empty-wide", 0, 0, 0, 250, 64, 64);
        harness.controller.submit(spec);
        harness.controller.tick();

        TaskSnapshot advanced = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, advanced.getState());
        assertEquals(4096, harness.backend.observations);
        assertEquals(0, harness.backend.submissions);
        assertEquals(
            "4096",
            advanced.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        assertEquals(
            "196321",
            advanced.getCheckpoint()
                .getValues()
                .get("progress.total"));
        Map<String, String> progress = advanced.getCheckpoint()
            .getValues();
        assertEquals(
            192225L,
            Long.parseLong(progress.get("progress.total")) - Long.parseLong(progress.get("progress.completed"))
                - Long.parseLong(progress.get("progress.protected"))
                - Long.parseLong(progress.get("progress.unreachable"))
                - Long.parseLong(progress.get("progress.fluidContained"))
                - Long.parseLong(progress.get("progress.failed")));
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void passivePrefixStopsBeforeBreakableTargetAndReobservesItForAction() {
        harness = new Harness();
        harness.backend.airObservationsBeforeBreakable = 5;
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("air-prefix", 0, 8, 8, 2, 12, 12);
        harness.controller.submit(spec);
        harness.controller.tick();

        TaskSnapshot skipped = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, skipped.getState());
        assertEquals(6, harness.backend.observations);
        assertEquals(0, harness.backend.submissions);
        assertEquals(
            "5",
            skipped.getCheckpoint()
                .getValues()
                .get("progress.completed"));

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, submitted.getState());
        assertEquals(7, harness.backend.observations);
        assertEquals(1, harness.backend.submissions);
        assertEquals(
            "5",
            submitted.getCheckpoint()
                .getValues()
                .get("progress.completed"));
    }

    @Test
    public void staleConfirmationIsRejectedWithoutAdvancingTheFrontier() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("stale", 0, 8, 8, 0, 12, 12);
        harness.controller.submit(spec);
        harness.controller.tick();
        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        harness.backend.confirmationEpochOffset = 1L;
        harness.backend.confirm();

        TaskSnapshot failed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.FAILED, failed.getState());
        assertEquals(submitted.getCheckpoint(), failed.getCheckpoint());
        assertEquals(
            "0",
            failed.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        assertTrue(
            failed.getDetail()
                .contains("stale or mismatched confirmation"));
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void staleObservationIsRejectedBeforeAnyLeaseOrAction() {
        harness = new Harness();
        harness.backend.observationRevisionOffset = 1L;
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("stale-observation", 0, 8, 8, 1, 12, 12);
        harness.controller.submit(spec);
        TaskSnapshot bound = task(harness.controller.tick(), spec.getId());

        TaskSnapshot retrying = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.QUEUED, retrying.getState());
        assertEquals(bound.getCheckpoint(), retrying.getCheckpoint());
        assertEquals(1, harness.backend.observations);
        assertEquals(0, harness.backend.submissions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void restoredCheckpointRebindsAuthorityWithoutAdvancingItsTarget() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("restore", 0, 8, 8, 1, 12, 12);
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirm();
        TaskSnapshot afterOne = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "1",
            afterOne.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        String frontierBefore = frontierKey(afterOne.getCheckpoint());
        TaskCheckpoint persisted = afterOne.getCheckpoint();
        harness.close();

        harness = new Harness();
        harness.controller.restore(spec, persisted);
        TaskSnapshot rebound = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, rebound.getState());
        assertEquals(
            persisted.getRevision() + 1L,
            rebound.getCheckpoint()
                .getRevision());
        assertEquals(
            "1",
            rebound.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        assertEquals(frontierBefore, frontierKey(rebound.getCheckpoint()));
        assertEquals(0, harness.backend.observations);
        assertEquals(0, harness.backend.submissions);

        harness.controller.tick();
        assertEquals(1, harness.backend.observations);
        assertEquals(1, harness.backend.submissions);
        assertEquals(
            rebound.getCheckpoint()
                .getRevision(),
            harness.backend.lastRequest.getTaskRevision());
    }

    @Test
    public void suspensionCancelsTheActionAndResumeReobservesTheSameFrontier() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("pause", 0, 8, 8, 1, 12, 12);
        harness.controller.submit(spec);
        harness.controller.tick();
        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        String frontierBefore = frontierKey(submitted.getCheckpoint());
        RecordingBackend.Handle first = harness.backend.active;

        harness.controller.pause(spec.getId());
        TaskSnapshot suspended = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.SUSPENDED, suspended.getState());
        assertEquals(ExcavationActionState.CANCELLED, first.state);
        assertEquals(
            "0",
            suspended.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        assertEquals(frontierBefore, frontierKey(suspended.getCheckpoint()));
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());

        harness.controller.resume(spec.getId());
        TaskSnapshot rebound = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, rebound.getState());
        assertEquals(
            "active",
            rebound.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(frontierBefore, frontierKey(rebound.getCheckpoint()));
        assertEquals(1, harness.backend.submissions);

        harness.controller.tick();
        assertEquals(2, harness.backend.observations);
        assertEquals(2, harness.backend.submissions);
        assertEquals(
            first.request.getIntent()
                .getPosition(),
            harness.backend.lastRequest.getIntent()
                .getPosition());
    }

    @Test
    public void missingQuarryMaterialBlocksWithoutRetryAndResumesAfterRefill() {
        harness = new Harness();
        harness.backend.materialAvailable = false;
        TaskSpec spec = ExcavationTask
            .managedQuarryCylinder("missing-ramp", 0, 8, 8, 2, 12, 12, ManagedQuarryConfiguration.defaults());
        harness.controller.submit(spec);
        TaskSnapshot blocked = null;
        for (int tick = 0; tick < 6; tick++) blocked = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            BlockedCause.MISSING_REQUIREMENT,
            blocked.getBlockedReason()
                .get()
                .getCause());
        assertTrue(
            blocked.getDetail()
                .contains("minecraft:cobblestone"));
        assertEquals(0, blocked.getRetryCount());
        assertEquals(0, harness.backend.managedSubmissions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());

        harness.backend.materialAvailable = true;
        harness.controller.resume(spec.getId());
        for (int tick = 0; tick < 6; tick++) harness.controller.tick();
        assertEquals(1, harness.backend.managedSubmissions);
    }

    @Test
    public void managedQuarryFailsClosedWhenInfrastructureBackendIsUnavailable() {
        harness = new Harness();
        harness.backend.managedAvailable = false;
        TaskSpec managed = ExcavationTask
            .managedQuarryCylinder("managed", 0, 8, 8, 2, 12, 12, ManagedQuarryConfiguration.defaults());
        harness.controller.submit(managed);

        TaskSnapshot blocked = task(harness.controller.tick(), managed.getId());

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(0, harness.backend.observations);
        assertEquals(0, harness.backend.managedSubmissions);
    }

    @Test
    public void managedInfrastructureIsConfirmedInOrderBeforeVolumeFrontierAdvances() {
        harness = new Harness();
        TaskSpec managed = ExcavationTask
            .managedQuarryCylinder("managed", 0, 8, 8, 2, 12, 12, ManagedQuarryConfiguration.defaults());
        harness.controller.submit(managed);
        TaskSnapshot bound = task(harness.controller.tick(), managed.getId());
        String initialFrontier = frontierKey(bound.getCheckpoint());

        task(harness.controller.tick(), managed.getId());
        assertEquals(1, harness.backend.managedSubmissions);
        assertEquals(0, harness.backend.submissions);
        assertEquals(
            initialFrontier,
            frontierKey(task(harness.controller.snapshot(), managed.getId()).getCheckpoint()));

        harness.backend.confirmManaged();
        harness.controller.tick();
        harness.controller.tick();
        assertEquals(2, harness.backend.managedSubmissions);
        assertEquals(0, harness.backend.submissions);

        harness.backend.confirmManaged();
        harness.controller.tick();
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
        assertEquals(
            initialFrontier,
            frontierKey(task(harness.controller.snapshot(), managed.getId()).getCheckpoint()));

        harness.backend.confirm();
        TaskSnapshot advanced = task(harness.controller.tick(), managed.getId());
        assertFalse(initialFrontier.equals(frontierKey(advanced.getCheckpoint())));
    }

    @Test
    public void managedFluidUsesApprovedContainmentAndRecordsTheExactOutcome() {
        harness = new Harness();
        harness.backend.classification = ExcavationBlockClassification.FLUID_FLOWING;
        TaskSpec managed = ExcavationTask
            .managedQuarryCylinder("fluid", 0, 8, 8, 2, 12, 12, ManagedQuarryConfiguration.defaults());
        harness.controller.submit(managed);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirmManaged();
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirmManaged();
        harness.controller.tick();
        harness.controller.tick();

        assertEquals(
            io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationIntentKind.CONTAIN_FLUID,
            harness.backend.lastRequest.getIntent()
                .getKind());
        assertTrue(
            harness.backend.lastLease.getCapabilities()
                .contains(ActionCapability.CONTAINER));
        harness.backend.confirm();
        TaskSnapshot contained = task(harness.controller.tick(), managed.getId());
        assertEquals(
            "1",
            contained.getCheckpoint()
                .getValues()
                .get("progress.fluidContained"));
    }

    @Test
    public void unloadingRequirementBlocksAtExactFrontierAndResumeReobservesIt() {
        harness = new Harness();
        harness.backend.suspensionReason = ExcavationSuspensionReason.UNLOADING_REQUIRED;
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("unload", 0, 8, 8, 1, 12, 12);
        harness.controller.submit(spec);
        TaskSnapshot bound = task(harness.controller.tick(), spec.getId());
        String frontierBefore = frontierKey(bound.getCheckpoint());

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            "suspended",
            blocked.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(
            ExcavationSuspensionReason.UNLOADING_REQUIRED.name(),
            blocked.getCheckpoint()
                .getValues()
                .get("suspensionReason"));
        assertEquals(frontierBefore, frontierKey(blocked.getCheckpoint()));
        assertEquals(
            "0",
            blocked.getCheckpoint()
                .getValues()
                .get("progress.completed"));
        assertEquals(0, harness.backend.submissions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());

        harness.backend.suspensionReason = ExcavationSuspensionReason.NONE;
        harness.controller.resume(spec.getId());
        TaskSnapshot rebound = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, rebound.getState());
        assertEquals(
            "active",
            rebound.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(frontierBefore, frontierKey(rebound.getCheckpoint()));
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
    }

    @Test
    public void repairRequirementUsesItsExactPersistedSuspensionReason() {
        harness = new Harness();
        harness.backend.suspensionReason = ExcavationSuspensionReason.REPAIR_REQUIRED;
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder("repair", 0, 8, 8, 1, 12, 12);
        harness.controller.submit(spec);
        harness.controller.tick();

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            ExcavationSuspensionReason.REPAIR_REQUIRED.name(),
            blocked.getCheckpoint()
                .getValues()
                .get("suspensionReason"));
        assertTrue(
            blocked.getDetail()
                .contains("Tinkers repair"));
        assertEquals(0, harness.backend.submissions);
    }

    @Test
    public void configuredServiceRequirementsReachTheObservationBoundaryExactly() {
        harness = new Harness();
        TaskSpec spec = ExcavationTask.cleanVolumeCylinder(
            "services",
            -1,
            8,
            8,
            1,
            12,
            12,
            ExcavationServicePolicy.unloadAndRepair("mining", "ore-chest", "tool-forge", 4, 25));
        harness.controller.submit(spec);
        harness.controller.tick();

        harness.controller.tick();

        ExcavationObservationRequest request = harness.backend.lastObservationRequest;
        assertNotNull(request);
        assertEquals(-1, request.getDimensionId());
        assertTrue(
            request.getServiceRequirements()
                .isUnloadConfigured());
        assertTrue(
            request.getServiceRequirements()
                .isRepairConfigured());
        assertEquals(
            4,
            request.getServiceRequirements()
                .getReservedToolSlot());
        assertEquals(
            25,
            request.getServiceRequirements()
                .getPredictedWorkDamage());
        assertNotNull(harness.backend.lastRequest);
        assertEquals(4, harness.backend.lastRequest.getPreferredToolSlot());
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String taskId) {
        return snapshot.findTask(taskId)
            .orElseThrow(() -> new AssertionError("missing task " + taskId));
    }

    private static String frontierKey(TaskCheckpoint checkpoint) {
        Map<String, String> values = checkpoint.getValues();
        return values.get("frontier.layerY") + ':'
            + values.get("frontier.chunkX")
            + ':'
            + values.get("frontier.chunkZ")
            + ':'
            + values.get("frontier.band")
            + ':'
            + values.get("frontier.offset")
            + ':'
            + values.get("frontier.complete");
    }

    private static final class Harness implements AutoCloseable {

        private final InMemoryActionBroker broker = new InMemoryActionBroker();
        private final RecordingBackend backend = new RecordingBackend();
        private final Access access = new Access(backend);
        private long nowMillis;
        private final TaskOrchestrator controller = new TaskOrchestrator(
            () -> nowMillis,
            new RuntimeTaskRunnerFactory(UnusedNavigationAccess.INSTANCE, access),
            broker);

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class Access implements ExcavationRuntimeAccess {

        private ExcavationBackend backend;
        private boolean dryRun;

        private Access(ExcavationBackend backend) {
            this.backend = backend;
        }

        @Override
        public ExcavationBackend getExcavationBackend() {
            return backend;
        }

        @Override
        public boolean isDryRun() {
            return dryRun;
        }
    }

    private static final class RecordingBackend implements ExcavationBackend {

        private boolean collectDrops, collectionDone, collectionClosed;
        private ActionLease collectionLease;

        @Override
        public boolean supportsDropCollection() {
            return collectDrops;
        }

        @Override
        public DropCollection collectDrops(String taskId, CylinderExcavationSpec area, ActionLease lease) {
            collectionLease = lease;
            return new DropCollection() {

                public boolean poll() {
                    return collectionDone;
                }

                public String detail() {
                    return "Collecting mined items";
                }

                public void close() {
                    collectionClosed = true;
                }
            };
        }

        private boolean available = true;
        private boolean managedAvailable = true;
        private boolean materialAvailable = true;
        private int observations;
        private int submissions;
        private int managedObservations;
        private int managedSubmissions;
        private long observationRevisionOffset;
        private long confirmationEpochOffset;
        private ExcavationSuspensionReason suspensionReason = ExcavationSuspensionReason.NONE;
        private ExcavationBlockClassification classification = ExcavationBlockClassification.BREAKABLE;
        private int airObservationsBeforeBreakable = -1;
        private ExcavationObservationRequest lastObservationRequest;
        private ExcavationActionRequest lastRequest;
        private ActionLease lastLease;
        private Handle active;
        private ManagedHandle managedActive;
        private final Set<io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition> confirmedClear = new HashSet<>();
        private final Set<io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition> confirmedManaged = new HashSet<>();

        @Override
        public ExcavationBackendAvailability availability() {
            return available ? ExcavationBackendAvailability.available("recording excavation backend ready")
                : ExcavationBackendAvailability.unavailable("recording excavation backend disabled");
        }

        @Override
        public ExcavationBackendAvailability managedQuarryAvailability() {
            return managedAvailable ? ExcavationBackendAvailability.available("recording managed backend ready")
                : ExcavationBackendAvailability.unavailable("recording managed backend disabled");
        }

        @Override
        public ManagedQuarryObservationResult observeManagedQuarry(ManagedQuarryObservationRequest request) {
            managedObservations++;
            boolean present = confirmedManaged.contains(
                request.getIntent()
                    .getPosition());
            return new ManagedQuarryObservationResult(
                request.getTaskRevision(),
                request.getActionEpoch(),
                request.getGeometryKey(),
                request.getStartFrontier(),
                request.getIntent()
                    .getPosition(),
                present ? request.getIntent()
                    .getApprovedMaterial() + "@0" : "minecraft:air@0",
                present,
                materialAvailable);
        }

        @Override
        public ManagedQuarryActionHandle executeManagedQuarry(ManagedQuarryActionRequest request,
            ActionLease actionLease) {
            assertTrue(actionLease.isValid());
            assertEquals(request.getActionEpoch(), actionLease.getEpoch());
            assertTrue(
                actionLease.getCapabilities()
                    .contains(ActionCapability.CONTAINER));
            managedSubmissions++;
            managedActive = new ManagedHandle(request);
            return managedActive;
        }

        @Override
        public ExcavationObservationResult observe(ExcavationObservationRequest request) {
            int observationIndex = observations++;
            lastObservationRequest = request;
            ExcavationBlockClassification observedClassification = confirmedClear.contains(request.getPosition())
                ? ExcavationBlockClassification.AIR
                : airObservationsBeforeBreakable >= 0 && observationIndex < airObservationsBeforeBreakable
                    ? ExcavationBlockClassification.AIR
                    : classification;
            ExcavationObservation observation = new ExcavationObservation(
                request.getPosition(),
                observedClassification,
                observedClassification == ExcavationBlockClassification.AIR ? "minecraft:air@0" : "stone-fingerprint");
            return new ExcavationObservationResult(
                request.getTaskRevision() + observationRevisionOffset,
                request.getActionEpoch(),
                request.getGeometryKey(),
                request.getStartFrontier(),
                observation,
                suspensionReason);
        }

        @Override
        public ExcavationActionHandle execute(ExcavationActionRequest request, ActionLease actionLease) {
            assertTrue(actionLease.isValid());
            assertEquals(request.getActionEpoch(), actionLease.getEpoch());
            submissions++;
            lastRequest = request;
            lastLease = actionLease;
            active = new Handle(request);
            return active;
        }

        private void confirm() {
            assertNotNull(active);
            active.confirmation = new ConfirmedExcavationTargetResult(
                active.request.getTaskRevision(),
                active.request.getActionEpoch() + confirmationEpochOffset,
                active.request.getGeometryKey(),
                active.request.getStartFrontier(),
                active.request.getIntent()
                    .getObservedFingerprint(),
                new ExcavationTargetResult(
                    active.request.getIntent()
                        .getPosition(),
                    active.request.getIntent()
                        .getKind()
                        == io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationIntentKind.CONTAIN_FLUID
                            ? ExcavationTargetOutcome.FLUID_CONTAINED
                            : ExcavationTargetOutcome.COMPLETED));
            active.state = ExcavationActionState.CONFIRMED;
            active.detail = "server-confirmed post-action observation";
            confirmedClear.add(
                active.request.getIntent()
                    .getPosition());
        }

        private void confirmManaged() {
            assertNotNull(managedActive);
            managedActive.confirmation = new ConfirmedManagedQuarryResult(
                managedActive.request.getTaskRevision(),
                managedActive.request.getActionEpoch(),
                managedActive.request.getGeometryKey(),
                managedActive.request.getStartFrontier(),
                managedActive.request.getIntent(),
                managedActive.request.getIntent()
                    .getApprovedMaterial() + "@0",
                managedActive.request.getIntent()
                    .getApprovedMaterial());
            managedActive.state = ExcavationActionState.CONFIRMED;
            managedActive.detail = "server-confirmed managed material";
            confirmedManaged.add(
                managedActive.request.getIntent()
                    .getPosition());
        }

        private static final class Handle implements ExcavationActionHandle {

            private final ExcavationActionRequest request;
            private ExcavationActionState state = ExcavationActionState.SUBMITTED;
            private String detail = "submitted";
            private ConfirmedExcavationTargetResult confirmation;

            private Handle(ExcavationActionRequest request) {
                this.request = request;
            }

            @Override
            public String getRequestId() {
                return request.getRequestId();
            }

            @Override
            public ExcavationActionProgress progress() {
                return new ExcavationActionProgress(request.getRequestId(), state, detail, confirmation);
            }

            @Override
            public void cancel() {
                if (state != ExcavationActionState.FAILED) {
                    state = ExcavationActionState.CANCELLED;
                    confirmation = null;
                    detail = "cancelled";
                }
            }
        }

        private static final class ManagedHandle implements ManagedQuarryActionHandle {

            private final ManagedQuarryActionRequest request;
            private ExcavationActionState state = ExcavationActionState.SUBMITTED;
            private String detail = "submitted";
            private ConfirmedManagedQuarryResult confirmation;

            private ManagedHandle(ManagedQuarryActionRequest request) {
                this.request = request;
            }

            @Override
            public String getRequestId() {
                return request.getRequestId();
            }

            @Override
            public ManagedQuarryActionProgress progress() {
                return new ManagedQuarryActionProgress(request.getRequestId(), state, detail, confirmation);
            }

            @Override
            public void cancel() {
                state = ExcavationActionState.CANCELLED;
                confirmation = null;
                detail = "cancelled";
            }
        }
    }

    private enum UnusedNavigationAccess implements NavigationRuntimeAccess {

        INSTANCE;

        @Override
        public NavigationBackend getNavigationBackend() {
            return null;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }

        @Override
        public void publishNavigationProgress(NavigationProgress progress) {}
    }

    private static final class FixedClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
