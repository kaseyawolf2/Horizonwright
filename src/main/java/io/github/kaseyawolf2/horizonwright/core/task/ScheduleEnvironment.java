package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable scheduler input supplied by the runtime at a controller tick boundary. */
public final class ScheduleEnvironment {

    public static final long UNKNOWN_WORLD_TIME = -1L;

    private static final ScheduleEnvironment DISCONNECTED = new ScheduleEnvironment(
        false,
        false,
        UNKNOWN_WORLD_TIME,
        Collections.<String>emptySet());

    private final boolean connected;
    private final boolean reconnected;
    private final long worldTimeTicks;
    private final Set<String> conditions;
    private java.util.Map<String, Integer> windowLeadTicks = java.util.Collections.emptyMap();

    public ScheduleEnvironment(boolean connected, boolean reconnected, long worldTimeTicks, Set<String> conditions) {
        if (reconnected && !connected) {
            throw new IllegalArgumentException("reconnected requires a connected environment");
        }
        if (worldTimeTicks < UNKNOWN_WORLD_TIME) {
            throw new IllegalArgumentException("worldTimeTicks must be non-negative or UNKNOWN_WORLD_TIME");
        }
        if (!connected && worldTimeTicks != UNKNOWN_WORLD_TIME) {
            throw new IllegalArgumentException("a disconnected environment cannot expose world time");
        }
        if (conditions == null) {
            throw new IllegalArgumentException("conditions must not be null");
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String condition : conditions) {
            if (condition == null || condition.trim()
                .isEmpty()) {
                throw new IllegalArgumentException("conditions must not contain blank values");
            }
            copy.add(condition.trim());
        }
        this.connected = connected;
        this.reconnected = reconnected;
        this.worldTimeTicks = worldTimeTicks;
        this.conditions = Collections.unmodifiableSet(copy);
    }

    public static ScheduleEnvironment disconnected() {
        return DISCONNECTED;
    }

    public static ScheduleEnvironment connected(long worldTimeTicks, Set<String> conditions) {
        return new ScheduleEnvironment(true, false, worldTimeTicks, conditions);
    }

    public static ScheduleEnvironment reconnected(long worldTimeTicks, Set<String> conditions) {
        return new ScheduleEnvironment(true, true, worldTimeTicks, conditions);
    }

    public ScheduleEnvironment withWindowLeadTicks(java.util.Map<String, Integer> leads) {
        ScheduleEnvironment copy = new ScheduleEnvironment(connected, reconnected, worldTimeTicks, conditions);
        java.util.Map<String, Integer> validated = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Integer> entry : leads.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 6000)
                throw new IllegalArgumentException("Window lead must be between 0 and 6000 ticks");
            validated.put(entry.getKey(), entry.getValue());
        }
        copy.windowLeadTicks = java.util.Collections.unmodifiableMap(validated);
        return copy;
    }

    public int getWindowLeadTicks(String scheduleId) {
        return windowLeadTicks.getOrDefault(scheduleId, 0);
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isReconnected() {
        return reconnected;
    }

    public boolean hasWorldTime() {
        return worldTimeTicks != UNKNOWN_WORLD_TIME;
    }

    public long getWorldTimeTicks() {
        return worldTimeTicks;
    }

    public Set<String> getConditions() {
        return conditions;
    }
}
