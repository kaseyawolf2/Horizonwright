package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable trigger and task template for one recurring scheduler rule. */
public final class ScheduleRule {

    public static final long WORLD_DAY_TICKS = 24_000L;

    private final String id;
    private final ScheduledTaskSpec task;
    private final ScheduleTrigger trigger;
    private final long intervalMillis;
    private final long initialDelayMillis;
    private final int windowStartTick;
    private final int windowEndTick;
    private final Set<String> requiredConditions;
    private final int relativeOrder;
    private final boolean catchUpAfterReconnect;

    private ScheduleRule(String id, ScheduledTaskSpec task, ScheduleTrigger trigger, long intervalMillis,
        long initialDelayMillis, int windowStartTick, int windowEndTick, Set<String> requiredConditions,
        int relativeOrder, boolean catchUpAfterReconnect) {
        this.id = requireText(id, "id");
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("trigger must not be null");
        }
        if (trigger == ScheduleTrigger.CONNECTED_INTERVAL) {
            if (intervalMillis < 1L) {
                throw new IllegalArgumentException("connected interval must be positive");
            }
            if (initialDelayMillis < 0L) {
                throw new IllegalArgumentException("initial delay must not be negative");
            }
        }
        if (trigger == ScheduleTrigger.WORLD_TIME_WINDOW) {
            if (windowStartTick < 0 || windowStartTick >= WORLD_DAY_TICKS
                || windowEndTick < 0
                || windowEndTick > WORLD_DAY_TICKS
                || windowStartTick == windowEndTick) {
                throw new IllegalArgumentException("world window must be a non-empty range within one day");
            }
        }
        this.task = task;
        this.trigger = trigger;
        this.intervalMillis = intervalMillis;
        this.initialDelayMillis = initialDelayMillis;
        this.windowStartTick = windowStartTick;
        this.windowEndTick = windowEndTick;
        this.requiredConditions = immutableConditions(requiredConditions);
        this.relativeOrder = relativeOrder;
        this.catchUpAfterReconnect = catchUpAfterReconnect;
    }

    public static ScheduleRule connectedInterval(String id, ScheduledTaskSpec task, long intervalMillis,
        long initialDelayMillis, Set<String> requiredConditions, int relativeOrder) {
        return new ScheduleRule(
            id,
            task,
            ScheduleTrigger.CONNECTED_INTERVAL,
            intervalMillis,
            initialDelayMillis,
            0,
            0,
            requiredConditions,
            relativeOrder,
            false);
    }

    public static ScheduleRule worldTimeWindow(String id, ScheduledTaskSpec task, int windowStartTick,
        int windowEndTick, Set<String> requiredConditions, int relativeOrder, boolean catchUpAfterReconnect) {
        return new ScheduleRule(
            id,
            task,
            ScheduleTrigger.WORLD_TIME_WINDOW,
            0L,
            0L,
            windowStartTick,
            windowEndTick,
            requiredConditions,
            relativeOrder,
            catchUpAfterReconnect);
    }

    public static ScheduleRule idle(String id, ScheduledTaskSpec task, Set<String> requiredConditions,
        int relativeOrder) {
        return new ScheduleRule(id, task, ScheduleTrigger.IDLE, 0L, 0L, 0, 0, requiredConditions, relativeOrder, false);
    }

    public String getId() {
        return id;
    }

    public ScheduledTaskSpec getTask() {
        return task;
    }

    public ScheduleTrigger getTrigger() {
        return trigger;
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    public int getWindowStartTick() {
        return windowStartTick;
    }

    public int getWindowEndTick() {
        return windowEndTick;
    }

    public Set<String> getRequiredConditions() {
        return requiredConditions;
    }

    public int getRelativeOrder() {
        return relativeOrder;
    }

    public boolean isCatchUpAfterReconnect() {
        return catchUpAfterReconnect;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScheduleRule)) {
            return false;
        }
        ScheduleRule that = (ScheduleRule) other;
        return intervalMillis == that.intervalMillis && initialDelayMillis == that.initialDelayMillis
            && windowStartTick == that.windowStartTick
            && windowEndTick == that.windowEndTick
            && relativeOrder == that.relativeOrder
            && catchUpAfterReconnect == that.catchUpAfterReconnect
            && id.equals(that.id)
            && task.equals(that.task)
            && trigger == that.trigger
            && requiredConditions.equals(that.requiredConditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            task,
            trigger,
            intervalMillis,
            initialDelayMillis,
            windowStartTick,
            windowEndTick,
            requiredConditions,
            relativeOrder,
            catchUpAfterReconnect);
    }

    private static Set<String> immutableConditions(Set<String> source) {
        if (source == null) {
            throw new IllegalArgumentException("requiredConditions must not be null");
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String condition : source) {
            copy.add(requireText(condition, "condition"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
