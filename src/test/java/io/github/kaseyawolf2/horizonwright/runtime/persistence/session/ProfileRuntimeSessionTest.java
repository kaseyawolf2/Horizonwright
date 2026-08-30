package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRoute;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileReassociation;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileStatePaths;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceException;

public class ProfileRuntimeSessionTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void connectRestoreTickAndDisconnectAreOrderedAndDeduplicated() {
        List<String> events = new ArrayList<>();
        WorldProfileIdentity identity = identity("profile-a", "world-a");
        RecordingPersistence persistence = new RecordingPersistence(identity, envelope(identity, 7L), events);
        RecordingFactory factory = new RecordingFactory(events, false);
        ProfileRuntimeSession session = new ProfileRuntimeSession(factory, () -> 500L);
        RuntimeConnectionToken token = new RuntimeConnectionToken("connection-a");

        assertEquals(RuntimeSessionState.UNBOUND, session.getState());
        session.bind(identity, persistence);
        assertEquals(RuntimeSessionState.WAITING_FOR_WORLD, session.getState());

        RuntimeSessionConnection connected = session.connect(token);
        assertEquals(8L, connected.getConnectionEpoch());
        assertEquals(RuntimeSessionState.ACTIVE, session.getState());
        assertSame(connected, session.connect(new RuntimeConnectionToken("connection-a")));
        assertEquals(1, persistence.loadCount);
        assertEquals(1, factory.createCount);
        assertEquals(1, factory.runtime.restoreCount);

        assertFalse(session.clientTick(new RuntimeConnectionToken("stale")));
        assertTrue(session.clientTick(token));
        assertEquals(1, factory.runtime.tickCount);
        assertFalse(session.disconnect(new RuntimeConnectionToken("stale")));
        assertEquals(RuntimeSessionState.ACTIVE, session.getState());

        assertTrue(session.disconnect(token));
        assertEquals(RuntimeSessionState.RETIRED, session.getState());
        assertFalse(session.disconnect(token));
        session.close();

        assertEquals(1, persistence.saveCount);
        assertEquals(8L, persistence.savedConnectionEpoch);
        assertEquals(1, factory.runtime.closeCount);
        assertEquals(Arrays.asList("load", "create", "restore", "tick", "save", "close"), events);
    }

    @Test
    public void closeWhileActivePerformsOneFinalSaveBeforeOneClose() {
        List<String> events = new ArrayList<>();
        WorldProfileIdentity identity = identity("profile-a", "world-a");
        RecordingPersistence persistence = new RecordingPersistence(identity, envelope(identity, 0L), events);
        RecordingFactory factory = new RecordingFactory(events, false);
        ProfileRuntimeSession session = new ProfileRuntimeSession(factory, () -> 600L);

        session.bind(identity, persistence);
        session.connect(new RuntimeConnectionToken("connection-a"));
        session.close();
        session.close();

        assertEquals(RuntimeSessionState.RETIRED, session.getState());
        assertEquals(1, persistence.saveCount);
        assertEquals(1, factory.runtime.closeCount);
        assertEquals(Arrays.asList("load", "create", "restore", "save", "close"), events);
    }

    @Test
    public void corruptRuntimeFailsBeforeFactoryAndIsNeverTickedSavedOrReplaced() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        WorldProfileIdentity identity = identity("profile-a", "world-a");
        HorizonwrightPersistenceStore store = new HorizonwrightPersistenceStore(root);
        ProfileStatePaths paths = saveProfile(store, identity);
        byte[] corrupt = "{broken".getBytes(StandardCharsets.UTF_8);
        Files.write(paths.getRuntimeFile(), corrupt);
        RecordingFactory factory = new RecordingFactory(new ArrayList<String>(), false);
        ProfileRuntimeSession session = new ProfileRuntimeSession(factory, () -> 700L);
        RuntimeConnectionToken token = new RuntimeConnectionToken("connection-a");
        session.bind(identity, new TaskControllerRuntimeSessionPersistence(store, identity));

        try {
            session.connect(token);
            fail("expected corrupt persistence refusal");
        } catch (RuntimeSessionException expected) {
            assertTrue(expected.getCause() instanceof TaskControllerPersistenceException);
        }

        assertEquals(RuntimeSessionState.FAILED, session.getState());
        assertEquals(0, factory.createCount);
        assertFalse(session.clientTick(token));
        session.close();
        assertArrayEquals(corrupt, Files.readAllBytes(paths.getRuntimeFile()));
    }

    @Test
    public void mismatchedEnvelopeNeverReachesTheSelectedProfilesRuntime() {
        List<String> events = new ArrayList<>();
        WorldProfileIdentity profileA = identity("profile-a", "world-a");
        WorldProfileIdentity profileB = identity("profile-b", "world-b");
        RecordingPersistence dishonestBoundary = new RecordingPersistence(profileB, envelope(profileA, 2L), events);
        RecordingFactory factory = new RecordingFactory(events, false);
        ProfileRuntimeSession profileBSession = new ProfileRuntimeSession(factory, () -> 800L);
        profileBSession.bind(profileB, dishonestBoundary);

        try {
            profileBSession.connect(new RuntimeConnectionToken("connection-b"));
            fail("expected cross-profile runtime refusal");
        } catch (RuntimeSessionException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("does not match"));
        }

        assertEquals(RuntimeSessionState.FAILED, profileBSession.getState());
        assertEquals(0, factory.createCount);
        assertEquals(0, dishonestBoundary.saveCount);
        assertEquals(Collections.singletonList("load"), events);
    }

    @Test
    public void failedRestoreClosesFreshRuntimeButNeverActivatesTicksOrSavesIt() {
        List<String> events = new ArrayList<>();
        WorldProfileIdentity identity = identity("profile-a", "world-a");
        RecordingPersistence persistence = new RecordingPersistence(identity, envelope(identity, 0L), events);
        RecordingFactory factory = new RecordingFactory(events, true);
        ProfileRuntimeSession session = new ProfileRuntimeSession(factory, () -> 900L);
        RuntimeConnectionToken token = new RuntimeConnectionToken("connection-a");
        session.bind(identity, persistence);

        try {
            session.connect(token);
            fail("expected restore failure");
        } catch (RuntimeSessionException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("restore"));
        }

        assertEquals(RuntimeSessionState.FAILED, session.getState());
        assertEquals(1, factory.runtime.restoreCount);
        assertEquals(0, factory.runtime.tickCount);
        assertEquals(1, factory.runtime.closeCount);
        assertEquals(0, persistence.saveCount);
        assertFalse(session.clientTick(token));
        assertEquals(Arrays.asList("load", "create", "restore", "close"), events);
    }

    private static ProfileStatePaths saveProfile(HorizonwrightPersistenceStore store, WorldProfileIdentity identity)
        throws Exception {
        ProfileStatePaths paths = store.pathsForProfile(identity.getProfileId());
        store.saveProfile(
            paths,
            new ProfileEnvelope(
                1L,
                identity,
                Collections.<ProfileReassociation>emptyList(),
                Collections.<NamedLocation>emptyList(),
                Collections.<NamedRoute>emptyList()));
        return paths;
    }

    private static WorldProfileIdentity identity(String profileId, String worldFingerprint) {
        return new WorldProfileIdentity(profileId, profileId, "server.test:25565", worldFingerprint, 1L);
    }

    private static RuntimeEnvelope envelope(WorldProfileIdentity identity, long lastConnectionEpoch) {
        return new RuntimeEnvelope(
            100L,
            identity.getProfileId(),
            identity.getServerAddress(),
            identity.getWorldFingerprint(),
            lastConnectionEpoch,
            null,
            TaskControllerState.empty());
    }

    private static TaskOrchestrator controller() {
        return new TaskOrchestrator(
            new ZeroClock(),
            (spec,
                checkpoint) -> context -> StepResult
                    .completed(context.getActionEpoch(), context.getCheckpoint(), "complete"),
            new InMemoryActionBroker());
    }

    private static final class RecordingPersistence implements RuntimeSessionPersistence {

        private final WorldProfileIdentity expectedIdentity;
        private final RuntimeEnvelope loaded;
        private final List<String> events;
        private int loadCount;
        private int saveCount;
        private long savedConnectionEpoch;

        private RecordingPersistence(WorldProfileIdentity expectedIdentity, RuntimeEnvelope loaded,
            List<String> events) {
            this.expectedIdentity = expectedIdentity;
            this.loaded = loaded;
            this.events = events;
        }

        @Override
        public WorldProfileIdentity getExpectedIdentity() {
            return expectedIdentity;
        }

        @Override
        public RuntimeEnvelope load() {
            events.add("load");
            loadCount++;
            return loaded;
        }

        @Override
        public RuntimeEnvelope save(long writtenAtEpochMillis, RuntimeSessionConnection connection,
            RuntimeSessionRuntime runtime) {
            events.add("save");
            saveCount++;
            savedConnectionEpoch = connection.getConnectionEpoch();
            return new RuntimeEnvelope(
                writtenAtEpochMillis,
                expectedIdentity.getProfileId(),
                expectedIdentity.getServerAddress(),
                expectedIdentity.getWorldFingerprint(),
                connection.getConnectionEpoch(),
                runtime.snapshotUnresolvedDeathState(),
                runtime.getController()
                    .exportState());
        }
    }

    private static final class RecordingFactory implements RuntimeSessionRuntimeFactory {

        private final List<String> events;
        private final boolean failRestore;
        private int createCount;
        private RecordingRuntime runtime;

        private RecordingFactory(List<String> events, boolean failRestore) {
            this.events = events;
            this.failRestore = failRestore;
        }

        @Override
        public RuntimeSessionRuntime create(RuntimeSessionConnection connection) {
            events.add("create");
            createCount++;
            runtime = new RecordingRuntime(events, failRestore);
            return runtime;
        }
    }

    private static final class RecordingRuntime implements RuntimeSessionRuntime {

        private final List<String> events;
        private final boolean failRestore;
        private final TaskOrchestrator controller = controller();
        private int restoreCount;
        private int tickCount;
        private int closeCount;

        private RecordingRuntime(List<String> events, boolean failRestore) {
            this.events = events;
            this.failRestore = failRestore;
        }

        @Override
        public void restore(RuntimeEnvelope envelope) {
            events.add("restore");
            restoreCount++;
            if (failRestore) {
                throw new IllegalStateException("injected restore failure");
            }
            controller.restoreState(envelope.getTaskControllerState());
        }

        @Override
        public void clientTick() {
            events.add("tick");
            tickCount++;
        }

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
            events.add("close");
            closeCount++;
            controller.close();
        }
    }

    private static final class ZeroClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
