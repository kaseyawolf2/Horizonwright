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
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadClickPrediction;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class UnloadTaskRunnerTest {

    private static final ItemFingerprint PICK = new ItemFingerprint("tconstruct:pickaxe", 0, "tool", 1);
    private static final ItemFingerprint ORE = new ItemFingerprint("gregtech:ore", 4, "ore", 16);
    private Harness harness;

    @After
    public void closeHarness() {
        if (harness != null) harness.close();
    }

    @Test
    public void persistsExactPlanBeforeExecutingAndCompletesOnlyAfterConfirmation() {
        harness = new Harness();
        TaskSpec spec = UnloadTask.create("unload-one", "mining", "ore-chest");
        harness.controller.submit(spec);

        TaskSnapshot prepared = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, prepared.getState());
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

        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
        harness.backend.confirm();
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
                .get("completedTransactions"));

        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(1, harness.backend.submissions);
    }

    @Test
    public void restartFromAwaitingReconcilesServerTruthWithoutReplaying() {
        Harness original = new Harness();
        TaskSpec spec = UnloadTask.create("restart", "mining", "ore-chest");
        original.controller.submit(spec);
        original.controller.tick();
        TaskSnapshot awaiting = task(original.controller.tick(), spec.getId());
        assertEquals(1, original.backend.submissions);
        TaskCheckpoint persisted = awaiting.getCheckpoint();
        original.backend.applyServerResultWithoutDeliveringConfirmation();
        original.close();

        harness = new Harness();
        harness.backend.unloaded = true;
        harness.controller.restore(spec, persisted);
        TaskSnapshot reconciled = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, reconciled.getState());
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
    public void rejectionBlocksUntilOperatorAcknowledgesAndDoesNotAutomaticallyResend() {
        harness = new Harness();
        TaskSpec spec = UnloadTask.create("rejected", "mining", "ore-chest");
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.reject();

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(1, harness.backend.submissions);
        harness.controller.tick();
        harness.controller.tick();
        assertEquals(1, harness.backend.submissions);
    }

    @Test
    public void changedContainerAfterPreparationDiscardsPlanWithoutClicking() {
        harness = new Harness();
        TaskSpec spec = UnloadTask.create("changed", "mining", "ore-chest");
        harness.controller.submit(spec);
        TaskSnapshot prepared = task(harness.controller.tick(), spec.getId());
        String preparedFingerprint = prepared.getCheckpoint()
            .getValues()
            .get("transactionFingerprint");
        harness.backend.changedOreCount = true;

        TaskSnapshot reconciled = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, reconciled.getState());
        assertEquals(
            "READY",
            reconciled.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(0, harness.backend.submissions);
        assertTrue(!preparedFingerprint.isEmpty());
    }

    @Test
    public void incompleteLoadoutBlocksBeforeAnyContainerLeaseOrClick() {
        harness = new Harness();
        harness.backend.missingPick = true;
        TaskSpec spec = UnloadTask.create("missing", "mining", "ore-chest");
        harness.controller.submit(spec);

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(0, harness.backend.submissions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String taskId) {
        return snapshot.findTask(taskId)
            .orElseThrow(AssertionError::new);
    }

    private static final class Harness implements AutoCloseable {

        private final InMemoryActionBroker broker = new InMemoryActionBroker();
        private final Backend backend = new Backend();
        private final TaskOrchestrator controller = new TaskOrchestrator(
            new FixedClock(),
            new RuntimeTaskRunnerFactory(UnusedNavigation.INSTANCE, DisabledExcavation.INSTANCE, new Access(backend)),
            broker);

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class Access implements UnloadRuntimeAccess {

        private final Backend backend;

        private Access(Backend backend) {
            this.backend = backend;
        }

        @Override
        public UnloadBackend getUnloadBackend() {
            return backend;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private static final class Backend implements UnloadBackend {

        private final NamedLoadout loadout = new NamedLoadout(
            "mining",
            "Mining",
            Collections.singletonList(
                new LoadoutReservation(
                    "pick",
                    LoadoutRole.TOOL,
                    PICK.getItemId(),
                    PICK.getMetadata(),
                    PICK.getDataHash(),
                    1)));
        private boolean missingPick;
        private boolean unloaded;
        private boolean changedOreCount;
        private int submissions;
        private ActionLease lastLease;
        private Handle active;

        @Override
        public UnloadBackendAvailability availability() {
            return UnloadBackendAvailability.available("test chest open");
        }

        @Override
        public UnloadObservationResult observe(UnloadObservationRequest request) {
            if (missingPick) {
                return new UnloadObservationResult(
                    request.getTaskId(),
                    request.getCheckpointRevision(),
                    request.getActionEpoch(),
                    request.getStorageId(),
                    loadout,
                    Arrays.asList(null, ORE),
                    StorageItemFilter.acceptAll(),
                    Collections.<UnloadClickPrediction>emptyList());
            }
            if (unloaded) {
                return new UnloadObservationResult(
                    request.getTaskId(),
                    request.getCheckpointRevision(),
                    request.getActionEpoch(),
                    request.getStorageId(),
                    loadout,
                    Arrays.asList(PICK, null),
                    StorageItemFilter.acceptAll(),
                    Collections.<UnloadClickPrediction>emptyList());
            }
            ItemFingerprint observedOre = changedOreCount
                ? new ItemFingerprint(ORE.getItemId(), ORE.getMetadata(), ORE.getDataHash(), 15)
                : ORE;
            ContainerSnapshot before = snapshot(10L, null, observedOre);
            ContainerSnapshot after = snapshot(11L, observedOre, null);
            VerifiedContainerClick click = new VerifiedContainerClick("move-ore", 1, 0, 1, before, after);
            return new UnloadObservationResult(
                request.getTaskId(),
                request.getCheckpointRevision(),
                request.getActionEpoch(),
                request.getStorageId(),
                loadout,
                Arrays.asList(PICK, observedOre),
                StorageItemFilter.acceptAll(),
                Collections.singletonList(new UnloadClickPrediction(1, click)));
        }

        @Override
        public UnloadActionHandle execute(UnloadActionRequest request, ActionLease lease) {
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

        private void confirm() {
            applyServerResultWithoutDeliveringConfirmation();
            active.state = UnloadActionState.CONFIRMED;
        }

        private void applyServerResultWithoutDeliveringConfirmation() {
            VerifiedContainerClick click = active.request.getTransaction()
                .getClicks()
                .get(0);
            assertTrue(
                active.request.getTransaction()
                    .confirm(click.getClickId(), true, click.getExpectedAfter(), active.request.getActionEpoch()));
            unloaded = true;
        }

        private void reject() {
            VerifiedContainerClick click = active.request.getTransaction()
                .getClicks()
                .get(0);
            active.request.getTransaction()
                .confirm(click.getClickId(), false, click.getExpectedBefore(), active.request.getActionEpoch());
            active.state = UnloadActionState.REJECTED;
        }
    }

    private static final class Handle implements UnloadActionHandle {

        private final UnloadActionRequest request;
        private UnloadActionState state = UnloadActionState.SUBMITTED;

        private Handle(UnloadActionRequest request) {
            this.request = request;
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public UnloadActionProgress progress() {
            return new UnloadActionProgress(request.getRequestId(), state, state.name());
        }

        @Override
        public void cancel() {
            if (state != UnloadActionState.CONFIRMED) state = UnloadActionState.FAILED;
        }
    }

    private static ContainerSnapshot snapshot(long revision, ItemFingerprint... slots) {
        return new ContainerSnapshot(7, "chest", "layout", revision, Arrays.asList(slots), null);
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

    private static final class FixedClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
