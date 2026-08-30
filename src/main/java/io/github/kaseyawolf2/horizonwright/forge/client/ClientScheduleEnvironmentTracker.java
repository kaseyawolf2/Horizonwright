package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;

/** Converts client connection edges into immutable scheduler environments. */
public final class ClientScheduleEnvironmentTracker {

    private boolean connectedBefore;
    private boolean connectedLastTick;

    public synchronized ScheduleEnvironment observe(boolean connected, long worldTimeTicks, Set<String> conditions) {
        if (!connected) {
            connectedLastTick = false;
            return ScheduleEnvironment.disconnected();
        }
        boolean reconnected = connectedBefore && !connectedLastTick;
        connectedBefore = true;
        connectedLastTick = true;
        return reconnected ? ScheduleEnvironment.reconnected(worldTimeTicks, conditions)
            : ScheduleEnvironment.connected(worldTimeTicks, conditions);
    }
}
