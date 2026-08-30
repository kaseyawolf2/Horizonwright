package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Immutable selection of tasks that an operator can meaningfully resume. */
public final class TaskResumeCandidates {

    private final List<TaskSnapshot> candidates;

    private TaskResumeCandidates(List<TaskSnapshot> candidates) {
        this.candidates = Collections.unmodifiableList(candidates);
    }

    public static TaskResumeCandidates from(Iterable<TaskSnapshot> tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("tasks must not be null");
        }
        List<TaskSnapshot> candidates = new ArrayList<>();
        for (TaskSnapshot task : tasks) {
            if (task == null) {
                throw new IllegalArgumentException("tasks must not contain null");
            }
            if (isResumeEligible(task)) {
                candidates.add(task);
            }
        }
        return new TaskResumeCandidates(candidates);
    }

    public List<TaskSnapshot> asList() {
        return candidates;
    }

    public int size() {
        return candidates.size();
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    public boolean isAmbiguous() {
        return candidates.size() > 1;
    }

    public Optional<TaskSnapshot> onlyCandidate() {
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.<TaskSnapshot>empty();
    }

    private static boolean isResumeEligible(TaskSnapshot task) {
        TaskState state = task.getState();
        if (state == TaskState.SUSPENDED || state == TaskState.BLOCKED) {
            return true;
        }
        return state == TaskState.SUSPENDING && task.getSuspensionReason() != TaskSuspensionReason.CANCELLATION;
    }
}
