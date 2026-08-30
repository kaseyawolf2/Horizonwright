package io.github.kaseyawolf2.horizonwright.core.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.RestoredTaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleRule;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleState;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduledTaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.SchedulerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSuspensionReason;

public class RuntimeControllerStatePersistenceTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void controllerQueueScheduleCheckpointAndEpochRoundTripThroughRuntimeEnvelope() throws Exception {
        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        store.saveProfile(paths, profile());
        TaskControllerState state = controllerState();
        RuntimeEnvelope runtime = new RuntimeEnvelope(
            500L,
            "gtnh-main",
            "server.test:25565",
            "world:alpha",
            17L,
            null,
            state);

        store.saveRuntime(paths, runtime);
        RuntimeEnvelope reloaded = store.loadRuntime(paths)
            .getValue();

        assertEquals(runtime, reloaded);
        assertEquals(17L, reloaded.getLastConnectionEpoch());
        assertEquals(18L, reloaded.minimumNextConnectionEpoch());
        assertEquals(
            29L,
            reloaded.getTaskControllerState()
                .getLastActionEpoch());
        assertEquals(
            2,
            reloaded.getTaskControllerState()
                .getTasks()
                .size());
        RestoredTaskSnapshot blocked = reloaded.getTaskControllerState()
            .getTasks()
            .get(0);
        assertEquals(TaskState.BLOCKED, blocked.getState());
        assertEquals(
            4L,
            blocked.getCheckpoint()
                .getRevision());
        assertEquals(
            "returning",
            blocked.getCheckpoint()
                .getValues()
                .get("phase"));
        assertEquals(
            "cleanup",
            blocked.getSourceScheduleId()
                .get());
        ScheduleSnapshot schedule = reloaded.getTaskControllerState()
            .getScheduler()
            .getSchedules()
            .get(0);
        assertEquals(1L, schedule.getSequence());
        assertEquals(
            "schedule[cleanup]#1",
            schedule.getLastTaskId()
                .get());

        String json = new String(Files.readAllBytes(paths.getRuntimeFile()), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"taskControllerState\""));
        assertTrue(json.contains("\"lastConnectionEpoch\": 17"));
    }

    @Test
    public void invalidNestedQueueStateIsCorruptRatherThanReflectivelyAccepted() throws Exception {
        HorizonwrightPersistenceStore store = store();
        ProfileStatePaths paths = store.pathsForProfile("gtnh-main");
        store.saveProfile(paths, profile());
        store.saveRuntime(
            paths,
            new RuntimeEnvelope(500L, "gtnh-main", "server.test:25565", "world:alpha", 3L, null, controllerState()));
        String valid = new String(Files.readAllBytes(paths.getRuntimeFile()), StandardCharsets.UTF_8);
        String corrupt = valid.replaceFirst("\\\"queuePosition\\\": 0", "\\\"queuePosition\\\": 7");
        Files.write(paths.getRuntimeFile(), corrupt.getBytes(StandardCharsets.UTF_8));

        PersistenceLoadResult<RuntimeEnvelope> result = store.loadRuntime(paths);

        assertEquals(PersistenceLoadStatus.CORRUPT, result.getStatus());
        assertTrue(
            result.getDiagnostic()
                .contains("queue positions are not contiguous"));
    }

    @Test
    public void runtimeRejectsConnectionEpochsThatCannotAdvance() {
        assertInvalidConnectionEpoch(-1L);
        assertInvalidConnectionEpoch(Long.MAX_VALUE);
    }

    private HorizonwrightPersistenceStore store() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        return new HorizonwrightPersistenceStore(root);
    }

    private static ProfileEnvelope profile() {
        WorldProfileIdentity identity = new WorldProfileIdentity(
            "gtnh-main",
            "GTNH Main",
            "server.test:25565",
            "world:alpha",
            1L);
        return new ProfileEnvelope(
            10L,
            identity,
            Collections.<ProfileReassociation>emptyList(),
            Collections.<NamedLocation>emptyList(),
            Collections.<NamedRoute>emptyList());
    }

    private static TaskControllerState controllerState() {
        ScheduleRule rule = ScheduleRule.connectedInterval(
            "cleanup",
            ScheduledTaskSpec.of("cleanup", "Cleanup", TaskLane.CHORE),
            1_000L,
            100L,
            Collections.singleton("base-loaded"),
            2);
        ScheduleSnapshot schedule = new ScheduleSnapshot(
            rule,
            ScheduleState.ACTIVE,
            1L,
            900L,
            ScheduleSnapshot.NO_WORLD_OCCURRENCE,
            false,
            "schedule[cleanup]#1",
            1L,
            0L);
        SchedulerSnapshot scheduler = new SchedulerSnapshot(700L, 12_000L, true, Collections.singletonList(schedule));
        BlockedReason reason = BlockedReason
            .missingRequirement("drop-off missing", "base", "DROP_OFF", "Register a drop-off container.");
        RestoredTaskSnapshot blocked = new RestoredTaskSnapshot(
            new TaskSpec(
                "scheduled-blocked",
                "cleanup",
                "Scheduled Cleanup",
                TaskLane.CHORE,
                Collections.singletonMap("mode", "strict")),
            TaskState.BLOCKED,
            new TaskCheckpoint(4L, Collections.singletonMap("phase", "returning")),
            2,
            300L,
            TaskSuspensionReason.NONE,
            reason,
            0,
            3L,
            "waiting for drop-off",
            "cleanup");
        RestoredTaskSnapshot completed = new RestoredTaskSnapshot(
            TaskSpec.of("schedule[cleanup]#1", "cleanup", "Cleanup", TaskLane.CHORE),
            TaskState.COMPLETED,
            TaskCheckpoint.empty(),
            0,
            0L,
            TaskSuspensionReason.NONE,
            null,
            -1,
            0L,
            "complete",
            "cleanup");
        return new TaskControllerState(29L, Arrays.asList(blocked, completed), scheduler);
    }

    private static void assertInvalidConnectionEpoch(long epoch) {
        try {
            new RuntimeEnvelope(
                500L,
                "gtnh-main",
                "server.test:25565",
                "world:alpha",
                epoch,
                null,
                TaskControllerState.empty());
            fail("expected invalid connection epoch rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("advanceable"));
        }
    }
}
