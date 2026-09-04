package io.github.kaseyawolf2.horizonwright.core.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.base.LivestockSpecies;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryConfiguration;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.HusbandryTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.SleepTask;

/** Synthetic scheduler/reconnect proof for the Milestone 4 recurring-base acceptance shape. */
public class OperationalBaseMvpOrchestrationTest {

    @Test
    public void recurringBaseChoresAndNightSleepPreserveRadius250ExcavationAcrossReconnect() {
        FakeClock originalClock = new FakeClock();
        RecordingFactory originalFactory = new RecordingFactory();
        TaskOrchestrator original = orchestrator(originalClock, originalFactory);
        try {
            configureSchedules(original);
            ManagedQuarryConfiguration quarryConfiguration = new ManagedQuarryConfiguration(
                "minecraft:cobblestone",
                "minecraft:torch",
                "minecraft:cobblestone",
                4);
            TaskSpec excavation = ExcavationTask
                .managedQuarryCylinder("radius-250", 0, 0, 0, 250, 20, 70, quarryConfiguration);
            original.submit(excavation);

            ControllerSnapshot firstWork = original.tick(environment(12_000L));
            TaskCheckpoint afterFirstBlock = task(firstWork, excavation.getId()).getCheckpoint();
            assertEquals(1, completedBlocks(afterFirstBlock));

            originalClock.advance(100L);
            ControllerSnapshot farmPreemption = original.tick(environment(12_100L));
            assertEquals(TaskState.SUSPENDED, task(farmPreemption, excavation.getId()).getState());
            assertEquals(
                TaskSuspensionReason.PREEMPTION,
                task(farmPreemption, excavation.getId()).getSuspensionReason());
            assertEquals(afterFirstBlock, task(farmPreemption, excavation.getId()).getCheckpoint());
            String firstFarm = scheduleTask(farmPreemption, "farm");
            assertEquals(TaskState.QUEUED, task(farmPreemption, firstFarm).getState());
            assertEquals(TaskState.COMPLETED, task(original.tick(environment(12_100L)), firstFarm).getState());

            ControllerSnapshot resumedAfterFarm = original.tick(environment(12_100L));
            assertEquals(TaskState.RUNNING, task(resumedAfterFarm, excavation.getId()).getState());
            assertEquals(2, completedBlocks(task(resumedAfterFarm, excavation.getId()).getCheckpoint()));

            originalClock.advance(100L);
            ControllerSnapshot nightPreemption = original.tick(environment(13_000L));
            assertEquals(TaskState.SUSPENDED, task(nightPreemption, excavation.getId()).getState());
            String sleep = scheduleTask(nightPreemption, "sleep");
            String secondFarm = scheduleTask(nightPreemption, "farm");
            String husbandry = scheduleTask(nightPreemption, "husbandry");
            assertEquals(TaskState.QUEUED, task(nightPreemption, sleep).getState());
            assertEquals(TaskState.QUEUED, task(nightPreemption, secondFarm).getState());
            assertEquals(TaskState.QUEUED, task(nightPreemption, husbandry).getState());

            assertEquals(TaskState.COMPLETED, task(original.tick(environment(13_000L)), sleep).getState());
            assertEquals(TaskState.COMPLETED, task(original.tick(environment(13_000L)), secondFarm).getState());
            assertEquals(TaskState.COMPLETED, task(original.tick(environment(13_000L)), husbandry).getState());
            ControllerSnapshot resumedAfterNight = original.tick(environment(13_000L));
            TaskCheckpoint beforeReconnect = task(resumedAfterNight, excavation.getId()).getCheckpoint();
            assertEquals(3, completedBlocks(beforeReconnect));

            TaskControllerState persisted = original.exportState();
            FakeClock restoredClock = new FakeClock();
            RecordingFactory restoredFactory = new RecordingFactory();
            TaskOrchestrator restored = orchestrator(restoredClock, restoredFactory);
            try {
                ControllerSnapshot restoredSnapshot = restored.restoreState(persisted);
                assertEquals(TaskState.QUEUED, task(restoredSnapshot, excavation.getId()).getState());
                assertEquals(beforeReconnect, task(restoredSnapshot, excavation.getId()).getCheckpoint());

                ControllerSnapshot reconnected = restored.tick(reconnectedEnvironment(13_000L));
                assertEquals(TaskState.RUNNING, task(reconnected, excavation.getId()).getState());
                assertEquals(4, completedBlocks(task(reconnected, excavation.getId()).getCheckpoint()));
                assertEquals(
                    1L,
                    reconnected.getScheduler()
                        .findSchedule("sleep")
                        .get()
                        .getTotalRuns());
                assertEquals(
                    2L,
                    reconnected.getScheduler()
                        .findSchedule("farm")
                        .get()
                        .getTotalRuns());
                assertEquals(
                    1L,
                    reconnected.getScheduler()
                        .findSchedule("husbandry")
                        .get()
                        .getTotalRuns());
                assertFalse(restoredFactory.restoredExcavationCheckpoints.isEmpty());
                assertEquals(beforeReconnect, restoredFactory.restoredExcavationCheckpoints.get(0));
            } finally {
                restored.close();
            }
        } finally {
            original.close();
        }
    }

    private static void configureSchedules(TaskOrchestrator orchestrator) {
        orchestrator.submitSchedule(
            ScheduleRule.connectedInterval(
                "farm",
                FarmTask.scheduledPass("field", 2),
                100L,
                100L,
                Collections.<String>emptySet(),
                0));
        orchestrator.submitSchedule(
            ScheduleRule.connectedInterval(
                "husbandry",
                HusbandryTask.scheduledPass("cow-pen", LivestockSpecies.COW, 2, 8, 16),
                200L,
                200L,
                Collections.<String>emptySet(),
                10));
        orchestrator.submitSchedule(
            ScheduleRule.worldTimeWindow(
                "sleep",
                SleepTask.scheduled("home-bed"),
                12_542,
                23_461,
                Collections.<String>emptySet(),
                -10,
                false));
    }

    private static TaskOrchestrator orchestrator(FakeClock clock, RecordingFactory factory) {
        return new TaskOrchestrator(clock, factory, new InMemoryActionBroker());
    }

    private static ScheduleEnvironment environment(long worldTime) {
        return ScheduleEnvironment.connected(worldTime, Collections.<String>emptySet());
    }

    private static ScheduleEnvironment reconnectedEnvironment(long worldTime) {
        return ScheduleEnvironment.reconnected(worldTime, Collections.<String>emptySet());
    }

    private static TaskSnapshot task(ControllerSnapshot snapshot, String taskId) {
        return snapshot.findTask(taskId)
            .orElseThrow(() -> new AssertionError("missing task " + taskId));
    }

    private static String scheduleTask(ControllerSnapshot snapshot, String scheduleId) {
        return snapshot.getScheduler()
            .findSchedule(scheduleId)
            .get()
            .getLastTaskId()
            .get();
    }

    private static int completedBlocks(TaskCheckpoint checkpoint) {
        String value = checkpoint.getValues()
            .get("synthetic.completedBlocks");
        return value == null ? 0 : Integer.parseInt(value);
    }

    private static final class RecordingFactory implements TaskRunnerFactory {

        private final java.util.List<TaskCheckpoint> restoredExcavationCheckpoints = new java.util.ArrayList<>();

        @Override
        public TaskRunner create(TaskSpec spec, TaskCheckpoint checkpoint) {
            if (!ExcavationTask.TYPE.equals(spec.getType())) {
                return context -> StepResult
                    .completed(context.getActionEpoch(), context.getCheckpoint(), "chore complete");
            }
            restoredExcavationCheckpoints.add(checkpoint);
            return context -> {
                if (context.isSuspensionRequested()) {
                    return StepResult.safeSuspension(
                        context.getActionEpoch(),
                        context.getCheckpoint(),
                        "excavation frontier parked");
                }
                Map<String, String> values = new LinkedHashMap<>(
                    context.getCheckpoint()
                        .getValues());
                values.put("synthetic.completedBlocks", Integer.toString(completedBlocks(context.getCheckpoint()) + 1));
                TaskCheckpoint advanced = new TaskCheckpoint(
                    context.getCheckpoint()
                        .getRevision() + 1L,
                    values);
                return StepResult.progress(context.getActionEpoch(), advanced, "synthetic excavation advanced");
            };
        }
    }

    private static final class FakeClock implements MonotonicClock {

        private long now;

        @Override
        public long nowMillis() {
            return now;
        }

        private void advance(long millis) {
            now += millis;
        }
    }
}
