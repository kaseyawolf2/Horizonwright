package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SaplingReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservation;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservationState;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedCause;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class TreeTaskRunnerTest {

    @Test
    public void selectedPlantingPatternAndSpacingPersistInTaskParameters() {
        TaskSpec spec = TreeTask.scheduledPass("woodlot", 4, 7, 9)
            .instantiate("jungle-grid");
        assertEquals(7, TreeTask.plantSpecies(spec.getParameters()));
        assertEquals(9, TreeTask.plantSpacing(spec.getParameters()));
        assertEquals(-1, TreeTask.plantSpecies(Collections.emptyMap()));
    }

    private Harness harness;

    @Test
    public void plantingUsesNewGridSiteInsteadOfFelledRoot() {
        harness = new Harness(standing(), 8);
        TaskSpec spec = TreeTask.scheduledPass("woodlot", 2, 0, 5)
            .instantiate("grid-test");
        harness.backend.gridOverride = Collections.singletonList(secondTree(clear()));
        harness.backend.observed.put("second-oak", secondTree(clear()));
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirm(clear());
        harness.controller.tick();
        TaskSnapshot gridSaved = task(harness.controller.tick(), spec.getId());
        TreeTaskCheckpointCodec.State restored = TreeTaskCheckpointCodec.decode(spec, gridSaved.getCheckpoint());
        assertTrue(restored.planting);
        assertEquals(
            new BasePosition(0, 5, 64, 4),
            restored.pending.get(0)
                .getReplantPosition());
        harness.controller.tick();
        assertEquals(
            new BasePosition(0, 5, 64, 4),
            harness.backend.handle.request.getDecision()
                .getReplantPosition());
        harness.backend.confirm(secondTree(planted()));
        assertEquals(TaskState.COMPLETED, task(harness.controller.tick(), spec.getId()).getState());
    }

    @Test
    public void legacyTaskWithoutGridSettingsBlocksBeforeFelling() {
        harness = new Harness(standing(), 8);
        TaskSpec spec = TreeTask.finitePass("legacy", "woodlot", 2);
        harness.controller.submit(spec);
        assertEquals(TaskState.BLOCKED, task(harness.controller.tick(), spec.getId()).getState());
        assertEquals(0, harness.backend.actions);
    }

    @Test
    public void allTreesAreFelledBeforeCollectionAndPlantingAndCollectionResumes() {
        harness = new Harness(standing(), 8);
        TreeObservation second = secondTree(standing());
        harness.backend.scanTrees = Arrays.asList(standing(), second);
        harness.backend.observed.put(second.getTreeId(), second);
        TaskSpec spec = TreeTask.scheduledPass("woodlot", 2, 0, 5)
            .instantiate("two-trees");
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirm(clear());
        harness.controller.tick();
        assertEquals(0, harness.backend.collections);
        harness.controller.tick();
        assertEquals(2, harness.backend.actions);
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.DIG));
        harness.backend.confirm(secondTree(clear()));
        TaskSnapshot bothFelled = task(harness.controller.tick(), spec.getId());
        TreeTaskCheckpointCodec.State restored = TreeTaskCheckpointCodec.decode(spec, bothFelled.getCheckpoint());
        assertTrue(restored.collecting());
        assertEquals(2, restored.pending.size());
        harness.backend.collectionReady = false;
        harness.controller.tick();
        assertEquals(2, harness.backend.actions);
        harness.controller.pause(spec.getId());
        TaskSnapshot suspended = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.SUSPENDED, suspended.getState());
        assertTrue(
            TreeTaskCheckpointCodec.decode(spec, suspended.getCheckpoint())
                .collecting());
        harness.controller.resume(spec.getId());
        harness.backend.collectionReady = true;
        harness.controller.tick();
        harness.controller.tick();
        assertEquals(3, harness.backend.actions);
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.DIG));
        harness.backend.confirm(planted());
        harness.controller.tick();
        harness.controller.tick();
        assertEquals(4, harness.backend.actions);
        harness.backend.confirm(secondTree(planted()));
        assertEquals(TaskState.COMPLETED, task(harness.controller.tick(), spec.getId()).getState());
    }

    private static TreeObservation secondTree(TreeObservation original) {
        return new TreeObservation(
            "second-oak",
            original.getRevision(),
            original.getObservationFingerprint(),
            original.getRequiredSaplingFingerprint(),
            original.getState() == TreeObservationState.STANDING
                ? Arrays.asList(new BasePosition(0, 5, 64, 4), new BasePosition(0, 5, 65, 4))
                : Collections.emptyList(),
            new BasePosition(0, 5, 64, 4),
            original.getState(),
            original.isMature(),
            false);
    }

    @After
    public void closeHarness() {
        if (harness != null) harness.close();
    }

    @Test
    public void fellAndReplantAdvanceOnlyAfterSeparateConfirmedPostconditions() {
        harness = new Harness(standing(), 4);
        TaskSpec spec = TreeTask.scheduledPass("woodlot", 2, 0, 5)
            .instantiate("trees");
        harness.controller.submit(spec);

        assertEquals(TaskState.RUNNING, task(harness.controller.tick(), spec.getId()).getState());
        TaskSnapshot fellSubmitted = task(harness.controller.tick(), spec.getId());
        assertEquals(1, harness.backend.actions);
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.DIG));
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.PLACE));
        assertEquals(
            "READY_TO_FELL",
            fellSubmitted.getCheckpoint()
                .getValues()
                .get("work.stage"));

        harness.backend.confirm(clear());
        TaskSnapshot clearConfirmed = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "READY_TO_REPLANT",
            clearConfirmed.getCheckpoint()
                .getValues()
                .get("pending.0.work.stage"));
        assertEquals(
            "1",
            clearConfirmed.getCheckpoint()
                .getValues()
                .get("nextIndex"));

        task(harness.controller.tick(), spec.getId()); // collection, before planting
        assertEquals(1, harness.backend.actions);
        assertEquals(1, harness.backend.collections);
        task(harness.controller.tick(), spec.getId());
        assertEquals(2, harness.backend.actions);
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.CONTAINER));
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.DIG));
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.PLACE));

        harness.backend.confirm(planted());
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
                .get("verifiedTrees"));
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void pauseDuringReplantCancelsWithoutLosingDurableClearFrontier() {
        harness = new Harness(standing(), 4);
        TaskSpec spec = TreeTask.scheduledPass("woodlot", 2, 0, 5)
            .instantiate("trees");
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick();
        harness.backend.confirm(clear());
        harness.controller.tick();
        harness.controller.tick(); // collect before planting
        TaskSnapshot replantSubmitted = task(harness.controller.tick(), spec.getId());
        RecordingBackend.Handle firstReplant = harness.backend.handle;

        harness.controller.pause(spec.getId());
        TaskSnapshot suspended = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.SUSPENDED, suspended.getState());
        assertEquals(replantSubmitted.getCheckpoint(), suspended.getCheckpoint());
        assertEquals(TreeBackend.ActionState.CANCELLED, firstReplant.state);
        assertEquals(
            "READY_TO_REPLANT",
            suspended.getCheckpoint()
                .getValues()
                .get("work.stage"));

        harness.controller.resume(spec.getId());
        task(harness.controller.tick(), spec.getId());
        assertEquals(3, harness.backend.actions);
    }

    @Test
    public void reserveShortageBlocksBeforeFellingAuthorityIsAcquired() {
        harness = new Harness(standing(), 2);
        TaskSpec spec = TreeTask.scheduledPass("woodlot", 2, 0, 5)
            .instantiate("trees");
        harness.controller.submit(spec);
        harness.controller.tick();

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            BlockedCause.MISSING_REQUIREMENT,
            blocked.getBlockedReason()
                .get()
                .getCause());
        assertEquals(0, harness.backend.actions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String id) {
        return snapshot.findTask(id)
            .orElseThrow(() -> new AssertionError("missing task " + id));
    }

    private static TreeObservation standing() {
        return new TreeObservation(
            "oak@2,64,4",
            10L,
            "oak-standing",
            "minecraft:sapling:0",
            Arrays.asList(new BasePosition(0, 2, 64, 4), new BasePosition(0, 2, 65, 4)),
            new BasePosition(0, 2, 64, 4),
            TreeObservationState.STANDING,
            true,
            false);
    }

    private static TreeObservation clear() {
        return new TreeObservation(
            "oak@2,64,4",
            11L,
            "oak-clear",
            "minecraft:sapling:0",
            Collections.<BasePosition>emptyList(),
            new BasePosition(0, 2, 64, 4),
            TreeObservationState.FELLED_CLEAR,
            false,
            false);
    }

    private static TreeObservation planted() {
        return new TreeObservation(
            "oak@2,64,4",
            12L,
            "oak-planted",
            "minecraft:sapling:0",
            Collections.<BasePosition>emptyList(),
            new BasePosition(0, 2, 64, 4),
            TreeObservationState.SAPLING_PLANTED,
            false,
            false);
    }

    @Test
    public void emptyPlantingSiteStartsWithPlantingAndSurvivesCheckpointRoundTrip() {
        harness = new Harness(clear(), 4);
        TaskSpec spec = TreeTask.scheduledPass("woodlot", 2, 0, 5)
            .instantiate("plant-empty");
        harness.controller.submit(spec);
        harness.controller.tick();
        harness.controller.tick(); // defer empty planting site
        harness.controller.tick(); // collect before planting
        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        assertEquals(1, harness.backend.actions);
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.CONTAINER));
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.DIG));
        assertEquals(
            io.github.kaseyawolf2.horizonwright.core.base.TreeWorkStage.READY_TO_REPLANT,
            TreeTaskCheckpointCodec.decode(spec, submitted.getCheckpoint()).work.getStage());
        harness.backend.confirm(planted());
        assertEquals(TaskState.COMPLETED, task(harness.controller.tick(), spec.getId()).getState());
    }

    @Test
    public void twoByTwoPlantingPatternIsRetainedInDurableWorkIdentity() {
        TreeObservation site = new TreeObservation(
            "empty@2,64,4|2x2",
            1L,
            "clear",
            "minecraft:sapling:5",
            Collections.emptyList(),
            new BasePosition(0, 2, 64, 4),
            TreeObservationState.FELLED_CLEAR,
            false,
            false);
        NamedArea area = new NamedArea("test", "Test", new BasePosition(0, 0, 60, 0), new BasePosition(0, 10, 80, 10));
        io.github.kaseyawolf2.horizonwright.core.base.TreeWorkCheckpoint work = io.github.kaseyawolf2.horizonwright.core.base.TreeWorkCheckpoint
            .start(area, 1L, site);
        assertEquals(
            4,
            work.getReplantPositions()
                .size());
        assertTrue(
            work.getReplantPositions()
                .contains(new BasePosition(0, 3, 64, 5)));
    }

    private static final class Harness implements AutoCloseable {

        private final InMemoryActionBroker broker = new InMemoryActionBroker();
        private final RecordingBackend backend;
        private final TaskOrchestrator controller;

        private Harness(TreeObservation initial, int saplings) {
            backend = new RecordingBackend(initial, saplings);
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

        private final TreeBackend backend;

        private Access(TreeBackend backend) {
            this.backend = backend;
        }

        @Override
        public FarmBackend getFarmBackend() {
            return null;
        }

        @Override
        public TreeBackend getTreeBackend() {
            return backend;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private static final class RecordingBackend implements TreeBackend {

        private final NamedArea area = new NamedArea(
            "woodlot",
            "Woodlot",
            new BasePosition(0, 0, 60, 0),
            new BasePosition(0, 8, 72, 8));
        private final int saplings;
        private TreeObservation current;
        private int actions;
        private int collections;
        private boolean collectionReady = true;
        private java.util.List<TreeObservation> scanTrees;
        private java.util.List<TreeObservation> gridOverride;
        private final java.util.Map<String, TreeObservation> observed = new java.util.HashMap<>();
        private ActionLease lease;
        private Handle handle;

        private RecordingBackend(TreeObservation initial, int saplings) {
            current = initial;
            this.saplings = saplings;
        }

        @Override
        public FarmBackend.Availability availability() {
            return FarmBackend.Availability.available("recording tree backend ready");
        }

        @Override
        public PassSnapshot scan(ScanRequest request) {
            return new PassSnapshot(
                request.getTaskId(),
                request.getActionEpoch(),
                area,
                scanTrees == null ? Collections.singletonList(current) : scanTrees);
        }

        @Override
        public TargetSnapshot observe(TargetRequest request) {
            return new TargetSnapshot(
                request.getTaskId(),
                request.getPassRevision(),
                request.getActionEpoch(),
                request.getIndex(),
                observed.getOrDefault(
                    request.getWork()
                        .getTreeId(),
                    current),
                new SaplingReserveEvidence(
                    1L,
                    "inventory",
                    "minecraft:sapling:0",
                    saplings,
                    request.getMinimumSaplingReserve()));
        }

        @Override
        public PassSnapshot plantingGrid(ScanRequest request) {
            if (gridOverride != null)
                return new PassSnapshot(request.getTaskId(), request.getActionEpoch(), area, gridOverride);
            java.util.List<TreeObservation> sites = scanTrees == null ? Collections.singletonList(clear())
                : Arrays.asList(clear(), secondTree(clear()));
            return new PassSnapshot(request.getTaskId(), request.getActionEpoch(), area, sites);
        }

        @Override
        public CollectionHandle collectDrops(String taskId, NamedArea collectionArea, ActionLease collectionLease) {
            collections++;
            assertFalse(
                collectionLease.getCapabilities()
                    .contains(ActionCapability.DIG));
            return new CollectionHandle() {

                public boolean poll() {
                    return collectionReady;
                }

                public String detail() {
                    return "collected";
                }

                public void cancel() {}
            };
        }

        @Override
        public ActionHandle execute(ActionRequest request, ActionLease actionLease) {
            assertTrue(actionLease.isValid());
            actions++;
            lease = actionLease;
            handle = new Handle(request);
            return handle;
        }

        private void confirm(TreeObservation after) {
            assertNotNull(handle);
            current = after;
            observed.put(after.getTreeId(), after);
            handle.after = after;
            handle.state = ActionState.CONFIRMED;
        }

        private static final class Handle implements ActionHandle {

            private final ActionRequest request;
            private ActionState state = ActionState.SUBMITTED;
            private TreeObservation after;

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
