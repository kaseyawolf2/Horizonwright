package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class RepairTaskRunnerTest {

    private static final int RESERVED_INVENTORY_SLOT = 2;
    private static final ItemFingerprint TOOL = new ItemFingerprint("tconstruct:pickaxe", 0, "damaged", 1);
    private static final ItemFingerprint MATERIAL = new ItemFingerprint("tconstruct:material", 0, "material", 1);
    private Harness harness;

    @After
    public void closeHarness() {
        if (harness != null) harness.close();
    }

    @Test
    public void persistsPlanBeforeClickAndCompletesOnlyAfterVerifiedRepairEvidence() {
        harness = new Harness();
        TaskSpec spec = taskSpec("repair-one");
        harness.controller.submit(spec);

        TaskSnapshot prepared = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "PREPARED",
            prepared.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(0, harness.backend.submissions);

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "AWAITING_CONFIRMATION",
            submitted.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(1, harness.backend.submissions);
        assertTrue(
            harness.backend.lastLease.getCapabilities()
                .contains(ActionCapability.CONTAINER));

        harness.backend.confirm(ConfirmationMode.VALID);
        TaskSnapshot verified = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "READY",
            verified.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(
            "1",
            verified.getCheckpoint()
                .getValues()
                .get("completedRepairs"));
        assertEquals(TaskState.COMPLETED, task(harness.controller.tick(), spec.getId()).getState());
    }

    @Test
    public void restartFromAwaitingReconcilesRepairedToolWithoutReplay() {
        Harness original = new Harness();
        TaskSpec spec = taskSpec("repair-restart");
        original.controller.submit(spec);
        original.controller.tick();
        TaskCheckpoint awaiting = task(original.controller.tick(), spec.getId()).getCheckpoint();
        original.backend.confirm(ConfirmationMode.VALID);
        original.close();

        harness = new Harness();
        harness.backend.repaired = true;
        harness.controller.restore(spec, awaiting);
        TaskSnapshot reconciled = task(harness.controller.tick(), spec.getId());

        assertEquals(
            "READY",
            reconciled.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(0, harness.backend.submissions);
        assertEquals(TaskState.COMPLETED, task(harness.controller.tick(), spec.getId()).getState());
        assertEquals(0, harness.backend.submissions);
    }

    @Test
    public void changedPredictionAfterPreparationIsDiscardedWithoutClicking() {
        harness = new Harness();
        TaskSpec spec = taskSpec("repair-changed");
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.backend.changedPrediction = true;

        TaskSnapshot reconciled = task(harness.controller.tick(), spec.getId());

        assertEquals(
            "READY",
            reconciled.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(0, harness.backend.submissions);
    }

    @Test
    public void waitsLiveForOperatorToOpenStationThenContinuesWithoutManualResume() {
        harness = new Harness();
        harness.backend.stationOpen = false;
        TaskSpec spec = taskSpec("repair-wait-for-station");
        harness.controller.submit(spec);

        TaskSnapshot waiting = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, waiting.getState());
        assertEquals(0, harness.backend.submissions);

        harness.backend.stationOpen = true;
        TaskSnapshot prepared = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, prepared.getState());
        assertEquals(
            "PREPARED",
            prepared.getCheckpoint()
                .getValues()
                .get("phase"));

        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
    }

    @Test
    public void automaticallyApproachesAndOpensStationBeforeRepairing() {
        harness = new Harness();
        harness.backend.stationOpen = false;
        harness.backend.automatedStationAccess = true;
        TaskSpec spec = taskSpec("repair-automatic-station");
        harness.controller.submit(spec);

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, submitted.getState());
        assertEquals(1, harness.backend.stationAccessSubmissions);
        assertTrue(
            harness.backend.lastStationLease.getCapabilities()
                .contains(ActionCapability.MOVEMENT));
        assertTrue(
            harness.backend.lastStationLease.getCapabilities()
                .contains(ActionCapability.LOOK));
        assertTrue(
            harness.backend.lastStationLease.getCapabilities()
                .contains(ActionCapability.USE));

        assertEquals(TaskState.RUNNING, task(harness.controller.tick(), spec.getId()).getState());
        harness.backend.confirmStationAccess();
        assertEquals(TaskState.RUNNING, task(harness.controller.tick(), spec.getId()).getState());

        TaskSnapshot prepared = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "PREPARED",
            prepared.getCheckpoint()
                .getValues()
                .get("phase"));
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
    }

    @Test
    public void stagesInputsUnderContainerAuthorityBeforePreparingRepair() {
        harness = new Harness();
        harness.backend.automatedInputStaging = true;
        TaskSpec spec = taskSpec("repair-stage-inputs");
        harness.controller.submit(spec);

        TaskSnapshot staging = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, staging.getState());
        assertEquals(1, harness.backend.inputStagingSubmissions);
        assertTrue(
            harness.backend.lastInputStagingLease.getCapabilities()
                .contains(ActionCapability.CONTAINER));

        assertEquals(TaskState.RUNNING, task(harness.controller.tick(), spec.getId()).getState());
        harness.backend.confirmInputStaging();
        assertEquals(TaskState.RUNNING, task(harness.controller.tick(), spec.getId()).getState());

        TaskSnapshot prepared = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "PREPARED",
            prepared.getCheckpoint()
                .getValues()
                .get("phase"));
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
    }

    @Test
    public void exactRepairPreviewWinsWhenStationMutatesItsInputDamage() {
        harness = new Harness();
        harness.backend.previewMutatedInput = true;
        TaskSpec spec = taskSpec("repair-mutated-preview-input");
        harness.controller.submit(spec);

        TaskSnapshot prepared = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, prepared.getState());
        assertEquals(
            "PREPARED",
            prepared.getCheckpoint()
                .getValues()
                .get("phase"));

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, submitted.getState());
        assertEquals(
            "AWAITING_CONFIRMATION",
            submitted.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(1, harness.backend.submissions);
    }

    @Test
    public void revalidationReusesPlanningRevisionForRevisionBoundTransactionIds() {
        harness = new Harness();
        harness.backend.revisionBoundTransactionId = true;
        TaskSpec spec = taskSpec("repair-revision-bound-transaction");
        harness.controller.submit(spec);

        TaskSnapshot prepared = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "PREPARED",
            prepared.getCheckpoint()
                .getValues()
                .get("phase"));

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "AWAITING_CONFIRMATION",
            submitted.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(1, harness.backend.submissions);
    }

    @Test
    public void rejectionAndMissingMaterialNeverReachAutomaticReplay() {
        harness = new Harness();
        TaskSpec rejected = taskSpec("repair-rejected");
        harness.controller.submit(rejected);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.reject();
        assertEquals(TaskState.BLOCKED, task(harness.controller.tick(), rejected.getId()).getState());
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);

        harness.close();
        harness = new Harness();
        harness.backend.noMaterial = true;
        TaskSpec missing = taskSpec("repair-material");
        harness.controller.submit(missing);
        assertEquals(TaskState.BLOCKED, task(harness.controller.tick(), missing.getId()).getState());
        assertEquals(0, harness.backend.submissions);
    }

    @Test
    public void changedIdentityAndReservedReturnSlotAreRejectedAfterServerConfirmation() {
        assertConfirmationBlocked(ConfirmationMode.CHANGED_IDENTITY);
        assertConfirmationBlocked(ConfirmationMode.CHANGED_RESERVED_SLOT);
        assertConfirmationBlocked(ConfirmationMode.NO_MATERIAL_CONSUMED);
        assertConfirmationBlocked(ConfirmationMode.WRONG_FINGERPRINT);
    }

    @Test
    public void transactionTouchingUnapprovedSlotIsRejectedBeforeAuthority() {
        harness = new Harness();
        harness.backend.unapprovedClick = true;
        TaskSpec spec = taskSpec("repair-unapproved-slot");
        harness.controller.submit(spec);

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(0, harness.backend.submissions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    private void assertConfirmationBlocked(ConfirmationMode mode) {
        if (harness != null) harness.close();
        harness = new Harness();
        TaskSpec spec = taskSpec(
            "repair-invalid-" + mode.name()
                .toLowerCase());
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirm(mode);
        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(1, harness.backend.submissions);
    }

    private static TaskSpec taskSpec(String id) {
        return RepairTask.create(id, "tool-forge", RESERVED_INVENTORY_SLOT, 100);
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String id) {
        return snapshot.findTask(id)
            .orElseThrow(AssertionError::new);
    }

    private enum ConfirmationMode {
        VALID,
        CHANGED_IDENTITY,
        CHANGED_RESERVED_SLOT,
        NO_MATERIAL_CONSUMED,
        WRONG_FINGERPRINT
    }

    private static final class Harness implements AutoCloseable {

        private final InMemoryActionBroker broker = new InMemoryActionBroker();
        private final Backend backend = new Backend();
        private final TaskOrchestrator controller = new TaskOrchestrator(
            new FixedClock(),
            new RuntimeTaskRunnerFactory(
                UnusedNavigation.INSTANCE,
                DisabledExcavation.INSTANCE,
                DisabledUnload.INSTANCE,
                new Access(backend)),
            broker);

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class Access implements RepairRuntimeAccess {

        private final Backend backend;

        private Access(Backend backend) {
            this.backend = backend;
        }

        @Override
        public RepairBackend getRepairBackend() {
            return backend;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private static final class Backend implements RepairBackend {

        private boolean repaired;
        private boolean changedPrediction;
        private boolean noMaterial;
        private boolean unapprovedClick;
        private boolean stationOpen = true;
        private boolean automatedStationAccess;
        private boolean automatedInputStaging;
        private boolean previewMutatedInput;
        private boolean revisionBoundTransactionId;
        private int submissions;
        private int stationAccessSubmissions;
        private int inputStagingSubmissions;
        private ActionLease lastLease;
        private ActionLease lastStationLease;
        private ActionLease lastInputStagingLease;
        private Handle active;
        private StationHandle stationAccess;
        private FakeInputStagingHandle inputStaging;

        @Override
        public RepairBackendAvailability availability() {
            return stationOpen ? RepairBackendAvailability.available("pinned test forge open")
                : RepairBackendAvailability.waitingForOperator("waiting for pinned test forge");
        }

        @Override
        public StationAccessHandle accessStation(StationAccessRequest request, ActionLease lease) {
            if (!automatedStationAccess) return null;
            stationAccessSubmissions++;
            lastStationLease = lease;
            stationAccess = new StationHandle(request.getRequestId());
            return stationAccess;
        }

        @Override
        public RepairBackend.InputStagingHandle stageInputs(RepairObservationRequest request, ActionLease lease) {
            if (!automatedInputStaging || inputStaging != null && inputStaging.state == InputStagingState.CONFIRMED)
                return null;
            inputStagingSubmissions++;
            lastInputStagingLease = lease;
            inputStaging = new FakeInputStagingHandle(
                request.getTaskId() + "-input-staging-r" + request.getCheckpointRevision());
            return inputStaging;
        }

        @Override
        public RepairObservationResult observe(RepairObservationRequest request) {
            RepairToolSnapshot input = repaired ? repairedTool()
                : previewMutatedInput ? new RepairToolSnapshot("pick-stable", 1, 1000, RESERVED_INVENTORY_SLOT)
                    : damagedTool();
            if (repaired) return observation(request, input, null, 0, null);
            RepairToolSnapshot predicted = new RepairToolSnapshot(
                "pick-stable",
                changedPrediction ? 450 : previewMutatedInput ? 0 : 400,
                1000,
                RESERVED_INVENTORY_SLOT);
            ContainerSnapshot before = snapshot(10L, TOOL, MATERIAL, null, null, null, null);
            ContainerSnapshot after = snapshot(
                11L,
                new ItemFingerprint("tconstruct:pickaxe", 0, changedPrediction ? "repair-450" : "repair-400", 1),
                null,
                null,
                null,
                null,
                null);
            ContainerTransaction transaction = new ContainerTransaction(
                request.getTaskId() + "-transaction"
                    + (revisionBoundTransactionId ? "-r" + request.getCheckpointRevision() : ""),
                request.getActionEpoch(),
                Collections.singletonList(
                    new VerifiedContainerClick("consume-material", unapprovedClick ? 6 : 1, 0, 0, before, after)));
            return observation(request, input, predicted, noMaterial ? 0 : 1, transaction);
        }

        private RepairObservationResult observation(RepairObservationRequest request, RepairToolSnapshot input,
            RepairToolSnapshot output, int consumed, ContainerTransaction transaction) {
            return new RepairObservationResult(
                request.getTaskId(),
                request.getCheckpointRevision(),
                request.getActionEpoch(),
                request.getStationId(),
                7,
                4,
                4,
                Collections.singletonList(5),
                true,
                input,
                output,
                consumed,
                transaction);
        }

        @Override
        public RepairActionHandle execute(RepairActionRequest request, ActionLease lease) {
            submissions++;
            lastLease = lease;
            VerifiedContainerClick click = request.getTransaction()
                .getClicks()
                .get(0);
            assertTrue(
                request.getTransaction()
                    .nextClick(click.getExpectedBefore(), request.getActionEpoch())
                    .isPresent());
            active = new Handle(request);
            return active;
        }

        private void confirm(ConfirmationMode mode) {
            VerifiedContainerClick click = active.request.getTransaction()
                .getClicks()
                .get(0);
            assertTrue(
                active.request.getTransaction()
                    .confirm(click.getClickId(), true, click.getExpectedAfter(), active.request.getActionEpoch()));
            RepairToolSnapshot output = repairedTool();
            int consumed = 1;
            if (mode == ConfirmationMode.CHANGED_IDENTITY)
                output = new RepairToolSnapshot("other-tool", 400, 1000, RESERVED_INVENTORY_SLOT);
            if (mode == ConfirmationMode.CHANGED_RESERVED_SLOT)
                output = new RepairToolSnapshot("pick-stable", 400, 1000, RESERVED_INVENTORY_SLOT + 1);
            if (mode == ConfirmationMode.NO_MATERIAL_CONSUMED) consumed = 0;
            String fingerprint = mode == ConfirmationMode.WRONG_FINGERPRINT ? "wrong-fingerprint"
                : active.request.getTransactionFingerprint();
            active.confirmation = new RepairActionConfirmation(fingerprint, output, consumed, true);
            active.state = RepairActionState.CONFIRMED;
            if (mode == ConfirmationMode.VALID) repaired = true;
        }

        private void reject() {
            VerifiedContainerClick click = active.request.getTransaction()
                .getClicks()
                .get(0);
            active.request.getTransaction()
                .confirm(click.getClickId(), false, click.getExpectedBefore(), active.request.getActionEpoch());
            active.state = RepairActionState.REJECTED;
        }

        private void confirmStationAccess() {
            stationOpen = true;
            stationAccess.state = StationAccessState.CONFIRMED;
        }

        private void confirmInputStaging() {
            inputStaging.state = InputStagingState.CONFIRMED;
        }
    }

    private static final class FakeInputStagingHandle implements RepairBackend.InputStagingHandle {

        private final String requestId;
        private RepairBackend.InputStagingState state = RepairBackend.InputStagingState.EXECUTING;

        private FakeInputStagingHandle(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public String getRequestId() {
            return requestId;
        }

        @Override
        public RepairBackend.InputStagingProgress progress() {
            return new RepairBackend.InputStagingProgress(requestId, state, state.name());
        }

        @Override
        public void cancel() {
            state = RepairBackend.InputStagingState.CANCELLED;
        }
    }

    private static final class StationHandle implements RepairBackend.StationAccessHandle {

        private final String requestId;
        private RepairBackend.StationAccessState state = RepairBackend.StationAccessState.APPROACHING;

        private StationHandle(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public String getRequestId() {
            return requestId;
        }

        @Override
        public RepairBackend.StationAccessProgress progress() {
            return new RepairBackend.StationAccessProgress(requestId, state, state.name());
        }

        @Override
        public void cancel() {
            state = RepairBackend.StationAccessState.CANCELLED;
        }
    }

    private static final class Handle implements RepairActionHandle {

        private final RepairActionRequest request;
        private RepairActionState state = RepairActionState.SUBMITTED;
        private RepairActionConfirmation confirmation;

        private Handle(RepairActionRequest request) {
            this.request = request;
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public RepairActionProgress progress() {
            return new RepairActionProgress(request.getRequestId(), state, state.name(), confirmation);
        }

        @Override
        public void cancel() {
            if (state != RepairActionState.CONFIRMED) state = RepairActionState.FAILED;
        }
    }

    private static RepairToolSnapshot damagedTool() {
        return new RepairToolSnapshot("pick-stable", 900, 1000, RESERVED_INVENTORY_SLOT);
    }

    private static RepairToolSnapshot repairedTool() {
        return new RepairToolSnapshot("pick-stable", 400, 1000, RESERVED_INVENTORY_SLOT);
    }

    private static ContainerSnapshot snapshot(long revision, ItemFingerprint... slots) {
        return new ContainerSnapshot(
            7,
            "tconstruct.tools.inventory.ToolStationContainer",
            "layout",
            revision,
            Arrays.asList(slots),
            null);
    }

    private enum UnusedNavigation implements NavigationRuntimeAccess {

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

    private enum DisabledExcavation implements ExcavationRuntimeAccess {

        INSTANCE;

        @Override
        public ExcavationBackend getExcavationBackend() {
            return null;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private enum DisabledUnload implements UnloadRuntimeAccess {

        INSTANCE;

        @Override
        public UnloadBackend getUnloadBackend() {
            return null;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private static final class FixedClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
