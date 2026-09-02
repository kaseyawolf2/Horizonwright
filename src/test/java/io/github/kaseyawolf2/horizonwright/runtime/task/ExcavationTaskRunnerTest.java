package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationMode;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetOutcome;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetResult;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedCause;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class ExcavationTaskRunnerTest {

    private Harness harness;

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

        TaskSnapshot waiting = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, waiting.getState());
        assertEquals(
            1L,
            waiting.getCheckpoint()
                .getRevision());
        assertEquals(1, harness.backend.observations);
        assertEquals(1, harness.backend.submissions);

        harness.backend.confirm();
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
    public void managedQuarrySpecificationsAreRejectedByThisBridge() {
        TaskSpec clean = ExcavationTask.cleanVolumeCylinder("managed", 0, 8, 8, 1, 12, 12);
        Map<String, String> managedParameters = new LinkedHashMap<>(clean.getParameters());
        managedParameters.put(ExcavationTask.MODE, ExcavationMode.MANAGED_QUARRY.name());
        TaskSpec managed = new TaskSpec(
            clean.getId(),
            clean.getType(),
            clean.getDisplayName(),
            TaskLane.FALLBACK,
            managedParameters);

        assertThrows(
            IllegalArgumentException.class,
            () -> new RuntimeTaskRunnerFactory(UnusedNavigationAccess.INSTANCE, new Access(new RecordingBackend()))
                .create(managed, TaskCheckpoint.empty()));
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
        private final TaskOrchestrator controller = new TaskOrchestrator(
            new FixedClock(),
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

        private boolean available = true;
        private int observations;
        private int submissions;
        private long observationRevisionOffset;
        private long confirmationEpochOffset;
        private ExcavationSuspensionReason suspensionReason = ExcavationSuspensionReason.NONE;
        private ExcavationObservationRequest lastObservationRequest;
        private ExcavationActionRequest lastRequest;
        private ActionLease lastLease;
        private Handle active;

        @Override
        public ExcavationBackendAvailability availability() {
            return available ? ExcavationBackendAvailability.available("recording excavation backend ready")
                : ExcavationBackendAvailability.unavailable("recording excavation backend disabled");
        }

        @Override
        public ExcavationObservationResult observe(ExcavationObservationRequest request) {
            observations++;
            lastObservationRequest = request;
            ExcavationObservation observation = new ExcavationObservation(
                request.getPosition(),
                ExcavationBlockClassification.BREAKABLE,
                "stone-fingerprint");
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
                    ExcavationTargetOutcome.COMPLETED));
            active.state = ExcavationActionState.CONFIRMED;
            active.detail = "server-confirmed post-action observation";
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
