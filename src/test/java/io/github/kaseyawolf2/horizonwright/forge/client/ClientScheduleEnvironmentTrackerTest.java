package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;

public class ClientScheduleEnvironmentTrackerTest {

    @Test
    public void distinguishesInitialConnectionFromReconnect() {
        ClientScheduleEnvironmentTracker tracker = new ClientScheduleEnvironmentTracker();

        ScheduleEnvironment disconnected = tracker
            .observe(false, ScheduleEnvironment.UNKNOWN_WORLD_TIME, Collections.<String>emptySet());
        assertFalse(disconnected.isConnected());

        ScheduleEnvironment initial = tracker.observe(true, 100L, Collections.singleton("safe"));
        assertTrue(initial.isConnected());
        assertFalse(initial.isReconnected());
        assertTrue(
            initial.getConditions()
                .contains("safe"));

        assertFalse(
            tracker.observe(true, 101L, Collections.<String>emptySet())
                .isReconnected());
        tracker.observe(false, ScheduleEnvironment.UNKNOWN_WORLD_TIME, Collections.<String>emptySet());

        ScheduleEnvironment restored = tracker.observe(true, 500L, Collections.<String>emptySet());
        assertTrue(restored.isConnected());
        assertTrue(restored.isReconnected());
    }

    @Test(expected = IllegalArgumentException.class)
    public void delegatesInvalidConnectedWorldTimeValidation() {
        new ClientScheduleEnvironmentTracker().observe(true, -2L, Collections.<String>emptySet());
    }
}
