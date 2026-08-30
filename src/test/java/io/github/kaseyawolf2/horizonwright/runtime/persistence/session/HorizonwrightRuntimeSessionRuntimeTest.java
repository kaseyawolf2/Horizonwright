package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.runtime.task.GoToTask;

public class HorizonwrightRuntimeSessionRuntimeTest {

    @Test
    public void factoryCreatesRuntimesWithIndependentTasksAndActionEpochs() {
        RecordingDeathFactory deaths = new RecordingDeathFactory(new ArrayList<String>());
        HorizonwrightRuntimeSessionFactory factory = new HorizonwrightRuntimeSessionFactory(
            connection -> ScheduleEnvironment.connected(100L, Collections.<String>emptySet()),
            deaths);
        WorldProfileIdentity profileA = identity("profile-a", "world-a");
        WorldProfileIdentity profileB = identity("profile-b", "world-b");
        RuntimeSessionRuntime runtimeA = factory.create(connection(profileA, "connection-a", 3L));
        RuntimeSessionRuntime runtimeB = factory.create(connection(profileB, "connection-b", 4L));

        runtimeA.restore(envelope(profileA, stateWithTaskAndEpoch("a-task", 40L)));
        runtimeB.restore(envelope(profileB, TaskControllerState.empty()));

        assertTrue(
            runtimeA.getController()
                .inspect("a-task")
                .isPresent());
        assertFalse(
            runtimeB.getController()
                .inspect("a-task")
                .isPresent());
        assertEquals(
            41L,
            runtimeA.getController()
                .snapshot()
                .getActionEpoch());
        assertEquals(
            1L,
            runtimeB.getController()
                .snapshot()
                .getActionEpoch());

        runtimeA.getController()
            .submit(GoToTask.create("a-second-task", 0, 10, 64, 10, 1));
        assertTrue(
            runtimeB.getController()
                .snapshot()
                .getTasks()
                .isEmpty());
        assertEquals(2, deaths.boundaries.size());
        assertEquals(1, deaths.boundaries.get(0).restoreCount);
        assertEquals(1, deaths.boundaries.get(1).restoreCount);

        runtimeA.close();
        runtimeB.close();
    }

    @Test
    public void restoreCompletesExactlyOnceBeforeAnyTickOrSnapshot() {
        List<String> events = new ArrayList<>();
        RecordingDeathFactory deaths = new RecordingDeathFactory(events);
        HorizonwrightRuntimeSessionFactory factory = new HorizonwrightRuntimeSessionFactory(connection -> {
            events.add("environment");
            return ScheduleEnvironment.connected(100L, Collections.<String>emptySet());
        }, deaths);
        WorldProfileIdentity identity = identity("profile-a", "world-a");
        RuntimeSessionRuntime runtime = factory.create(connection(identity, "connection-a", 1L));

        assertRequiresRestore(runtime);
        assertTrue(events.isEmpty());

        RuntimeEnvelope envelope = envelope(identity, TaskControllerState.empty());
        runtime.restore(envelope);
        try {
            runtime.restore(envelope);
            fail("expected duplicate restore refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("already"));
        }

        runtime.clientTick();
        assertEquals(Arrays.asList("death-restore", "environment"), events);
        assertEquals(1, deaths.boundaries.get(0).restoreCount);
        assertEquals(0, deaths.boundaries.get(0).snapshotCount);
        assertEquals(null, runtime.snapshotUnresolvedDeathState());
        assertEquals(1, deaths.boundaries.get(0).snapshotCount);
        runtime.close();
        runtime.close();
    }

    @Test
    public void activeAdapterRejectsADisconnectedTickEnvironment() {
        RecordingDeathFactory deaths = new RecordingDeathFactory(new ArrayList<String>());
        HorizonwrightRuntimeSessionFactory factory = new HorizonwrightRuntimeSessionFactory(
            connection -> ScheduleEnvironment.disconnected(),
            deaths);
        WorldProfileIdentity identity = identity("profile-a", "world-a");
        RuntimeSessionRuntime runtime = factory.create(connection(identity, "connection-a", 1L));
        runtime.restore(envelope(identity, TaskControllerState.empty()));

        try {
            runtime.clientTick();
            fail("expected disconnected environment refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("connected"));
        } finally {
            runtime.close();
        }
    }

    private static void assertRequiresRestore(RuntimeSessionRuntime runtime) {
        try {
            runtime.clientTick();
            fail("expected pre-restore tick refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("restore"));
        }
        try {
            runtime.getController();
            fail("expected pre-restore controller refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("restore"));
        }
        try {
            runtime.snapshotUnresolvedDeathState();
            fail("expected pre-restore death snapshot refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("restore"));
        }
    }

    private static RuntimeSessionConnection connection(WorldProfileIdentity identity, String token, long epoch) {
        return new RuntimeSessionConnection(identity, new RuntimeConnectionToken(token), epoch);
    }

    private static RuntimeEnvelope envelope(WorldProfileIdentity identity, TaskControllerState state) {
        return new RuntimeEnvelope(
            100L,
            identity.getProfileId(),
            identity.getServerAddress(),
            identity.getWorldFingerprint(),
            0L,
            null,
            state);
    }

    private static TaskControllerState stateWithTaskAndEpoch(String taskId, long actionEpoch) {
        HorizonwrightRuntime seed = HorizonwrightRuntime.createSession();
        try {
            seed.getController()
                .submit(GoToTask.create(taskId, 0, 1, 64, 1, 1));
            TaskControllerState exported = seed.exportControllerState();
            return new TaskControllerState(actionEpoch, exported.getTasks(), exported.getScheduler());
        } finally {
            seed.close();
        }
    }

    private static WorldProfileIdentity identity(String profileId, String worldFingerprint) {
        return new WorldProfileIdentity(profileId, profileId, "server.test:25565", worldFingerprint, 1L);
    }

    private static final class RecordingDeathFactory implements RuntimeSessionDeathStateBoundaryFactory {

        private final List<String> events;
        private final List<RecordingDeathBoundary> boundaries = new ArrayList<>();

        private RecordingDeathFactory(List<String> events) {
            this.events = events;
        }

        @Override
        public RuntimeSessionDeathStateBoundary create(RuntimeSessionConnection connection) {
            RecordingDeathBoundary boundary = new RecordingDeathBoundary(events);
            boundaries.add(boundary);
            return boundary;
        }
    }

    private static final class RecordingDeathBoundary implements RuntimeSessionDeathStateBoundary {

        private final List<String> events;
        private UnresolvedDeathState state;
        private int restoreCount;
        private int snapshotCount;

        private RecordingDeathBoundary(List<String> events) {
            this.events = events;
        }

        @Override
        public void restore(UnresolvedDeathState restoredState) {
            events.add("death-restore");
            restoreCount++;
            state = restoredState;
        }

        @Override
        public UnresolvedDeathState snapshot() {
            snapshotCount++;
            return state;
        }
    }
}
