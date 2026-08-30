package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Optional;

/** Immutable persisted state for one scheduler rule. */
public final class ScheduleSnapshot {

    public static final long NO_WORLD_OCCURRENCE = Long.MIN_VALUE;

    private final ScheduleRule rule;
    private final ScheduleState state;
    private final long sequence;
    private final long nextConnectedDueMillis;
    private final long lastWorldOccurrence;
    private final boolean idleLatched;
    private final String lastTaskId;
    private final long totalRuns;
    private final long catchUpRuns;

    public ScheduleSnapshot(ScheduleRule rule, ScheduleState state, long sequence, long nextConnectedDueMillis,
        long lastWorldOccurrence, boolean idleLatched, String lastTaskId, long totalRuns, long catchUpRuns) {
        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        if (nextConnectedDueMillis < 0L) {
            throw new IllegalArgumentException("nextConnectedDueMillis must not be negative");
        }
        if (totalRuns < 0L || catchUpRuns < 0L || catchUpRuns > totalRuns) {
            throw new IllegalArgumentException("run counters are inconsistent");
        }
        if (lastTaskId != null && lastTaskId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("lastTaskId must not be blank");
        }
        if (sequence == 0L && lastTaskId != null) {
            throw new IllegalArgumentException("a rule without a sequence cannot have a last task");
        }
        if (sequence > 0L) {
            String expectedTaskId = "schedule[" + rule.getId() + "]#" + sequence;
            if (!expectedTaskId.equals(lastTaskId)) {
                throw new IllegalArgumentException("lastTaskId does not match the schedule sequence");
            }
        }
        if (sequence != totalRuns) {
            throw new IllegalArgumentException("sequence and totalRuns must match");
        }
        this.rule = rule;
        this.state = state;
        this.sequence = sequence;
        this.nextConnectedDueMillis = nextConnectedDueMillis;
        this.lastWorldOccurrence = lastWorldOccurrence;
        this.idleLatched = idleLatched;
        this.lastTaskId = lastTaskId == null ? null : lastTaskId.trim();
        this.totalRuns = totalRuns;
        this.catchUpRuns = catchUpRuns;
    }

    public ScheduleRule getRule() {
        return rule;
    }

    public ScheduleState getState() {
        return state;
    }

    public long getSequence() {
        return sequence;
    }

    public long getNextConnectedDueMillis() {
        return nextConnectedDueMillis;
    }

    public long getLastWorldOccurrence() {
        return lastWorldOccurrence;
    }

    public boolean isIdleLatched() {
        return idleLatched;
    }

    public Optional<String> getLastTaskId() {
        return Optional.ofNullable(lastTaskId);
    }

    public long getTotalRuns() {
        return totalRuns;
    }

    public long getCatchUpRuns() {
        return catchUpRuns;
    }
}
