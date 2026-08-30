package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;
import io.github.kaseyawolf2.horizonwright.runtime.task.GoToTask;

public class ClientRuntimeSessionManagerTest {

    @Test
    public void disconnectThenReconnectSameProfileUsesFreshRuntimeAndRestoredState() {
        Fixture fixture = fixture();
        WorldProfileIdentity profileA = identity("profile-a", "world-a");
        RuntimeConnectionToken firstToken = new RuntimeConnectionToken("connection-1");
        RuntimeConnectionToken secondToken = new RuntimeConnectionToken("connection-2");

        assertEquals(
            RuntimeSessionState.UNBOUND,
            fixture.manager.getDiagnostic()
                .getState());
        assertFalse(
            fixture.manager.getCurrentRuntime()
                .isPresent());
        fixture.manager.bindProfile(profileA);
        assertEquals(
            RuntimeSessionState.WAITING_FOR_WORLD,
            fixture.manager.getDiagnostic()
                .getState());
        assertFalse(
            fixture.manager.getCurrentController()
                .isPresent());

        assertTrue(fixture.manager.worldReady(profileA, firstToken));
        HorizonwrightRuntime firstRuntime = fixture.manager.getCurrentRuntime()
            .get();
        firstRuntime.getController()
            .submit(GoToTask.create("persisted-a-task", 0, 10, 64, 10, 1));
        long firstEpoch = firstRuntime.controllerSnapshot()
            .getActionEpoch();

        assertTrue(fixture.manager.worldUnavailable(profileA, firstToken));
        assertEquals(
            RuntimeSessionState.WAITING_FOR_WORLD,
            fixture.manager.getDiagnostic()
                .getState());
        assertFalse(
            fixture.manager.getCurrentRuntime()
                .isPresent());
        assertTrue(fixture.manager.worldReady(profileA, secondToken));

        HorizonwrightRuntime secondRuntime = fixture.manager.getCurrentRuntime()
            .get();
        assertNotSame(firstRuntime, secondRuntime);
        assertTrue(
            secondRuntime.getController()
                .inspect("persisted-a-task")
                .isPresent());
        assertTrue(
            secondRuntime.controllerSnapshot()
                .getActionEpoch() > firstEpoch);
        assertEquals(2, fixture.persistence(profileA).loadCount);
        assertEquals(1, fixture.persistence(profileA).saveCount);
        fixture.manager.close();
    }

    @Test
    public void profileChangeRetiresABeforeBAndNeverLeaksATasks() {
        Fixture fixture = fixture();
        WorldProfileIdentity profileA = identity("profile-a", "world-a");
        WorldProfileIdentity profileB = identity("profile-b", "world-b");
        fixture.manager.bindProfile(profileA);
        fixture.manager.worldReady(profileA, new RuntimeConnectionToken("connection-a"));
        HorizonwrightRuntime runtimeA = fixture.manager.getCurrentRuntime()
            .get();
        runtimeA.getController()
            .submit(GoToTask.create("a-only-task", 0, 20, 64, 20, 1));

        assertTrue(fixture.manager.bindProfile(profileB));
        assertEquals(1, fixture.persistence(profileA).saveCount);
        assertEquals(
            RuntimeSessionState.WAITING_FOR_WORLD,
            fixture.manager.getDiagnostic()
                .getState());
        assertFalse(
            fixture.manager.getCurrentRuntime()
                .isPresent());
        fixture.manager.worldReady(profileB, new RuntimeConnectionToken("connection-b"));

        HorizonwrightRuntime runtimeB = fixture.manager.getCurrentRuntime()
            .get();
        assertNotSame(runtimeA, runtimeB);
        assertFalse(
            runtimeB.getController()
                .inspect("a-only-task")
                .isPresent());
        assertTrue(
            fixture.persistence(profileA).loaded.getTaskControllerState()
                .getTasks()
                .size() == 1);
        assertTrue(
            fixture.persistence(profileB).loaded.getTaskControllerState()
                .getTasks()
                .isEmpty());
        fixture.manager.close();
    }

    @Test
    public void duplicateWorldAndDisconnectEdgesAreInert() {
        Fixture fixture = fixture();
        WorldProfileIdentity profileA = identity("profile-a", "world-a");
        RuntimeConnectionToken firstToken = new RuntimeConnectionToken("connection-1");
        RuntimeConnectionToken secondToken = new RuntimeConnectionToken("connection-2");
        RuntimeConnectionToken thirdToken = new RuntimeConnectionToken("connection-3");
        fixture.manager.bindProfile(profileA);

        assertTrue(fixture.manager.worldReady(profileA, firstToken));
        assertFalse(fixture.manager.worldReady(profileA, firstToken));
        assertTrue(fixture.manager.clientTick(profileA, firstToken));
        assertFalse(fixture.manager.clientTick(profileA, new RuntimeConnectionToken("stale")));
        assertFalse(fixture.manager.worldUnavailable(profileA, new RuntimeConnectionToken("stale")));
        assertEquals(1, fixture.persistence(profileA).loadCount);
        assertEquals(1, fixture.deathFactory.createCount);

        assertTrue(fixture.manager.worldUnavailable(profileA, firstToken));
        assertFalse(fixture.manager.worldUnavailable(profileA, firstToken));
        assertFalse(fixture.manager.worldReady(profileA, firstToken));
        assertTrue(fixture.manager.worldReady(profileA, secondToken));
        assertFalse(fixture.manager.worldReady(profileA, secondToken));
        assertFalse(fixture.manager.worldUnavailable(profileA, firstToken));
        assertTrue(fixture.manager.worldUnavailable(profileA, secondToken));
        assertFalse(fixture.manager.worldReady(profileA, firstToken));
        assertFalse(fixture.manager.worldReady(profileA, secondToken));
        assertTrue(fixture.manager.worldReady(profileA, thirdToken));
        assertEquals(3, fixture.persistence(profileA).loadCount);
        assertEquals(3, fixture.deathFactory.createCount);
        fixture.manager.close();
    }

    @Test
    public void mismatchedRestoreBecomesTypedFailureWithoutExposingRuntime() {
        WorldProfileIdentity profileA = identity("profile-a", "world-a");
        WorldProfileIdentity profileB = identity("profile-b", "world-b");
        RecordingPersistence mismatched = new RecordingPersistence(profileA, emptyEnvelope(profileB));
        RecordingDeathFactory deaths = new RecordingDeathFactory();
        ClientRuntimeSessionManager manager = new ClientRuntimeSessionManager(
            runtimeFactory(deaths),
            identity -> mismatched,
            () -> 1_000L);
        manager.bindProfile(profileA);

        try {
            manager.worldReady(profileA, new RuntimeConnectionToken("connection-a"));
            fail("expected mismatched restore refusal");
        } catch (RuntimeSessionException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("does not match"));
        }

        ClientRuntimeSessionDiagnostic diagnostic = manager.getDiagnostic();
        assertEquals(RuntimeSessionState.FAILED, diagnostic.getState());
        assertTrue(
            diagnostic.getFailure()
                .isPresent());
        assertFalse(
            manager.getCurrentRuntime()
                .isPresent());
        assertFalse(
            manager.getCurrentController()
                .isPresent());
        assertEquals(0, mismatched.saveCount);
        assertEquals(0, deaths.createCount);
        manager.close();
    }

    private static Fixture fixture() {
        RecordingPersistenceFactory persistence = new RecordingPersistenceFactory();
        RecordingDeathFactory deaths = new RecordingDeathFactory();
        ClientRuntimeSessionManager manager = new ClientRuntimeSessionManager(
            runtimeFactory(deaths),
            persistence,
            () -> 1_000L);
        return new Fixture(manager, persistence, deaths);
    }

    private static HorizonwrightRuntimeSessionFactory runtimeFactory(RecordingDeathFactory deaths) {
        return new HorizonwrightRuntimeSessionFactory(
            connection -> ScheduleEnvironment.connected(100L, Collections.<String>emptySet()),
            deaths);
    }

    private static RuntimeEnvelope emptyEnvelope(WorldProfileIdentity identity) {
        return new RuntimeEnvelope(
            100L,
            identity.getProfileId(),
            identity.getServerAddress(),
            identity.getWorldFingerprint(),
            0L,
            null,
            io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState.empty());
    }

    private static WorldProfileIdentity identity(String profileId, String worldFingerprint) {
        return new WorldProfileIdentity(profileId, profileId, "server.test:25565", worldFingerprint, 1L);
    }

    private static final class Fixture {

        private final ClientRuntimeSessionManager manager;
        private final RecordingPersistenceFactory persistenceFactory;
        private final RecordingDeathFactory deathFactory;

        private Fixture(ClientRuntimeSessionManager manager, RecordingPersistenceFactory persistenceFactory,
            RecordingDeathFactory deathFactory) {
            this.manager = manager;
            this.persistenceFactory = persistenceFactory;
            this.deathFactory = deathFactory;
        }

        private RecordingPersistence persistence(WorldProfileIdentity identity) {
            return persistenceFactory.forIdentity(identity);
        }
    }

    private static final class RecordingPersistenceFactory implements RuntimeSessionPersistenceFactory {

        private final Map<String, RecordingPersistence> persistenceByProfile = new LinkedHashMap<>();

        @Override
        public RuntimeSessionPersistence create(WorldProfileIdentity identity) {
            return forIdentity(identity);
        }

        private RecordingPersistence forIdentity(WorldProfileIdentity identity) {
            RecordingPersistence persistence = persistenceByProfile.get(identity.getProfileId());
            if (persistence == null) {
                persistence = new RecordingPersistence(identity, emptyEnvelope(identity));
                persistenceByProfile.put(identity.getProfileId(), persistence);
            }
            return persistence;
        }
    }

    private static final class RecordingPersistence implements RuntimeSessionPersistence {

        private final WorldProfileIdentity identity;
        private RuntimeEnvelope loaded;
        private int loadCount;
        private int saveCount;

        private RecordingPersistence(WorldProfileIdentity identity, RuntimeEnvelope loaded) {
            this.identity = identity;
            this.loaded = loaded;
        }

        @Override
        public WorldProfileIdentity getExpectedIdentity() {
            return identity;
        }

        @Override
        public RuntimeEnvelope load() {
            loadCount++;
            return loaded;
        }

        @Override
        public RuntimeEnvelope save(long writtenAtEpochMillis, RuntimeSessionConnection connection,
            RuntimeSessionRuntime runtime) {
            saveCount++;
            loaded = new RuntimeEnvelope(
                writtenAtEpochMillis,
                identity.getProfileId(),
                identity.getServerAddress(),
                identity.getWorldFingerprint(),
                connection.getConnectionEpoch(),
                runtime.snapshotUnresolvedDeathState(),
                runtime.getController()
                    .exportState());
            return loaded;
        }
    }

    private static final class RecordingDeathFactory implements RuntimeSessionDeathStateBoundaryFactory {

        private int createCount;

        @Override
        public RuntimeSessionDeathStateBoundary create(HorizonwrightRuntime runtime,
            RuntimeSessionConnection connection) {
            createCount++;
            return new RuntimeSessionDeathStateBoundary() {

                private UnresolvedDeathState state;

                @Override
                public void restore(UnresolvedDeathState restoredState) {
                    state = restoredState;
                }

                @Override
                public void clientTick() {}

                @Override
                public void disconnect() {}

                @Override
                public UnresolvedDeathState snapshot() {
                    return state;
                }

                @Override
                public void close() {}
            };
        }
    }
}
