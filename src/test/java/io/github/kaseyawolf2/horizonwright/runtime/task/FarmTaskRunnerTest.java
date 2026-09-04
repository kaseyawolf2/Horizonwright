package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;
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
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend.ActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend.ActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend.ActionRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend.ActionState;

public class FarmTaskRunnerTest {

    private Harness harness;

    @After
    public void closeHarness() {
        if (harness != null) harness.close();
    }

    @Test
    public void disabledDefaultBlocksBeforeFreezingAnyPlotEvidence() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        TaskOrchestrator controller = new TaskOrchestrator(
            new FixedClock(),
            new RuntimeTaskRunnerFactory(UnusedNavigationAccess.INSTANCE),
            broker);
        try {
            TaskSpec spec = FarmTask.finitePass("farm", "north-field", 2);
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
    public void immatureCropCompletesWithoutAcquiringGameplayAuthority() {
        harness = new Harness(crop("wheat-3", false));
        TaskSpec spec = FarmTask.finitePass("farm", "north-field", 2);
        harness.controller.submit(spec);

        TaskSnapshot frozen = task(harness.controller.tick(), spec.getId());
        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, frozen.getState());
        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(
            "1",
            completed.getCheckpoint()
                .getValues()
                .get("nextIndex"));
        assertEquals(
            "0",
            completed.getCheckpoint()
                .getValues()
                .get("verifiedMutations"));
        assertEquals(0, harness.backend.actions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void matureCropAdvancesOnlyAfterVerifiedImmaturePostcondition() {
        harness = new Harness(crop("wheat-7", true));
        TaskSpec spec = FarmTask.finitePass("farm", "north-field", 2);
        harness.controller.submit(spec);
        harness.controller.tick();

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        TaskSnapshot waiting = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.RUNNING, submitted.getState());
        assertEquals(submitted.getCheckpoint(), waiting.getCheckpoint());
        assertEquals(
            "0",
            submitted.getCheckpoint()
                .getValues()
                .get("nextIndex"));
        assertEquals(1, harness.backend.actions);
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.USE));
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.PLACE));
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.HELD_USE));
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.CONTAINER));
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.DIG));

        harness.backend.confirm(crop("wheat-0", false));
        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(
            "1",
            completed.getCheckpoint()
                .getValues()
                .get("nextIndex"));
        assertEquals(
            "1",
            completed.getCheckpoint()
                .getValues()
                .get("verifiedMutations"));
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void changedFrozenTargetFailsBeforeLeaseOrAction() {
        harness = new Harness(crop("wheat-7", true));
        TaskSpec spec = FarmTask.finitePass("farm", "north-field", 2);
        harness.controller.submit(spec);
        TaskSnapshot frozen = task(harness.controller.tick(), spec.getId());
        harness.backend.current = crop("changed-elsewhere", true);

        TaskSnapshot failed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.FAILED, failed.getState());
        assertEquals(frozen.getCheckpoint(), failed.getCheckpoint());
        assertEquals(0, harness.backend.actions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void externallyReplantedFrozenTargetCompletesWithoutAnAction() {
        harness = new Harness(crop("wheat-7", true));
        TaskSpec spec = FarmTask.finitePass("farm", "north-field", 2);
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.backend.current = crop("wheat-0", false);

        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(0, harness.backend.actions);
        assertEquals(
            "1",
            completed.getCheckpoint()
                .getValues()
                .get("nextIndex"));
        assertEquals(
            "0",
            completed.getCheckpoint()
                .getValues()
                .get("verifiedMutations"));
    }

    @Test
    public void pauseCancelsAnUnconfirmedMutationWithoutAdvancingAndResumeReobserves() {
        harness = new Harness(crop("wheat-7", true));
        TaskSpec spec = FarmTask.finitePass("farm", "north-field", 2);
        harness.controller.submit(spec);
        harness.controller.tick();
        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        RecordingBackend.Handle first = harness.backend.handle;

        harness.controller.pause(spec.getId());
        TaskSnapshot suspended = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.SUSPENDED, suspended.getState());
        assertEquals(submitted.getCheckpoint(), suspended.getCheckpoint());
        assertEquals(ActionState.CANCELLED, first.state);
        assertEquals(
            "0",
            suspended.getCheckpoint()
                .getValues()
                .get("nextIndex"));

        harness.controller.resume(spec.getId());
        task(harness.controller.tick(), spec.getId());
        assertEquals(2, harness.backend.actions);
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String id) {
        return snapshot.findTask(id)
            .orElseThrow(() -> new AssertionError("missing task " + id));
    }

    private static CropObservation crop(String fingerprint, boolean mature) {
        return new CropObservation(
            new BasePosition(0, 2, 64, 4),
            CropFamily.VANILLA,
            fingerprint,
            "minecraft:wheat_seeds",
            true,
            mature,
            false);
    }

    private static final class Harness implements AutoCloseable {

        private final InMemoryActionBroker broker = new InMemoryActionBroker();
        private final RecordingBackend backend;
        private final TaskOrchestrator controller;

        private Harness(CropObservation crop) {
            backend = new RecordingBackend(crop);
            controller = new TaskOrchestrator(
                new FixedClock(),
                new RuntimeTaskRunnerFactory(UnusedNavigationAccess.INSTANCE, new Access(backend)),
                broker);
        }

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class Access implements FarmRuntimeAccess {

        private final FarmBackend backend;

        private Access(FarmBackend backend) {
            this.backend = backend;
        }

        @Override
        public FarmBackend getFarmBackend() {
            return backend;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private static final class RecordingBackend implements FarmBackend {

        private final NamedArea plot = new NamedArea(
            "north-field",
            "North field",
            new BasePosition(0, 0, 60, 0),
            new BasePosition(0, 8, 70, 8));
        private CropObservation current;
        private int actions;
        private ActionLease lease;
        private Handle handle;

        private RecordingBackend(CropObservation crop) {
            current = crop;
        }

        @Override
        public Availability availability() {
            return Availability.available("recording farm backend ready");
        }

        @Override
        public PassSnapshot scan(ScanRequest request) {
            return new PassSnapshot(
                request.getTaskId(),
                request.getActionEpoch(),
                plot,
                Collections.singletonList(current));
        }

        @Override
        public TargetSnapshot observe(TargetRequest request) {
            return new TargetSnapshot(
                request.getTaskId(),
                request.getPassRevision(),
                request.getActionEpoch(),
                request.getObservationIndex(),
                current,
                new SeedReserveEvidence(1L, "inventory", "minecraft:wheat_seeds", 10, request.getMinimumSeedReserve()));
        }

        @Override
        public ActionHandle execute(ActionRequest request, ActionLease actionLease) {
            assertTrue(actionLease.isValid());
            actions++;
            lease = actionLease;
            handle = new Handle(request);
            return handle;
        }

        private void confirm(CropObservation after) {
            assertNotNull(handle);
            current = after;
            handle.after = after;
            handle.state = ActionState.CONFIRMED;
        }

        private static final class Handle implements ActionHandle {

            private final ActionRequest request;
            private ActionState state = ActionState.SUBMITTED;
            private CropObservation after;

            private Handle(ActionRequest request) {
                this.request = request;
            }

            @Override
            public String getRequestId() {
                return request.getRequestId();
            }

            @Override
            public ActionProgress progress() {
                return new ActionProgress(request.getRequestId(), state, state.name(), after);
            }

            @Override
            public void cancel() {
                state = ActionState.CANCELLED;
                after = null;
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
