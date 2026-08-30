package io.github.kaseyawolf2.horizonwright.core.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class TaskSchedulerTest {

    @Test
    public void connectedIntervalsExcludeOfflineTimeAndEmitAtMostOneMissedRun() {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.submit(interval("ore", 100L, 100L, TaskLane.CHORE, 0));

        assertTrue(evaluate(scheduler, 0L, connected(), false).isEmpty());
        assertTrue(evaluate(scheduler, 99L, connected(), false).isEmpty());
        List<ScheduledTaskRequest> first = evaluate(scheduler, 100L, connected(), false);
        assertEquals(1, first.size());
        assertEquals(
            "schedule[ore]#1",
            first.get(0)
                .getTask()
                .getId());

        assertTrue(evaluate(scheduler, 150L, ScheduleEnvironment.disconnected(), false).isEmpty());
        assertTrue(evaluate(scheduler, 10_000L, ScheduleEnvironment.disconnected(), false).isEmpty());
        assertTrue(evaluate(scheduler, 10_000L, reconnected(), false).isEmpty());
        assertEquals(1, evaluate(scheduler, 10_050L, connected(), false).size());

        List<ScheduledTaskRequest> oneAfterLongConnectedGap = evaluate(scheduler, 20_000L, connected(), false);
        assertEquals(1, oneAfterLongConnectedGap.size());
        assertTrue(evaluate(scheduler, 20_000L, connected(), false).isEmpty());
    }

    @Test
    public void intervalThatBecomesDueAtDisconnectCatchesUpOnceOnReconnect() {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.submit(interval("disconnect-edge", 100L, 100L, TaskLane.CHORE, 0));
        evaluate(scheduler, 0L, connected(), false);

        assertTrue(evaluate(scheduler, 100L, ScheduleEnvironment.disconnected(), false).isEmpty());
        assertTrue(evaluate(scheduler, 10_000L, ScheduleEnvironment.disconnected(), false).isEmpty());
        List<ScheduledTaskRequest> catchUp = evaluate(scheduler, 10_000L, reconnected(), false);

        assertEquals(1, catchUp.size());
        assertTrue(
            catchUp.get(0)
                .isCatchUp());
        assertTrue(evaluate(scheduler, 10_000L, connected(), false).isEmpty());
    }

    @Test
    public void dueRulesUseFixedLanesThenRelativeChoreOrder() {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.submit(interval("later", 100L, 0L, TaskLane.CHORE, 20));
        scheduler.submit(interval("fallback", 100L, 0L, TaskLane.FALLBACK, -100));
        scheduler.submit(interval("first", 100L, 0L, TaskLane.CHORE, 10));
        scheduler.submit(interval("safety", 100L, 0L, TaskLane.SAFETY, 500));

        List<ScheduledTaskRequest> requests = evaluate(scheduler, 0L, connected(), true);

        assertEquals(4, requests.size());
        assertEquals(
            "safety",
            requests.get(0)
                .getScheduleId());
        assertEquals(
            "first",
            requests.get(1)
                .getScheduleId());
        assertEquals(
            "later",
            requests.get(2)
                .getScheduleId());
        assertEquals(
            "fallback",
            requests.get(3)
                .getScheduleId());
    }

    @Test
    public void worldWindowWaitsForConditionsAndRunsOnlyOncePerOccurrence() {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.submit(
            ScheduleRule.worldTimeWindow(
                "harvest",
                ScheduledTaskSpec.of("harvest", "Harvest", TaskLane.CHORE),
                1_000,
                2_000,
                Collections.singleton("crops-ready"),
                0,
                true));

        assertTrue(
            scheduler.evaluate(0L, world(1_100L), true, Collections.<String>emptySet())
                .isEmpty());
        List<ScheduledTaskRequest> due = scheduler
            .evaluate(1L, world(1_500L, "crops-ready"), true, Collections.<String>emptySet());
        assertEquals(1, due.size());
        assertFalse(
            due.get(0)
                .isCatchUp());
        assertTrue(
            scheduler.evaluate(2L, world(1_900L, "crops-ready"), true, Collections.<String>emptySet())
                .isEmpty());
        assertEquals(
            1,
            scheduler.evaluate(3L, world(25_100L, "crops-ready"), true, Collections.<String>emptySet())
                .size());
    }

    @Test
    public void reconnectRestoreCollapsesManyMissedWindowsIntoOneCatchUp() {
        TaskScheduler original = new TaskScheduler();
        original.submit(
            ScheduleRule.worldTimeWindow(
                "night-watch",
                ScheduledTaskSpec.of("watch", "Night watch", TaskLane.CHORE),
                1_000,
                2_000,
                Collections.<String>emptySet(),
                0,
                true));
        original.evaluate(0L, world(500L), true, Collections.<String>emptySet());

        TaskScheduler restored = new TaskScheduler();
        restored.restore(original.snapshot());
        long afterFiveDays = 5L * ScheduleRule.WORLD_DAY_TICKS + 5_000L;
        List<ScheduledTaskRequest> catchUp = restored.evaluate(
            0L,
            ScheduleEnvironment.reconnected(afterFiveDays, Collections.<String>emptySet()),
            true,
            Collections.<String>emptySet());

        assertEquals(1, catchUp.size());
        assertTrue(
            catchUp.get(0)
                .isCatchUp());
        assertEquals(
            "schedule[night-watch]#1",
            catchUp.get(0)
                .getTask()
                .getId());
        assertTrue(
            restored
                .evaluate(
                    1L,
                    ScheduleEnvironment.connected(afterFiveDays, Collections.<String>emptySet()),
                    true,
                    Collections.<String>emptySet())
                .isEmpty());
        ScheduleSnapshot snapshot = restored.inspect("night-watch")
            .get();
        assertEquals(1L, snapshot.getTotalRuns());
        assertEquals(1L, snapshot.getCatchUpRuns());
    }

    @Test
    public void restorePreservesConnectedEdgeWithoutChargingOfflineProcessTime() {
        TaskScheduler original = new TaskScheduler();
        original.submit(interval("restart", 100L, 100L, TaskLane.CHORE, 0));
        original.submit(
            ScheduleRule.worldTimeWindow(
                "restart-window",
                ScheduledTaskSpec.of("watch", "Restart watch", TaskLane.CHORE),
                1_000,
                2_000,
                Collections.<String>emptySet(),
                0,
                true));
        original.evaluate(0L, world(500L), false, Collections.<String>emptySet());
        original.evaluate(50L, world(500L), false, Collections.<String>emptySet());
        SchedulerSnapshot persisted = original.snapshot();
        assertTrue(persisted.wasConnectedAtSnapshot());
        assertEquals(50L, persisted.getConnectedElapsedMillis());

        TaskScheduler restored = new TaskScheduler();
        restored.restore(persisted);

        assertTrue(
            restored.snapshot()
                .wasConnectedAtSnapshot());
        assertEquals(
            50L,
            restored.snapshot()
                .getConnectedElapsedMillis());
        assertTrue(evaluate(restored, 10_000L, ScheduleEnvironment.disconnected(), false).isEmpty());
        assertFalse(
            restored.snapshot()
                .wasConnectedAtSnapshot());
        assertEquals(
            50L,
            restored.snapshot()
                .getConnectedElapsedMillis());
        long afterFiveDays = 5L * ScheduleRule.WORLD_DAY_TICKS + 5_000L;
        List<ScheduledTaskRequest> catchUp = restored.evaluate(
            20_000L,
            ScheduleEnvironment.connected(afterFiveDays, Collections.<String>emptySet()),
            false,
            Collections.<String>emptySet());
        assertEquals(1, catchUp.size());
        assertEquals(
            "restart-window",
            catchUp.get(0)
                .getScheduleId());
        assertTrue(
            catchUp.get(0)
                .isCatchUp());
        assertTrue(
            restored
                .evaluate(
                    20_000L,
                    ScheduleEnvironment.connected(afterFiveDays, Collections.<String>emptySet()),
                    false,
                    Collections.<String>emptySet())
                .isEmpty());
        assertTrue(
            restored
                .evaluate(
                    20_049L,
                    ScheduleEnvironment.connected(afterFiveDays, Collections.<String>emptySet()),
                    false,
                    Collections.<String>emptySet())
                .isEmpty());
        assertEquals(
            1,
            restored
                .evaluate(
                    20_050L,
                    ScheduleEnvironment.connected(afterFiveDays, Collections.<String>emptySet()),
                    false,
                    Collections.<String>emptySet())
                .size());
    }

    @Test
    public void occupiedOccurrencesAreConsumedWithoutCreatingBacklog() {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.submit(interval("cleanup", 100L, 100L, TaskLane.CHORE, 0));
        scheduler.evaluate(0L, connected(), false, Collections.<String>emptySet());

        assertTrue(
            scheduler.evaluate(100L, connected(), false, Collections.singleton("cleanup"))
                .isEmpty());
        assertTrue(
            scheduler.evaluate(100L, connected(), false, Collections.<String>emptySet())
                .isEmpty());
        assertEquals(
            0L,
            scheduler.inspect("cleanup")
                .get()
                .getTotalRuns());
    }

    @Test
    public void idleRulesFireOnEdgesAndPauseDoesNotAccumulateIntervals() {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.submit(
            ScheduleRule.idle(
                "idle",
                ScheduledTaskSpec.of("idle", "Idle", TaskLane.FALLBACK),
                Collections.<String>emptySet(),
                0));
        scheduler.submit(interval("paused", 100L, 100L, TaskLane.CHORE, 0));
        scheduler.pause("paused");

        assertEquals(1, evaluate(scheduler, 0L, connected(), true).size());
        assertTrue(evaluate(scheduler, 100L, connected(), true).isEmpty());
        assertTrue(evaluate(scheduler, 200L, connected(), false).isEmpty());
        assertEquals(1, evaluate(scheduler, 201L, connected(), true).size());

        scheduler.resume("paused");
        assertTrue(evaluate(scheduler, 300L, connected(), false).isEmpty());
        assertEquals(1, evaluate(scheduler, 301L, connected(), false).size());
    }

    @Test
    public void rejectsInvalidEnvironmentSnapshotsAndRestoreTargets() {
        assertIllegalArgument(() -> new ScheduleEnvironment(false, false, 1L, Collections.<String>emptySet()));
        assertIllegalArgument(() -> new SchedulerSnapshot(0L, -2L, false, Collections.<ScheduleSnapshot>emptyList()));

        ScheduleRule rule = interval("duplicate", 1L, 0L, TaskLane.CHORE, 0);
        ScheduleSnapshot saved = new ScheduleSnapshot(
            rule,
            ScheduleState.ACTIVE,
            0L,
            0L,
            ScheduleSnapshot.NO_WORLD_OCCURRENCE,
            false,
            null,
            0L,
            0L);
        assertIllegalArgument(() -> new SchedulerSnapshot(0L, -1L, false, Arrays.asList(saved, saved)));

        TaskScheduler nonEmpty = new TaskScheduler();
        nonEmpty.submit(rule);
        try {
            nonEmpty.restore(SchedulerSnapshot.empty());
            fail("expected non-empty restore rejection");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("empty"));
        }
    }

    private static ScheduleRule interval(String id, long interval, long initialDelay, TaskLane lane,
        int relativeOrder) {
        return ScheduleRule.connectedInterval(
            id,
            ScheduledTaskSpec.of("test", id, lane),
            interval,
            initialDelay,
            Collections.<String>emptySet(),
            relativeOrder);
    }

    private static List<ScheduledTaskRequest> evaluate(TaskScheduler scheduler, long now,
        ScheduleEnvironment environment, boolean idle) {
        return scheduler.evaluate(now, environment, idle, Collections.<String>emptySet());
    }

    private static ScheduleEnvironment connected() {
        return ScheduleEnvironment.connected(ScheduleEnvironment.UNKNOWN_WORLD_TIME, Collections.<String>emptySet());
    }

    private static ScheduleEnvironment reconnected() {
        return ScheduleEnvironment.reconnected(ScheduleEnvironment.UNKNOWN_WORLD_TIME, Collections.<String>emptySet());
    }

    private static ScheduleEnvironment world(long worldTime, String... conditions) {
        Set<String> values = new HashSet<>(Arrays.asList(conditions));
        return ScheduleEnvironment.connected(worldTime, values);
    }

    private static void assertIllegalArgument(Runnable invocation) {
        try {
            invocation.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
