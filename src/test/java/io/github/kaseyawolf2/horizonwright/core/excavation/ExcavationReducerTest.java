package io.github.kaseyawolf2.horizonwright.core.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ExcavationReducerTest {

    @Test
    public void reportsAllMutuallyExclusiveVolumeOutcomes() {
        CylinderExcavationSpec spec = managedSpec(2);
        ExcavationCheckpoint checkpoint = ExcavationCheckpoint.start(spec, 10L, 20L);
        ExcavationPlan plan = ExcavationPlanner.calculate(
            spec,
            ExcavationTestSupport.window(
                spec,
                checkpoint.getFrontier(),
                6,
                10L,
                20L,
                ExcavationBlockClassification.AIR,
                ExcavationBlockClassification.PROTECTED_GRAVE,
                ExcavationBlockClassification.PROTECTED_INFRASTRUCTURE,
                ExcavationBlockClassification.FLUID_FLOWING,
                ExcavationBlockClassification.UNREACHABLE,
                ExcavationBlockClassification.FAILED),
            ManagedQuarryConfiguration.defaults());
        ExcavationExecutionResult result = new ExcavationExecutionResult(
            plan,
            ExcavationTestSupport.outcomes(
                plan,
                ExcavationTargetOutcome.COMPLETED,
                ExcavationTargetOutcome.PROTECTED,
                ExcavationTargetOutcome.PROTECTED,
                ExcavationTargetOutcome.FLUID_CONTAINED,
                ExcavationTargetOutcome.UNREACHABLE,
                ExcavationTargetOutcome.FAILED),
            ExcavationSuspensionReason.NONE);

        ExcavationResultApplication application = ExcavationReducer.apply(checkpoint, result);
        ExcavationProgress progress = application.getCheckpoint()
            .getProgress();

        assertTrue(application.wasApplied());
        assertEquals(ExcavationResultDisposition.APPLIED, application.getDisposition());
        assertEquals(
            11L,
            application.getCheckpoint()
                .getTaskRevision());
        assertEquals(1L, progress.getCompleted());
        assertEquals(2L, progress.getProtectedBlocks());
        assertEquals(1L, progress.getFluidContained());
        assertEquals(1L, progress.getUnreachable());
        assertEquals(1L, progress.getFailed());
        assertEquals(spec.getVolume() - 6L, progress.getRemaining());
    }

    @Test
    public void rejectsStaleTaskEpochFrontierAndGeometryWithoutMutation() {
        CylinderExcavationSpec spec = managedSpec(1);
        ExcavationCheckpoint checkpoint = ExcavationCheckpoint.start(spec, 7L, 11L);
        ExcavationPlan plan = breakablePlan(spec, checkpoint, 2);
        ExcavationExecutionResult result = completed(plan, 2);

        ExcavationCheckpoint newerTask = checkpoint.suspend(ExcavationSuspensionReason.PREEMPTED, 8L, 12L);
        assertRejected(newerTask, result, ExcavationResultDisposition.STALE_TASK_REVISION);

        ExcavationCheckpoint newerEpoch = ExcavationCheckpoint.restore(
            spec,
            7L,
            12L,
            checkpoint.getFrontier(),
            checkpoint.getProgress(),
            ExcavationSuspensionReason.NONE);
        assertRejected(newerEpoch, result, ExcavationResultDisposition.STALE_ACTION_EPOCH);

        ExcavationFrontier advanced = plan.getIntents()
            .get(0)
            .getNextFrontier();
        ExcavationCheckpoint newerFrontier = ExcavationCheckpoint.restore(
            spec,
            7L,
            11L,
            advanced,
            new ExcavationProgress(spec.getVolume(), 1L, 0L, 0L, 0L, 0L),
            ExcavationSuspensionReason.NONE);
        assertRejected(newerFrontier, result, ExcavationResultDisposition.STALE_FRONTIER);

        CylinderExcavationSpec other = new CylinderExcavationSpec(0, -15, -16, 1, 5, 5, ExcavationMode.MANAGED_QUARRY);
        ExcavationCheckpoint otherCheckpoint = ExcavationCheckpoint.start(other, 7L, 11L);
        ExcavationPlan otherPlan = breakablePlan(other, otherCheckpoint, 1);
        assertRejected(checkpoint, completed(otherPlan, 1), ExcavationResultDisposition.WRONG_GEOMETRY);

        ExcavationCheckpoint suspendedSameStamp = ExcavationCheckpoint.restore(
            spec,
            7L,
            11L,
            checkpoint.getFrontier(),
            checkpoint.getProgress(),
            ExcavationSuspensionReason.DISCONNECTED);
        assertRejected(suspendedSameStamp, result, ExcavationResultDisposition.CHECKPOINT_SUSPENDED);
    }

    @Test
    public void unloadRepairReconnectAndPreemptionResumeTheExactNextTarget() {
        CylinderExcavationSpec spec = managedSpec(2);
        ExcavationCheckpoint start = ExcavationCheckpoint.start(spec, 4L, 7L);
        ExcavationPlan originalPlan = breakablePlan(spec, start, 5);
        ExcavationExecutionResult unload = new ExcavationExecutionResult(
            originalPlan,
            ExcavationTestSupport
                .outcomes(originalPlan, ExcavationTargetOutcome.COMPLETED, ExcavationTargetOutcome.COMPLETED),
            ExcavationSuspensionReason.UNLOADING_REQUIRED);

        ExcavationCheckpoint unloaded = ExcavationReducer.apply(start, unload)
            .getCheckpoint();
        ExcavationFrontier exact = originalPlan.getIntents()
            .get(1)
            .getNextFrontier();
        assertEquals(ExcavationSuspensionReason.UNLOADING_REQUIRED, unloaded.getSuspensionReason());
        assertEquals(exact, unloaded.getFrontier());
        assertEquals(
            2L,
            unloaded.getProgress()
                .getCompleted());

        ExcavationCheckpoint restored = ExcavationCheckpoint.restore(
            spec,
            unloaded.getTaskRevision(),
            unloaded.getActionEpoch(),
            ExcavationFrontier.restore(
                exact.getGeometryKey(),
                exact.getLayerY(),
                exact.getChunkX(),
                exact.getChunkZ(),
                exact.getBand(),
                exact.getOffset(),
                exact.isComplete()),
            unloaded.getProgress(),
            unloaded.getSuspensionReason());
        assertEquals(unloaded, restored);

        ExcavationCheckpoint resumed = restored.resume(6L, 8L);
        assertEquals(exact, resumed.getFrontier());
        ExcavationPlan resumedPlan = breakablePlan(spec, resumed, 3);
        assertEquals(
            originalPlan.getIntents()
                .get(2)
                .getPosition(),
            resumedPlan.getIntents()
                .get(0)
                .getPosition());

        ExcavationCheckpoint preempted = resumed.suspend(ExcavationSuspensionReason.PREEMPTED, 7L, 9L);
        ExcavationCheckpoint afterPreemption = preempted.resume(8L, 10L);
        ExcavationCheckpoint disconnected = afterPreemption.suspend(ExcavationSuspensionReason.DISCONNECTED, 9L, 11L);
        ExcavationCheckpoint afterReconnect = disconnected.resume(10L, 12L);
        assertEquals(exact, afterReconnect.getFrontier());
        assertEquals(resumed.getProgress(), afterReconnect.getProgress());

        ExcavationPlan repairPlan = breakablePlan(spec, afterReconnect, 2);
        ExcavationExecutionResult repair = new ExcavationExecutionResult(
            repairPlan,
            ExcavationTestSupport.outcomes(repairPlan, ExcavationTargetOutcome.COMPLETED),
            ExcavationSuspensionReason.REPAIR_REQUIRED);
        ExcavationCheckpoint repairing = ExcavationReducer.apply(afterReconnect, repair)
            .getCheckpoint();
        assertEquals(ExcavationSuspensionReason.REPAIR_REQUIRED, repairing.getSuspensionReason());
        assertEquals(
            repairPlan.getIntents()
                .get(0)
                .getNextFrontier(),
            repairing.getFrontier());

        assertRejected(afterReconnect, unload, ExcavationResultDisposition.STALE_TASK_REVISION);
    }

    @Test
    public void singlePositionCylinderCompletesWithZeroRemaining() {
        CylinderExcavationSpec spec = new CylinderExcavationSpec(-1, -8, -8, 0, 12, 12, ExcavationMode.CLEAN_VOLUME);
        ExcavationCheckpoint checkpoint = ExcavationCheckpoint.start(spec, 0L, 1L);
        ExcavationPlan plan = ExcavationPlanner.calculate(
            spec,
            ExcavationTestSupport
                .uniformWindow(spec, checkpoint.getFrontier(), 1, 0L, 1L, ExcavationBlockClassification.AIR),
            null);

        ExcavationCheckpoint completed = ExcavationReducer.apply(checkpoint, completed(plan, 1))
            .getCheckpoint();

        assertTrue(completed.isComplete());
        assertEquals(
            0L,
            completed.getProgress()
                .getRemaining());
        assertEquals(
            1L,
            completed.getProgress()
                .getCompleted());
    }

    @Test
    public void malformedOrDishonestExecutionResultsFailBeforeApplication() {
        CylinderExcavationSpec spec = managedSpec(1);
        ExcavationCheckpoint checkpoint = ExcavationCheckpoint.start(spec, 1L, 2L);
        ExcavationPlan protectedPlan = ExcavationPlanner.calculate(
            spec,
            ExcavationTestSupport.uniformWindow(
                spec,
                checkpoint.getFrontier(),
                1,
                1L,
                2L,
                ExcavationBlockClassification.PROTECTED_GRAVE),
            ManagedQuarryConfiguration.defaults());

        assertThrows(
            IllegalArgumentException.class,
            () -> new ExcavationExecutionResult(
                protectedPlan,
                ExcavationTestSupport.outcomes(protectedPlan, ExcavationTargetOutcome.COMPLETED),
                ExcavationSuspensionReason.NONE));

        ExcavationPlan breakable = breakablePlan(spec, checkpoint, 2);
        assertThrows(
            IllegalArgumentException.class,
            () -> new ExcavationExecutionResult(
                breakable,
                ExcavationTestSupport.outcomes(breakable, ExcavationTargetOutcome.COMPLETED),
                ExcavationSuspensionReason.NONE));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ExcavationExecutionResult(
                breakable,
                Collections.singletonList(
                    new ExcavationTargetResult(new BlockPosition(999, 5, 999), ExcavationTargetOutcome.COMPLETED)),
                ExcavationSuspensionReason.REPAIR_REQUIRED));
    }

    @Test
    public void persistedProgressCannotClaimAFrontierItDidNotReach() {
        CylinderExcavationSpec spec = managedSpec(2);
        ExcavationFrontier afterThree = CylinderExcavationGeometry
            .nextBatch(spec, CylinderExcavationGeometry.initialFrontier(spec), 3)
            .getNextFrontier();

        assertThrows(
            IllegalArgumentException.class,
            () -> ExcavationCheckpoint.restore(
                spec,
                1L,
                1L,
                afterThree,
                new ExcavationProgress(spec.getVolume(), 2L, 0L, 0L, 0L, 0L),
                ExcavationSuspensionReason.NONE));
    }

    @Test
    public void completedCheckpointCannotAlsoClaimSuspension() {
        CylinderExcavationSpec spec = new CylinderExcavationSpec(0, 0, 0, 0, 5, 5, ExcavationMode.CLEAN_VOLUME);
        ExcavationFrontier complete = CylinderExcavationGeometry
            .nextBatch(spec, CylinderExcavationGeometry.initialFrontier(spec), 1)
            .getNextFrontier();

        assertThrows(
            IllegalArgumentException.class,
            () -> ExcavationCheckpoint.restore(
                spec,
                2L,
                3L,
                complete,
                new ExcavationProgress(1L, 1L, 0L, 0L, 0L, 0L),
                ExcavationSuspensionReason.PREEMPTED));
    }

    private static CylinderExcavationSpec managedSpec(int radius) {
        return new CylinderExcavationSpec(0, -16, -16, radius, 5, 5, ExcavationMode.MANAGED_QUARRY);
    }

    private static ExcavationPlan breakablePlan(CylinderExcavationSpec spec, ExcavationCheckpoint checkpoint,
        int count) {
        return ExcavationPlanner.calculate(
            spec,
            ExcavationTestSupport.uniformWindow(
                spec,
                checkpoint.getFrontier(),
                count,
                checkpoint.getTaskRevision(),
                checkpoint.getActionEpoch(),
                ExcavationBlockClassification.BREAKABLE),
            spec.getMode() == ExcavationMode.MANAGED_QUARRY ? ManagedQuarryConfiguration.defaults() : null);
    }

    private static ExcavationExecutionResult completed(ExcavationPlan plan, int count) {
        ExcavationTargetOutcome[] outcomes = new ExcavationTargetOutcome[count];
        Arrays.fill(outcomes, ExcavationTargetOutcome.COMPLETED);
        return new ExcavationExecutionResult(
            plan,
            ExcavationTestSupport.outcomes(plan, outcomes),
            ExcavationSuspensionReason.NONE);
    }

    private static void assertRejected(ExcavationCheckpoint checkpoint, ExcavationExecutionResult result,
        ExcavationResultDisposition expected) {
        ExcavationResultApplication application = ExcavationReducer.apply(checkpoint, result);
        assertFalse(application.wasApplied());
        assertEquals(expected, application.getDisposition());
        assertSame(checkpoint, application.getCheckpoint());
    }
}
