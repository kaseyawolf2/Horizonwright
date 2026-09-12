package io.github.kaseyawolf2.horizonwright.core.base;

import static org.junit.Assert.*;

import org.junit.Test;

public class SleepTravelEstimateTest {

    @Test
    public void leadAccountsForDistanceHeightAndBufferButIsBounded() {
        assertEquals(100, SleepTravelEstimate.ticks(0, 0));
        assertEquals(1000, SleepTravelEstimate.ticks(100, 0));
        assertTrue(SleepTravelEstimate.ticks(100, 40) > SleepTravelEstimate.ticks(100, 0));
        assertEquals(6000, SleepTravelEstimate.ticks(10000, 100));
        assertFalse(SleepTravelEstimate.beforeTonight(24000, 12500));
        assertTrue(SleepTravelEstimate.beforeTonight(12500, 12000));
        assertFalse(SleepTravelEstimate.beforeTonight(12542, 12000));
    }

    @Test
    public void earlyTravelRetainsBedSafetyAndNormalSleepWindow() {
        SleepPlanner planner = new SleepPlanner();
        SleepObservation safe = observation(12000, false);
        assertEquals(
            SleepActionKind.SKIP_DAYTIME,
            planner.plan(safe)
                .getAction());
        assertEquals(
            SleepActionKind.USE_REGISTERED_BED,
            planner.plan(safe, 1000)
                .getAction());
        assertEquals(
            SleepActionKind.HOLD_DANGER,
            planner.plan(observation(12000, true), 1000)
                .getAction());
        assertEquals(
            SleepActionKind.SKIP_DAYTIME,
            planner.plan(observation(11000, false), 1000)
                .getAction());
        assertEquals(
            SleepActionKind.SKIP_DAYTIME,
            planner.plan(observation(24000, false), 1000)
                .getAction());
    }

    private static SleepObservation observation(long time, boolean danger) {
        return new SleepObservation(
            1,
            "bed",
            0,
            time,
            true,
            danger,
            SleepProviderKind.REGISTERED_BED,
            new BasePosition(0, 0, 64, 0),
            true,
            true);
    }
}
