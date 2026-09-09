package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceException;

public class ProfileRuntimeCheckpointDurabilityTest {

    private static final WorldProfileIdentity IDENTITY = new WorldProfileIdentity(
        "durability",
        "Durability",
        "singleplayer",
        "world",
        0L);
    private static final RuntimeConnectionToken TOKEN = new RuntimeConnectionToken("connection");
    private static final String MARKER = "horizonwright.inventory.preparing";

    @Test
    public void preparationMarkerIsSavedBeforeTheFollowingMutationCanRun() {
        Persistence persistence = new Persistence();
        Runtime runtime = new Runtime(persistence);
        try (ProfileRuntimeSession session = session(runtime, persistence)) {
            runtime.controller.submit(TaskSpec.of("work", "test", "Durable work", TaskLane.MANUAL));
            session.clientTick(TOKEN);
            assertEquals(1, persistence.saves);
            assertEquals(0, runtime.mutations);
            assertEquals(
                "true",
                persistence.saved.getTaskControllerState()
                    .getTasks()
                    .get(0)
                    .getCheckpoint()
                    .getValues()
                    .get(MARKER));
            session.clientTick(TOKEN);
            assertEquals(1, runtime.mutations);
            assertEquals(Arrays.asList("prepare", "save", "mutation", "save"), persistence.events);
        }
    }

    @Test
    public void unchangedWaitDoesNotWriteTheSameCheckpointOnEveryClientTick() {
        Persistence persistence = new Persistence();
        Runtime runtime = new Runtime(persistence);
        runtime.hold = true;
        try (ProfileRuntimeSession session = session(runtime, persistence)) {
            runtime.controller.submit(TaskSpec.of("work", "test", "Durable work", TaskLane.MANUAL));
            for (int tick = 0; tick < 12; tick++) session.clientTick(TOKEN);
            assertEquals(1, persistence.saves);
            assertEquals(0, runtime.mutations);
            runtime.hold = false;
            session.clientTick(TOKEN);
            assertEquals(2, persistence.saves);
            assertEquals(1, runtime.mutations);
        }
    }

    @Test
    public void checkpointSaveFailureRetiresRuntimeBeforeAnyFollowingMutation() {
        Persistence persistence = new Persistence();
        Runtime runtime = new Runtime(persistence);
        try (ProfileRuntimeSession session = session(runtime, persistence)) {
            runtime.controller.submit(TaskSpec.of("work", "test", "Durable work", TaskLane.MANUAL));
            persistence.failWrites = true;
            assertThrows(RuntimeSessionException.class, () -> session.clientTick(TOKEN));
            assertEquals(RuntimeSessionState.FAILED, session.getState());
            assertEquals(0, runtime.mutations);
            assertTrue(runtime.closed);
            assertFalse(session.clientTick(TOKEN));
            assertEquals(0, runtime.mutations);
        }
    }

    @Test
    public void staleSaveReceiptCannotAuthorizeTheFollowingMutation() {
        Persistence persistence = new Persistence();
        Runtime runtime = new Runtime(persistence);
        try (ProfileRuntimeSession session = session(runtime, persistence)) {
            runtime.controller.submit(TaskSpec.of("work", "test", "Durable work", TaskLane.MANUAL));
            persistence.returnStale = true;
            assertThrows(RuntimeSessionException.class, () -> session.clientTick(TOKEN));
            assertEquals(RuntimeSessionState.FAILED, session.getState());
            assertEquals(0, runtime.mutations);
            assertFalse(session.clientTick(TOKEN));
        }
    }

    private static ProfileRuntimeSession session(Runtime runtime, Persistence persistence) {
        ProfileRuntimeSession session = new ProfileRuntimeSession(connection -> runtime, () -> 10L);
        session.bind(IDENTITY, persistence);
        session.connect(TOKEN);
        return session;
    }

    private static RuntimeEnvelope envelope(long epoch, TaskControllerState state) {
        return new RuntimeEnvelope(
            10L,
            IDENTITY.getProfileId(),
            IDENTITY.getServerAddress(),
            IDENTITY.getWorldFingerprint(),
            epoch,
            null,
            state);
    }

    private static final class Persistence implements RuntimeSessionPersistence {

        final List<String> events = new ArrayList<>();
        RuntimeEnvelope saved = envelope(0L, TaskControllerState.empty());
        int saves;
        boolean failWrites;
        boolean returnStale;

        @Override
        public WorldProfileIdentity getExpectedIdentity() {
            return IDENTITY;
        }

        @Override
        public RuntimeEnvelope load() {
            return saved;
        }

        @Override
        public RuntimeEnvelope save(long now, RuntimeSessionConnection connection, RuntimeSessionRuntime runtime)
            throws TaskControllerPersistenceException {
            if (failWrites) throw new IllegalStateException("injected save failure: disk unavailable");
            if (returnStale) return saved;
            events.add("save");
            saves++;
            saved = envelope(
                connection.getConnectionEpoch(),
                runtime.getController()
                    .exportState());
            return saved;
        }
    }

    private static final class Runtime implements RuntimeSessionRuntime {

        final TaskOrchestrator controller;
        int mutations;
        boolean hold;
        boolean closed;

        Runtime(Persistence persistence) {
            controller = new TaskOrchestrator(() -> 0L, (spec, checkpoint) -> context -> {
                if (context.getCheckpoint()
                    .getRevision() == 0L) {
                    persistence.events.add("prepare");
                    return StepResult.progress(
                        context.getActionEpoch(),
                        new TaskCheckpoint(1L, Collections.singletonMap(MARKER, "true")),
                        "prepared");
                }
                if (hold) return StepResult.waitFor(context.getActionEpoch(), context.getCheckpoint(), 0L, "waiting");
                assertEquals(
                    "true",
                    persistence.saved.getTaskControllerState()
                        .getTasks()
                        .get(0)
                        .getCheckpoint()
                        .getValues()
                        .get(MARKER));
                mutations++;
                persistence.events.add("mutation");
                return StepResult.completed(
                    context.getActionEpoch(),
                    new TaskCheckpoint(2L, Collections.singletonMap(MARKER, "false")),
                    "confirmed");
            }, new InMemoryActionBroker());
        }

        @Override
        public void restore(RuntimeEnvelope envelope) {
            controller.restoreState(envelope.getTaskControllerState());
        }

        @Override
        public void clientTick() {
            controller.tick();
        }

        @Override
        public void prepareDisconnect() {}

        @Override
        public IHorizonwrightController getController() {
            return controller;
        }

        @Override
        public UnresolvedDeathState snapshotUnresolvedDeathState() {
            return null;
        }

        @Override
        public void close() {
            closed = true;
            controller.close();
        }
    }
}
