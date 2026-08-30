package io.github.kaseyawolf2.horizonwright.core.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class TaskResumeCandidatesTest {

    @Test
    public void selectsTheOnlySuspendedMovementTaskForIdFreeResume() {
        TaskSnapshot queued = task("queued", TaskState.QUEUED, TaskSuspensionReason.NONE);
        TaskSnapshot movement = task("goto-7", TaskState.SUSPENDED, TaskSuspensionReason.OPERATOR_PAUSE);

        TaskResumeCandidates selection = TaskResumeCandidates.from(Arrays.asList(queued, movement));

        assertEquals(1, selection.size());
        assertEquals(
            "goto-7",
            selection.onlyCandidate()
                .get()
                .getSpec()
                .getId());
        assertFalse(selection.isAmbiguous());
    }

    @Test
    public void blockedTasksAreEligibleButSeveralCandidatesRequireAnExplicitChoice() {
        TaskSnapshot first = task("goto-1", TaskState.BLOCKED, TaskSuspensionReason.NONE);
        TaskSnapshot second = task("excavate", TaskState.SUSPENDED, TaskSuspensionReason.OPERATOR_PAUSE);

        TaskResumeCandidates selection = TaskResumeCandidates.from(Arrays.asList(first, second));

        assertEquals(2, selection.size());
        assertTrue(selection.isAmbiguous());
        assertFalse(
            selection.onlyCandidate()
                .isPresent());
        assertEquals(
            "goto-1",
            selection.asList()
                .get(0)
                .getSpec()
                .getId());
        assertEquals(
            "excavate",
            selection.asList()
                .get(1)
                .getSpec()
                .getId());
    }

    @Test
    public void cancellationAndTerminalWorkAreNeverOfferedForResume() {
        TaskResumeCandidates selection = TaskResumeCandidates.from(
            Arrays.asList(
                task("cancelling", TaskState.SUSPENDING, TaskSuspensionReason.CANCELLATION),
                task("done", TaskState.COMPLETED, TaskSuspensionReason.NONE),
                task("failed", TaskState.FAILED, TaskSuspensionReason.NONE),
                task("cancelled", TaskState.CANCELLED, TaskSuspensionReason.CANCELLATION)));

        assertTrue(selection.isEmpty());
        assertFalse(
            selection.onlyCandidate()
                .isPresent());
    }

    @Test
    public void pendingOperatorPauseCanBeWithdrawnWithResume() {
        TaskResumeCandidates selection = TaskResumeCandidates
            .from(Collections.singletonList(task("moving", TaskState.SUSPENDING, TaskSuspensionReason.OPERATOR_PAUSE)));

        assertTrue(
            selection.onlyCandidate()
                .isPresent());
    }

    private static TaskSnapshot task(String id, TaskState state, TaskSuspensionReason reason) {
        return new TaskSnapshot(
            TaskSpec.of(id, "test", id, TaskLane.MANUAL),
            state,
            TaskCheckpoint.empty(),
            1L,
            0,
            0L,
            reason,
            null,
            state.isTerminal() ? -1 : 0,
            0L,
            state.name());
    }
}
