package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Immutable, persistence-neutral state for the deterministic task scheduler. */
public final class SchedulerSnapshot {

    private final long connectedElapsedMillis;
    private final long lastWorldTimeTicks;
    private final boolean connectedAtSnapshot;
    private final List<ScheduleSnapshot> schedules;

    public SchedulerSnapshot(long connectedElapsedMillis, long lastWorldTimeTicks, boolean connectedAtSnapshot,
        List<ScheduleSnapshot> schedules) {
        if (connectedElapsedMillis < 0L) {
            throw new IllegalArgumentException("connectedElapsedMillis must not be negative");
        }
        if (lastWorldTimeTicks < ScheduleEnvironment.UNKNOWN_WORLD_TIME) {
            throw new IllegalArgumentException("lastWorldTimeTicks is invalid");
        }
        if (schedules == null) {
            throw new IllegalArgumentException("schedules must not be null");
        }
        List<ScheduleSnapshot> copy = new ArrayList<>(schedules.size());
        Set<String> ids = new LinkedHashSet<>();
        for (ScheduleSnapshot schedule : schedules) {
            if (schedule == null) {
                throw new IllegalArgumentException("schedules must not contain null values");
            }
            if (!ids.add(
                schedule.getRule()
                    .getId())) {
                throw new IllegalArgumentException(
                    "duplicate schedule: " + schedule.getRule()
                        .getId());
            }
            copy.add(schedule);
        }
        this.connectedElapsedMillis = connectedElapsedMillis;
        this.lastWorldTimeTicks = lastWorldTimeTicks;
        this.connectedAtSnapshot = connectedAtSnapshot;
        this.schedules = Collections.unmodifiableList(copy);
    }

    public static SchedulerSnapshot empty() {
        return new SchedulerSnapshot(
            0L,
            ScheduleEnvironment.UNKNOWN_WORLD_TIME,
            false,
            Collections.<ScheduleSnapshot>emptyList());
    }

    public long getConnectedElapsedMillis() {
        return connectedElapsedMillis;
    }

    public long getLastWorldTimeTicks() {
        return lastWorldTimeTicks;
    }

    public boolean wasConnectedAtSnapshot() {
        return connectedAtSnapshot;
    }

    public List<ScheduleSnapshot> getSchedules() {
        return schedules;
    }

    public Optional<ScheduleSnapshot> findSchedule(String scheduleId) {
        if (scheduleId == null) {
            return Optional.empty();
        }
        String normalized = scheduleId.trim();
        for (ScheduleSnapshot schedule : schedules) {
            if (schedule.getRule()
                .getId()
                .equals(normalized)) {
                return Optional.of(schedule);
            }
        }
        return Optional.empty();
    }
}
