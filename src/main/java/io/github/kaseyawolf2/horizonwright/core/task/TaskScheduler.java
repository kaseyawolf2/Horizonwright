package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;

/**
 * Deterministic, persistence-neutral scheduler for task templates.
 *
 * <p>
 * Connected intervals advance only over observed connected time. Each evaluation consumes at
 * most one occurrence per rule, even if multiple intervals elapsed. Reconnect catch-up similarly
 * creates at most one task for a missed world-time rule.
 */
public final class TaskScheduler {

    private final Map<String, ScheduleRecord> schedules = new LinkedHashMap<>();

    private long connectedElapsedMillis;
    private long lastObservedMillis = Long.MIN_VALUE;
    private long lastWorldTimeTicks = ScheduleEnvironment.UNKNOWN_WORLD_TIME;
    private boolean previouslyConnected;
    private boolean reconnectPendingAfterRestore;
    private boolean restorePerformed;

    public ScheduleSnapshot submit(ScheduleRule rule) {
        requireRule(rule);
        if (schedules.containsKey(rule.getId())) {
            throw new IllegalArgumentException("schedule already exists: " + rule.getId());
        }
        ScheduleRecord record = new ScheduleRecord(rule, safeAdd(connectedElapsedMillis, rule.getInitialDelayMillis()));
        schedules.put(rule.getId(), record);
        trace("submitted", record, "connectedElapsed", connectedElapsedMillis);
        return record.snapshot();
    }

    public ScheduleSnapshot update(ScheduleRule replacement) {
        requireRule(replacement);
        ScheduleRecord record = requireSchedule(replacement.getId());
        if (record.state == ScheduleState.CANCELLED) {
            throw new IllegalStateException("cancelled schedules cannot be edited: " + replacement.getId());
        }
        record.rule = replacement;
        record.nextConnectedDueMillis = safeAdd(connectedElapsedMillis, replacement.getInitialDelayMillis());
        record.lastWorldOccurrence = ScheduleSnapshot.NO_WORLD_OCCURRENCE;
        record.idleLatched = false;
        trace("updated", record, "connectedElapsed", connectedElapsedMillis);
        return record.snapshot();
    }

    public ScheduleSnapshot pause(String scheduleId) {
        ScheduleRecord record = requireSchedule(scheduleId);
        if (record.state == ScheduleState.CANCELLED) {
            throw new IllegalStateException("cancelled schedules cannot be paused: " + scheduleId);
        }
        record.state = ScheduleState.PAUSED;
        record.idleLatched = false;
        trace("paused", record);
        return record.snapshot();
    }

    public ScheduleSnapshot resume(String scheduleId) {
        ScheduleRecord record = requireSchedule(scheduleId);
        if (record.state == ScheduleState.CANCELLED) {
            throw new IllegalStateException("cancelled schedules cannot be resumed: " + scheduleId);
        }
        if (record.state == ScheduleState.PAUSED) {
            record.state = ScheduleState.ACTIVE;
            if (record.rule.getTrigger() == ScheduleTrigger.CONNECTED_INTERVAL) {
                record.nextConnectedDueMillis = safeAdd(connectedElapsedMillis, record.rule.getIntervalMillis());
            }
            record.idleLatched = false;
        }
        trace("resumed", record, "connectedElapsed", connectedElapsedMillis);
        return record.snapshot();
    }

    public ScheduleSnapshot cancel(String scheduleId) {
        ScheduleRecord record = requireSchedule(scheduleId);
        record.state = ScheduleState.CANCELLED;
        record.idleLatched = false;
        trace("cancelled", record);
        return record.snapshot();
    }

    public Optional<ScheduleSnapshot> inspect(String scheduleId) {
        if (scheduleId == null || scheduleId.trim()
            .isEmpty()) {
            return Optional.empty();
        }
        ScheduleRecord record = schedules.get(scheduleId.trim());
        return record == null ? Optional.<ScheduleSnapshot>empty() : Optional.of(record.snapshot());
    }

    public SchedulerSnapshot snapshot() {
        List<ScheduleSnapshot> snapshots = new ArrayList<>(schedules.size());
        for (ScheduleRecord record : schedules.values()) {
            snapshots.add(record.snapshot());
        }
        return new SchedulerSnapshot(connectedElapsedMillis, lastWorldTimeTicks, previouslyConnected, snapshots);
    }

    /** Restores into a new, empty scheduler without counting process downtime as connected time. */
    public void restore(SchedulerSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (!isPristine()) {
            throw new IllegalStateException("scheduler restore requires a new, empty scheduler");
        }
        restorePerformed = true;
        connectedElapsedMillis = snapshot.getConnectedElapsedMillis();
        lastWorldTimeTicks = snapshot.getLastWorldTimeTicks();
        previouslyConnected = snapshot.wasConnectedAtSnapshot();
        reconnectPendingAfterRestore = snapshot.wasConnectedAtSnapshot();
        for (ScheduleSnapshot saved : snapshot.getSchedules()) {
            ScheduleRecord record = new ScheduleRecord(saved);
            schedules.put(record.rule.getId(), record);
            trace("restored", record, "connectedElapsed", connectedElapsedMillis, "lastWorldTime", lastWorldTimeTicks);
        }
    }

    boolean isPristine() {
        return !restorePerformed && schedules.isEmpty()
            && lastObservedMillis == Long.MIN_VALUE
            && connectedElapsedMillis == 0L
            && lastWorldTimeTicks == ScheduleEnvironment.UNKNOWN_WORLD_TIME
            && !previouslyConnected
            && !reconnectPendingAfterRestore;
    }

    List<ScheduledTaskRequest> evaluate(long nowMillis, ScheduleEnvironment environment, boolean controllerIdle,
        Set<String> occupiedScheduleIds) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("nowMillis must not be negative");
        }
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        if (occupiedScheduleIds == null) {
            throw new IllegalArgumentException("occupiedScheduleIds must not be null");
        }
        if (lastObservedMillis != Long.MIN_VALUE && nowMillis < lastObservedMillis) {
            throw new IllegalStateException("scheduler clock moved backwards");
        }
        if (lastObservedMillis != Long.MIN_VALUE && previouslyConnected) {
            connectedElapsedMillis = safeAdd(connectedElapsedMillis, nowMillis - lastObservedMillis);
        }

        boolean restoreReconnect = reconnectPendingAfterRestore && environment.isConnected();
        boolean reconnectObserved = environment.isReconnected() || restoreReconnect;
        long previousWorldTime = lastWorldTimeTicks;
        DevelopmentTrace.event(
            "scheduler",
            "evaluate",
            "now",
            nowMillis,
            "connectedElapsed",
            connectedElapsedMillis,
            "connected",
            environment.isConnected(),
            "reconnected",
            reconnectObserved,
            "worldTime",
            environment.getWorldTimeTicks(),
            "controllerIdle",
            controllerIdle,
            "occupied",
            occupiedScheduleIds,
            "schedules",
            schedules.size());
        List<DueRule> due = new ArrayList<>();
        for (ScheduleRecord record : schedules.values()) {
            if (record.state != ScheduleState.ACTIVE) {
                record.idleLatched = false;
                continue;
            }
            boolean conditionsMet = environment.getConditions()
                .containsAll(record.rule.getRequiredConditions());
            trace(
                "considered",
                record,
                "conditionsMet",
                conditionsMet,
                "occupied",
                occupiedScheduleIds.contains(record.rule.getId()),
                "worldTime",
                environment.getWorldTimeTicks(),
                "connectedElapsed",
                connectedElapsedMillis);
            switch (record.rule.getTrigger()) {
                case CONNECTED_INTERVAL:
                    evaluateConnectedInterval(
                        record,
                        environment,
                        reconnectObserved,
                        conditionsMet,
                        occupiedScheduleIds,
                        due);
                    break;
                case WORLD_TIME_WINDOW:
                    evaluateWorldWindow(
                        record,
                        environment,
                        reconnectObserved,
                        previousWorldTime,
                        conditionsMet,
                        occupiedScheduleIds,
                        due);
                    break;
                case IDLE:
                    evaluateIdle(record, environment, controllerIdle, conditionsMet, occupiedScheduleIds, due);
                    break;
                default:
                    throw new IllegalStateException("unhandled schedule trigger: " + record.rule.getTrigger());
            }
        }

        Collections.sort(due, new Comparator<DueRule>() {

            @Override
            public int compare(DueRule left, DueRule right) {
                int lane = Integer.compare(
                    left.record.rule.getTask()
                        .getLane()
                        .ordinal(),
                    right.record.rule.getTask()
                        .getLane()
                        .ordinal());
                if (lane != 0) {
                    return lane;
                }
                int relative = Integer
                    .compare(left.record.rule.getRelativeOrder(), right.record.rule.getRelativeOrder());
                return relative != 0 ? relative
                    : left.record.rule.getId()
                        .compareTo(right.record.rule.getId());
            }
        });

        List<ScheduledTaskRequest> requests = new ArrayList<>(due.size());
        for (DueRule candidate : due) {
            ScheduleRecord record = candidate.record;
            if (record.sequence == Long.MAX_VALUE) {
                throw new IllegalStateException("schedule sequence exhausted: " + record.rule.getId());
            }
            record.sequence++;
            record.totalRuns++;
            if (candidate.catchUp) {
                record.catchUpRuns++;
            }
            String taskId = "schedule[" + record.rule.getId() + "]#" + record.sequence;
            record.lastTaskId = taskId;
            trace("triggered", record, "task", taskId, "catchUp", candidate.catchUp);
            requests.add(
                new ScheduledTaskRequest(
                    record.rule.getId(),
                    record.rule.getTask()
                        .instantiate(taskId),
                    candidate.catchUp,
                    record.rule.getRelativeOrder()));
        }

        lastObservedMillis = nowMillis;
        previouslyConnected = environment.isConnected();
        if (environment.isConnected()) {
            reconnectPendingAfterRestore = false;
        }
        if (environment.hasWorldTime()) {
            lastWorldTimeTicks = environment.getWorldTimeTicks();
        }
        return Collections.unmodifiableList(requests);
    }

    private static void trace(String event, ScheduleRecord record, Object... extraFields) {
        Object[] fields = new Object[14 + extraFields.length];
        fields[0] = "schedule";
        fields[1] = record.rule.getId();
        fields[2] = "trigger";
        fields[3] = record.rule.getTrigger();
        fields[4] = "state";
        fields[5] = record.state;
        fields[6] = "taskType";
        fields[7] = record.rule.getTask()
            .getType();
        fields[8] = "sequence";
        fields[9] = record.sequence;
        fields[10] = "totalRuns";
        fields[11] = record.totalRuns;
        fields[12] = "nextConnectedDue";
        fields[13] = record.nextConnectedDueMillis;
        System.arraycopy(extraFields, 0, fields, 14, extraFields.length);
        DevelopmentTrace.event("scheduler", event, fields);
    }

    private void evaluateConnectedInterval(ScheduleRecord record, ScheduleEnvironment environment,
        boolean reconnectObserved, boolean conditionsMet, Set<String> occupiedScheduleIds, List<DueRule> due) {
        if (!environment.isConnected() || connectedElapsedMillis < record.nextConnectedDueMillis) {
            return;
        }
        advanceConnectedDue(record);
        if (conditionsMet && !occupiedScheduleIds.contains(record.rule.getId())) {
            due.add(new DueRule(record, reconnectObserved));
        }
    }

    private void evaluateWorldWindow(ScheduleRecord record, ScheduleEnvironment environment, boolean reconnectObserved,
        long previousWorldTime, boolean conditionsMet, Set<String> occupiedScheduleIds, List<DueRule> due) {
        if (!environment.isConnected() || !environment.hasWorldTime()) {
            return;
        }
        long worldTime = environment.getWorldTimeTicks();
        long currentOccurrence = occurrenceContaining(record.rule, worldTime);
        boolean currentWindowDue = currentOccurrence != ScheduleSnapshot.NO_WORLD_OCCURRENCE
            && currentOccurrence > record.lastWorldOccurrence;
        if (currentWindowDue && conditionsMet) {
            record.lastWorldOccurrence = currentOccurrence;
            if (!occupiedScheduleIds.contains(record.rule.getId())) {
                due.add(new DueRule(record, false));
            }
            return;
        }

        if (!reconnectObserved || !record.rule.isCatchUpAfterReconnect()
            || !conditionsMet
            || previousWorldTime == ScheduleEnvironment.UNKNOWN_WORLD_TIME
            || worldTime <= previousWorldTime) {
            return;
        }
        long missedOccurrence = latestOccurrenceStartingBy(record.rule, worldTime);
        if (missedOccurrence <= record.lastWorldOccurrence) {
            return;
        }
        long missedStart = occurrenceStart(record.rule, missedOccurrence);
        if (missedStart <= previousWorldTime) {
            return;
        }
        record.lastWorldOccurrence = missedOccurrence;
        if (!occupiedScheduleIds.contains(record.rule.getId())) {
            due.add(new DueRule(record, true));
        }
    }

    private static void evaluateIdle(ScheduleRecord record, ScheduleEnvironment environment, boolean controllerIdle,
        boolean conditionsMet, Set<String> occupiedScheduleIds, List<DueRule> due) {
        boolean eligible = environment.isConnected() && controllerIdle && conditionsMet;
        if (!eligible) {
            record.idleLatched = false;
            return;
        }
        if (!record.idleLatched) {
            record.idleLatched = true;
            if (!occupiedScheduleIds.contains(record.rule.getId())) {
                due.add(new DueRule(record, false));
            }
        }
    }

    private void advanceConnectedDue(ScheduleRecord record) {
        long interval = record.rule.getIntervalMillis();
        long elapsedPastDue = connectedElapsedMillis - record.nextConnectedDueMillis;
        long completeIntervals = elapsedPastDue / interval;
        long advance = safeMultiply(completeIntervals, interval);
        record.nextConnectedDueMillis = safeAdd(record.nextConnectedDueMillis, advance);
        record.nextConnectedDueMillis = safeAdd(record.nextConnectedDueMillis, interval);
    }

    private static long occurrenceContaining(ScheduleRule rule, long worldTime) {
        long day = worldTime / ScheduleRule.WORLD_DAY_TICKS;
        int timeOfDay = (int) (worldTime % ScheduleRule.WORLD_DAY_TICKS);
        int start = rule.getWindowStartTick();
        int end = rule.getWindowEndTick();
        if (start < end) {
            return timeOfDay >= start && timeOfDay < end ? day : ScheduleSnapshot.NO_WORLD_OCCURRENCE;
        }
        if (timeOfDay >= start) {
            return day;
        }
        return timeOfDay < end ? day - 1L : ScheduleSnapshot.NO_WORLD_OCCURRENCE;
    }

    private static long latestOccurrenceStartingBy(ScheduleRule rule, long worldTime) {
        long day = worldTime / ScheduleRule.WORLD_DAY_TICKS;
        if (occurrenceStart(rule, day) > worldTime) {
            day--;
        }
        return day;
    }

    private static long occurrenceStart(ScheduleRule rule, long occurrence) {
        if (occurrence < 0L) {
            return occurrence * ScheduleRule.WORLD_DAY_TICKS + rule.getWindowStartTick();
        }
        return safeAdd(safeMultiply(occurrence, ScheduleRule.WORLD_DAY_TICKS), rule.getWindowStartTick());
    }

    private ScheduleRecord requireSchedule(String scheduleId) {
        if (scheduleId == null || scheduleId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("scheduleId must not be blank");
        }
        ScheduleRecord record = schedules.get(scheduleId.trim());
        if (record == null) {
            throw new IllegalArgumentException("unknown schedule: " + scheduleId.trim());
        }
        return record;
    }

    private static void requireRule(ScheduleRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }
    }

    private static long safeAdd(long left, long right) {
        if (left < 0L || right < 0L) {
            throw new IllegalArgumentException("scheduler durations must not be negative");
        }
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static long safeMultiply(long left, long right) {
        if (left < 0L || right < 0L) {
            throw new IllegalArgumentException("scheduler durations must not be negative");
        }
        if (left == 0L || right == 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static final class DueRule {

        private final ScheduleRecord record;
        private final boolean catchUp;

        private DueRule(ScheduleRecord record, boolean catchUp) {
            this.record = record;
            this.catchUp = catchUp;
        }
    }

    private static final class ScheduleRecord {

        private ScheduleRule rule;
        private ScheduleState state = ScheduleState.ACTIVE;
        private long sequence;
        private long nextConnectedDueMillis;
        private long lastWorldOccurrence = ScheduleSnapshot.NO_WORLD_OCCURRENCE;
        private boolean idleLatched;
        private String lastTaskId;
        private long totalRuns;
        private long catchUpRuns;

        private ScheduleRecord(ScheduleRule rule, long nextConnectedDueMillis) {
            this.rule = rule;
            this.nextConnectedDueMillis = nextConnectedDueMillis;
        }

        private ScheduleRecord(ScheduleSnapshot snapshot) {
            this.rule = snapshot.getRule();
            this.state = snapshot.getState();
            this.sequence = snapshot.getSequence();
            this.nextConnectedDueMillis = snapshot.getNextConnectedDueMillis();
            this.lastWorldOccurrence = snapshot.getLastWorldOccurrence();
            this.idleLatched = snapshot.isIdleLatched();
            this.lastTaskId = snapshot.getLastTaskId()
                .orElse(null);
            this.totalRuns = snapshot.getTotalRuns();
            this.catchUpRuns = snapshot.getCatchUpRuns();
        }

        private ScheduleSnapshot snapshot() {
            return new ScheduleSnapshot(
                rule,
                state,
                sequence,
                nextConnectedDueMillis,
                lastWorldOccurrence,
                idleLatched,
                lastTaskId,
                totalRuns,
                catchUpRuns);
        }
    }
}
