package io.github.kaseyawolf2.horizonwright.runtime.persistence;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRoute;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadStatus;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileReassociation;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileStatePaths;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;

public class TaskControllerPersistenceCoordinatorTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void atomicSaveAndFreshReloadPreserveControllerAndConnectionEpoch() throws Exception {
        Fixture fixture = fixture();
        TaskOrchestrator original = orchestrator();
        original.submit(TaskSpec.of("journey", "goto", "Journey", TaskLane.MANUAL));

        RuntimeEnvelope saved = fixture.coordinator.save(200L, 11L, null, original);
        TaskOrchestrator fresh = orchestrator();
        RuntimeEnvelope loaded = fixture.coordinator.restoreFresh(fresh::restoreState);

        assertEquals(saved, loaded);
        assertEquals(11L, loaded.getLastConnectionEpoch());
        assertEquals(12L, loaded.minimumNextConnectionEpoch());
        assertEquals(
            TaskState.QUEUED,
            fresh.inspect("journey")
                .get()
                .getState());
        assertTrue(Files.isRegularFile(fixture.paths.getRuntimeFile()));

        try {
            fixture.coordinator.restoreFresh(fresh::restoreState);
            fail("expected non-fresh controller refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("empty"));
        }
    }

    @Test
    public void missingRuntimeRestoresAsEmptyButRequiresAValidBoundProfile() throws Exception {
        Fixture fixture = fixture();
        TaskOrchestrator fresh = orchestrator();

        RuntimeEnvelope loaded = fixture.coordinator.restoreFresh(fresh::restoreState);

        assertEquals(0L, loaded.getLastConnectionEpoch());
        assertTrue(
            loaded.getTaskControllerState()
                .getTasks()
                .isEmpty());
        assertFalse(Files.exists(fixture.paths.getRuntimeFile()));
    }

    @Test
    public void corruptAndNewerRuntimeAreRefusedAndPreserved() throws Exception {
        Fixture fixture = fixture();
        fixture.coordinator.save(200L, 3L, null, orchestrator());
        Files.write(fixture.paths.getRuntimeFile(), "{broken".getBytes(StandardCharsets.UTF_8));
        byte[] corrupt = Files.readAllBytes(fixture.paths.getRuntimeFile());

        assertRefused(fixture.coordinator, PersistenceLoadStatus.CORRUPT);
        try {
            fixture.coordinator.save(300L, 4L, null, orchestrator());
            fail("expected corrupt overwrite refusal");
        } catch (TaskControllerPersistenceException expected) {
            assertEquals(
                PersistenceLoadStatus.CORRUPT,
                expected.getLoadStatus()
                    .get());
        }
        assertArrayEquals(corrupt, Files.readAllBytes(fixture.paths.getRuntimeFile()));

        String newer = "{\n" + "  \"schemaVersion\": 2,\n" + "  \"documentKind\": \"runtime\"\n" + "}\n";
        Files.write(fixture.paths.getRuntimeFile(), newer.getBytes(StandardCharsets.UTF_8));
        assertRefused(fixture.coordinator, PersistenceLoadStatus.NEWER_SCHEMA);
        assertEquals(newer, new String(Files.readAllBytes(fixture.paths.getRuntimeFile()), StandardCharsets.UTF_8));
    }

    @Test
    public void mismatchedRuntimeBindingAndRegressingConnectionEpochAreRefused() throws Exception {
        Fixture fixture = fixture();
        fixture.store.saveRuntime(
            fixture.paths,
            new RuntimeEnvelope(
                100L,
                "gtnh-main",
                "other-server:25565",
                "world:alpha",
                9L,
                null,
                TaskControllerState.empty()));
        assertRefused(fixture.coordinator, PersistenceLoadStatus.PROFILE_MISMATCH);

        Files.delete(fixture.paths.getRuntimeFile());
        fixture.coordinator.save(200L, 9L, null, orchestrator());
        try {
            fixture.coordinator.save(300L, 8L, null, orchestrator());
            fail("expected epoch regression refusal");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("backwards"));
        }
    }

    private Fixture fixture() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        HorizonwrightPersistenceStore store = new HorizonwrightPersistenceStore(root);
        WorldProfileIdentity identity = identity();
        ProfileStatePaths paths = store.pathsForProfile(identity.getProfileId());
        store.saveProfile(
            paths,
            new ProfileEnvelope(
                10L,
                identity,
                Collections.<ProfileReassociation>emptyList(),
                Collections.<NamedLocation>emptyList(),
                Collections.<NamedRoute>emptyList()));
        return new Fixture(store, paths, new TaskControllerPersistenceCoordinator(store, identity));
    }

    private static WorldProfileIdentity identity() {
        return new WorldProfileIdentity("gtnh-main", "GTNH Main", "server.test:25565", "world:alpha", 1L);
    }

    private static TaskOrchestrator orchestrator() {
        return new TaskOrchestrator(
            new ZeroClock(),
            (spec,
                checkpoint) -> context -> StepResult
                    .completed(context.getActionEpoch(), context.getCheckpoint(), "complete"),
            new InMemoryActionBroker());
    }

    private static void assertRefused(TaskControllerPersistenceCoordinator coordinator,
        PersistenceLoadStatus expectedStatus) throws Exception {
        try {
            coordinator.load();
            fail("expected persistence refusal");
        } catch (TaskControllerPersistenceException expected) {
            assertEquals(
                expectedStatus,
                expected.getLoadStatus()
                    .get());
        }
    }

    private static final class ZeroClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }

    private static final class Fixture {

        private final HorizonwrightPersistenceStore store;
        private final ProfileStatePaths paths;
        private final TaskControllerPersistenceCoordinator coordinator;

        private Fixture(HorizonwrightPersistenceStore store, ProfileStatePaths paths,
            TaskControllerPersistenceCoordinator coordinator) {
            this.store = store;
            this.paths = paths;
            this.coordinator = coordinator;
        }
    }
}
