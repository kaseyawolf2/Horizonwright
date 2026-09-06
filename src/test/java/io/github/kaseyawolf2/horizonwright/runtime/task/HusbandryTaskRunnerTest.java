package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.base.AnimalObservation;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryObservation;
import io.github.kaseyawolf2.horizonwright.core.base.LivestockSpecies;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class HusbandryTaskRunnerTest {

    private Harness harness;

    @After
    public void closeHarness() {
        if (harness != null) harness.close();
    }

    @Test
    public void disabledBackendBlocksWithoutAuthority() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        TaskOrchestrator controller = new TaskOrchestrator(
            new Clock(),
            new RuntimeTaskRunnerFactory(UnusedNavigation.INSTANCE),
            broker);
        try {
            TaskSpec spec = task(2, 4, 8);
            controller.submit(spec);
            TaskSnapshot blocked = task(controller.tick(), spec.getId());
            assertEquals(TaskState.BLOCKED, blocked.getState());
            assertEquals(TaskCheckpoint.empty(), blocked.getCheckpoint());
            assertTrue(
                broker.snapshot()
                    .getActiveOwners()
                    .isEmpty());
        } finally {
            controller.close();
        }
    }

    @Test
    public void stablePopulationCompletesWithoutActionLease() {
        harness = new Harness(observation(1L, stableAdults()), null);
        TaskSpec spec = task(2, 4, 8);
        harness.controller.submit(spec);
        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(0, harness.backend.actions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void feedAdvancesOnlyAfterConfirmationThenFreshStableScan() {
        HusbandryObservation needsFeed = observation(1L, twoReadyAdults());
        HusbandryObservation stable = observation(2L, fourStableAdults());
        harness = new Harness(needsFeed, stable);
        TaskSpec spec = task(4, 6, 8);
        harness.controller.submit(spec);

        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.RUNNING, submitted.getState());
        assertEquals(HusbandryActionKind.FEED_ADULT, harness.backend.kind);
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.USE));
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.HELD_USE));
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.CONTAINER));
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.ATTACK));

        harness.backend.handle.state = HusbandryBackend.ActionState.CONFIRMED;
        TaskSnapshot advanced = task(harness.controller.tick(), spec.getId());
        assertEquals(
            "1",
            advanced.getCheckpoint()
                .getValues()
                .get("verifiedActions"));
        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(2, harness.backend.observations);
    }

    @Test
    public void pauseCancelsUnconfirmedActionWithoutAdvancement() {
        harness = new Harness(observation(1L, twoReadyAdults()), null);
        TaskSpec spec = task(4, 6, 8);
        harness.controller.submit(spec);
        TaskSnapshot submitted = task(harness.controller.tick(), spec.getId());
        RecordingBackend.Handle first = harness.backend.handle;
        harness.controller.pause(spec.getId());
        TaskSnapshot suspended = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.SUSPENDED, suspended.getState());
        assertEquals(submitted.getCheckpoint(), suspended.getCheckpoint());
        assertEquals(HusbandryBackend.ActionState.CANCELLED, first.state);
        harness.controller.resume(spec.getId());
        task(harness.controller.tick(), spec.getId());
        assertEquals(2, harness.backend.actions);
    }

    @Test
    public void hardActionCapBlocksAnOtherwiseRepeatingPolicy() {
        HusbandryObservation repeat = observation(1L, twoReadyAdults());
        harness = new Harness(repeat, repeat);
        TaskSpec spec = task(4, 6, 1);
        harness.controller.submit(spec);
        task(harness.controller.tick(), spec.getId());
        harness.backend.handle.state = HusbandryBackend.ActionState.CONFIRMED;
        task(harness.controller.tick(), spec.getId());
        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertTrue(
            blocked.getBlockedReason()
                .get()
                .getDetail()
                .contains("action cap"));
        assertEquals(1, harness.backend.actions);
    }

    @Test
    public void missingSpeciesPrerequisiteBlocksBeforeActionAuthority() {
        harness = new Harness(observation(1L, twoReadyAdults()), null);
        harness.backend.ready = false;
        TaskSpec spec = task(4, 6, 8);
        harness.controller.submit(spec);

        TaskSnapshot blocked = task(harness.controller.tick(), spec.getId());

        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(0, harness.backend.actions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
        assertTrue(
            blocked.getBlockedReason()
                .get()
                .getDetail()
                .contains("feed unavailable"));
    }

    private static TaskSpec task(int minimum, int maximum, int actionCap) {
        return HusbandryTask.finitePass("animals", "cow-pen", LivestockSpecies.COW, minimum, maximum, actionCap);
    }

    @Test
    public void excessPopulationWithoutCullingCompletesWithoutAttackLease() {
        harness = new Harness(observation(1L, fourStableAdults()), null);
        TaskSpec spec = task(2, 2, 8);
        harness.controller.submit(spec);
        TaskSnapshot result = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.COMPLETED, result.getState());
        assertEquals(0, harness.backend.actions);
        assertTrue(
            harness.broker.snapshot()
                .getActiveOwners()
                .isEmpty());
    }

    @Test
    public void authorizedCullWaitsForConfirmationThenRescansAndStopsAtMaximum() {
        harness = new Harness(observation(1L, fourStableAdults()), observation(2L, stableAdults()));
        TaskSpec spec = HusbandryTask.finitePass("animals", "cow-pen", LivestockSpecies.COW, 2, 3, 8, true);
        harness.controller.submit(spec);
        task(harness.controller.tick(), spec.getId());
        assertEquals(HusbandryActionKind.CULL_EXCESS_ADULT, harness.backend.kind);
        assertTrue(harness.backend.cullingAllowed);
        assertTrue(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.ATTACK));
        assertFalse(
            harness.backend.lease.getCapabilities()
                .contains(ActionCapability.USE));
        TaskSnapshot waiting = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskCheckpoint.empty(), waiting.getCheckpoint());
        harness.backend.handle.state = HusbandryBackend.ActionState.CONFIRMED;
        task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.COMPLETED, task(harness.controller.tick(), spec.getId()).getState());
        assertEquals(2, harness.backend.observations);
        assertEquals(1, harness.backend.actions);
    }

    @Test
    public void oldTasksDefaultToNoCullingAndSchedulesRetainExplicitChoice() {
        TaskSpec spec = task(2, 4, 8);
        java.util.Map<String, String> legacy = new java.util.LinkedHashMap<>(spec.getParameters());
        legacy.remove("allowCulling");
        TaskSpec old = new io.github.kaseyawolf2.horizonwright.core.task.ScheduledTaskSpec(
            spec.getType(),
            spec.getDisplayName(),
            spec.getLane(),
            legacy).instantiate("old");
        assertFalse(HusbandryTask.allowCulling(old));
        assertTrue(
            HusbandryTask.allowCulling(
                HusbandryTask.scheduledPass("cow-pen", LivestockSpecies.COW, 2, 4, 8, true)
                    .instantiate("scheduled")));
        assertFalse(HusbandryTask.allowCulling(spec));
    }

    @Test
    public void cullingTransitionsToCollectionWithBaritoneAuthorityThenCompletes() {
        HusbandryObservation drops = new HusbandryObservation(
            PEN,
            2L,
            "after-cull-drops",
            stableAdults(),
            Collections.singletonList(
                new io.github.kaseyawolf2.horizonwright.core.base.HusbandryDropObservation(
                    "leather",
                    "minecraft:leather",
                    new BasePosition(0, 2, 64, 2))),
            true,
            true);
        harness = new Harness(observation(1L, fourStableAdults()), drops);
        TaskSpec spec = HusbandryTask.finitePass("animals", "cow-pen", LivestockSpecies.COW, 2, 3, 1, true);
        harness.controller.submit(spec);
        task(harness.controller.tick(), spec.getId());
        assertEquals(HusbandryActionKind.CULL_EXCESS_ADULT, harness.backend.kind);
        harness.backend.handle.state = HusbandryBackend.ActionState.CONFIRMED;
        task(harness.controller.tick(), spec.getId());
        task(harness.controller.tick(), spec.getId());
        assertEquals(HusbandryActionKind.COLLECT_DROPS, harness.backend.kind);
        assertEquals(
            java.util.EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK),
            harness.backend.lease.getCapabilities());
        harness.backend.after = observation(3L, stableAdults());
        harness.backend.handle.state = HusbandryBackend.ActionState.CONFIRMED;
        task(harness.controller.tick(), spec.getId());
        TaskSnapshot completed = task(harness.controller.tick(), spec.getId());
        assertEquals(TaskState.COMPLETED, completed.getState());
        assertEquals(
            "2",
            completed.getCheckpoint()
                .getValues()
                .get("verifiedActions"));
        assertEquals(
            "1",
            completed.getCheckpoint()
                .getValues()
                .get("verifiedCollections"));
        assertEquals(2, harness.backend.actions);
    }

    @Test
    public void legacyAndSeparateCollectionCheckpointsCanBeRestored() {
        harness = new Harness(observation(1L, stableAdults()), null);
        TaskSpec spec = task(2, 4, 16);
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("verifiedActions", "16");
        new HusbandryTaskRunner(spec, new TaskCheckpoint(16L, values), new Access(harness.backend));
        values.put("verifiedCollections", "6");
        new HusbandryTaskRunner(spec, new TaskCheckpoint(16L, values), new Access(harness.backend));
        values.put("verifiedCollections", "17");
        try {
            new HusbandryTaskRunner(spec, new TaskCheckpoint(16L, values), new Access(harness.backend));
            org.junit.Assert.fail("collections cannot exceed all confirmed actions");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("collection count"));
        }
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String id) {
        return snapshot.findTask(id)
            .orElseThrow(() -> new AssertionError("missing task " + id));
    }

    private static HusbandryObservation observation(long revision, List<AnimalObservation> animals) {
        return new HusbandryObservation(
            PEN,
            revision,
            "animals-" + revision + "-" + animals.size(),
            animals,
            Collections.emptyList(),
            true,
            true);
    }

    private static List<AnimalObservation> stableAdults() {
        return Arrays.asList(adult("a", false), adult("b", false));
    }

    private static List<AnimalObservation> twoReadyAdults() {
        return Arrays.asList(adult("a", true), adult("b", true));
    }

    private static List<AnimalObservation> fourStableAdults() {
        return Arrays.asList(adult("a", false), adult("b", false), adult("c", false), adult("d", false));
    }

    private static AnimalObservation adult(String id, boolean ready) {
        return new AnimalObservation(
            id,
            LivestockSpecies.COW,
            new BasePosition(0, 2, 64, 2),
            true,
            false,
            false,
            false,
            ready,
            false);
    }

    private static final NamedArea PEN = new NamedArea(
        "cow-pen",
        "Cow pen",
        new BasePosition(0, 0, 60, 0),
        new BasePosition(0, 10, 70, 10));

    private static final class Harness implements AutoCloseable {

        private final InMemoryActionBroker broker = new InMemoryActionBroker();
        private final RecordingBackend backend;
        private final TaskOrchestrator controller;

        private Harness(HusbandryObservation initial, HusbandryObservation after) {
            backend = new RecordingBackend(initial, after);
            controller = new TaskOrchestrator(
                new Clock(),
                new RuntimeTaskRunnerFactory(UnusedNavigation.INSTANCE, new Access(backend)),
                broker);
        }

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class Access implements HusbandryRuntimeAccess {

        private final HusbandryBackend backend;

        private Access(HusbandryBackend backend) {
            this.backend = backend;
        }

        @Override
        public HusbandryBackend getHusbandryBackend() {
            return backend;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private static final class RecordingBackend implements HusbandryBackend {

        private HusbandryObservation current;
        private HusbandryObservation after;
        private int observations;
        private int actions;
        private HusbandryActionKind kind;
        private ActionLease lease;
        private Handle handle;
        private boolean ready = true;
        private boolean cullingAllowed;

        private RecordingBackend(HusbandryObservation initial, HusbandryObservation after) {
            current = initial;
            this.after = after;
        }

        @Override
        public Availability availability() {
            return Availability.available("recording husbandry ready");
        }

        @Override
        public ObservationSnapshot observe(ObservationRequest request) {
            observations++;
            if (request.getVerifiedActions() > 0 && after != null) current = after;
            return new ObservationSnapshot(
                request.getTaskId(),
                request.getActionEpoch(),
                request.getVerifiedActions(),
                current);
        }

        @Override
        public ActionReadiness readiness(io.github.kaseyawolf2.horizonwright.core.base.HusbandryPlan plan) {
            return ready ? ActionReadiness.ready("recording prerequisites ready")
                : ActionReadiness.unavailable("exact feed unavailable");
        }

        @Override
        public ActionHandle execute(ActionRequest request, ActionLease lease) {
            cullingAllowed = request.isCullingAllowed();
            actions++;
            this.lease = lease;
            kind = request.getPlan()
                .getActions()
                .get(0)
                .getKind();
            handle = new Handle(request.getRequestId());
            return handle;
        }

        private static final class Handle implements ActionHandle {

            private final String id;
            private ActionState state = ActionState.EXECUTING;

            private Handle(String id) {
                this.id = id;
            }

            @Override
            public String getRequestId() {
                return id;
            }

            @Override
            public ActionProgress progress() {
                return new ActionProgress(id, state, state.name());
            }

            @Override
            public void cancel() {
                state = ActionState.CANCELLED;
            }
        }
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

    private static final class Clock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
