package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;

public class ExcavationStatisticsTest {

    @Test
    public void rollingWindowExpiresAtThirtySecondsAndSurvivesPauseAndReload() {
        ExcavationStatistics stats = new ExcavationStatistics(TaskCheckpoint.empty(), 60, 100, 60);
        stats.tick(0);
        stats.tick(1000);
        stats.broken(10);
        stats.tick(30000);
        TaskCheckpoint saved = stats.attach(new TaskCheckpoint(1, java.util.Collections.emptyMap()));
        assertEquals(
            "10",
            saved.getValues()
                .get("stats.rolling.count"));
        assertTrue(
            ExcavationStatistics.describe(saved)
                .contains("30s avg: 20.0"));
        stats.pause();
        stats = new ExcavationStatistics(saved, 60, 100, 60);
        stats.tick(9000000);
        assertEquals(
            "10",
            stats.attach(saved)
                .getValues()
                .get("stats.rolling.count"));
        stats.tick(9001000);
        assertEquals(
            "0",
            stats.attach(saved)
                .getValues()
                .get("stats.rolling.count"));
        assertEquals(
            "10",
            stats.attach(saved)
                .getValues()
                .get("stats.broken"));
    }

    @Test
    public void layerResetsOnlyItsOwnCountersAndReportsIndependentRemainingAndEta() {
        ExcavationStatistics stats = new ExcavationStatistics(TaskCheckpoint.empty(), 60, 100, 60);
        stats.tick(0);
        stats.tick(20000);
        stats.broken(10);
        stats.layer(59);
        stats.tick(30000);
        stats.broken(5);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("progress.total", "300");
        values.put("progress.completed", "120");
        TaskCheckpoint saved = stats.attach(new TaskCheckpoint(2, values));
        assertEquals(
            "15",
            saved.getValues()
                .get("stats.broken"));
        assertEquals(
            "5",
            saved.getValues()
                .get("stats.layerBroken"));
        assertEquals(
            "15",
            saved.getValues()
                .get("stats.rolling.count"));
        assertEquals(
            "5",
            saved.getValues()
                .get("stats.layerRolling.count"));
        String text = ExcavationStatistics.describe(saved);
        assertTrue(text.startsWith("Overall Total\n\n"));
        assertTrue(text.contains("Broken: 15 : Remaining*: 180"));
        assertTrue(text.contains("Current Layer (Y 59)\n\nElapsed: 10s : Time remaining: 2m 40s"));
        assertTrue(text.contains("Broken: 5 : Remaining*: 80"));
        assertTrue(text.endsWith("Last layer time: 20s"));
    }

    @Test
    public void oldLayerStartsMeasuringImmediatelyAndCompletedEtaIsZero() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("stats.activeMs", "60000");
        values.put("stats.layerMs", "30000");
        ExcavationStatistics stats = new ExcavationStatistics(new TaskCheckpoint(1, values), 60, 100, 60);
        assertTrue(
            ExcavationStatistics.describe(stats.attach(new TaskCheckpoint(1, values)))
                .contains("Broken: 0+"));
        stats.tick(100000);
        stats.tick(110000);
        stats.broken(5);
        TaskCheckpoint saved = stats.attach(new TaskCheckpoint(1, values));
        String measured = ExcavationStatistics.describe(saved);
        assertTrue(measured.contains("Broken: 5+"));
        assertTrue(measured.contains("Blocks/min: 30.0"));
        assertFalse(measured.contains("n/a"));
        stats = new ExcavationStatistics(saved, 60, 100, 60);
        stats.tick(900000);
        stats.tick(910000);
        stats.broken(5);
        measured = ExcavationStatistics.describe(stats.attach(saved));
        assertTrue(measured.contains("Broken: 10+"));
        assertTrue(measured.contains("Blocks/min: 30.0"));
        stats.layer(59);
        values.put("frontier.complete", "true");
        String text = ExcavationStatistics.describe(stats.attach(new TaskCheckpoint(2, values)));
        assertFalse(text.contains("Broken: n/a"));
        assertFalse(text.contains("since tracking began"));
        assertTrue(text.contains("Time remaining: 0s"));
    }

    @Test
    public void alreadyAccumulatedHiddenLayerCountsRetainTheirMatchingTimeBaseline() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("stats.activeMs", "900000");
        values.put("stats.layerMs", "600000");
        values.put("stats.layerCountsKnown", "false");
        values.put("stats.layerBroken", "20");
        values.put("stats.layerEtaStartMs", "540000");
        ExcavationStatistics stats = new ExcavationStatistics(new TaskCheckpoint(1, values), 60, 100, 60);
        TaskCheckpoint saved = stats.attach(new TaskCheckpoint(1, values));
        assertEquals(
            "540000",
            saved.getValues()
                .get("stats.layerRateStartMs"));
        String text = ExcavationStatistics.describe(saved);
        assertTrue(text.contains("Broken: 20+"));
        assertTrue(text.contains("Blocks/min: 20.0"));
        assertFalse(text.contains("n/a"));
    }

    @Test
    public void resumingAnOlderTaskDoesNotPretendItsHistoricalProgressWasMeasured() {
        Map<String, String> old = new LinkedHashMap<>();
        old.put("progress.total", "1000");
        old.put("progress.completed", "900");
        TaskCheckpoint checkpoint = new TaskCheckpoint(1, old);
        ExcavationStatistics stats = new ExcavationStatistics(checkpoint, 60);
        stats.tick(0);
        stats.tick(60000);
        assertTrue(
            ExcavationStatistics.describe(stats.attach(checkpoint))
                .contains("waiting for breaks"));
        old.put("progress.completed", "950");
        stats.broken(50);
        assertTrue(
            ExcavationStatistics.describe(stats.attach(new TaskCheckpoint(2, old)))
                .contains("Time remaining: 1m 0s"));
    }

    @Test
    public void pauseAndRestartExcludeOfflineTimeAndPreserveCompletedLayerTimes() {
        ExcavationStatistics stats = new ExcavationStatistics(TaskCheckpoint.empty(), 60);
        stats.tick(1000);
        stats.tick(61000);
        stats.broken(1);
        stats.pause();
        stats.tick(600000);
        stats.tick(630000);
        stats.layer(59);
        TaskCheckpoint saved = stats.attach(new TaskCheckpoint(1, java.util.Collections.emptyMap()));
        assertEquals(
            "90000",
            saved.getValues()
                .get("stats.activeMs"));
        assertEquals(
            "90000",
            saved.getValues()
                .get("stats.lastLayerMs"));
        assertEquals(
            "1",
            saved.getValues()
                .get("stats.layers"));
        ExcavationStatistics restored = new ExcavationStatistics(saved, 60);
        restored.tick(9000000);
        restored.tick(9030000);
        TaskCheckpoint after = restored.attach(saved);
        assertEquals(
            "120000",
            after.getValues()
                .get("stats.activeMs"));
        assertEquals(
            "30000",
            after.getValues()
                .get("stats.layerMs"));
        assertEquals(
            "1",
            after.getValues()
                .get("stats.broken"));
    }

    @Test
    public void timeRemainingUsesBreakRateInsteadOfAirScanningRate() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("progress.total", "1000");
        values.put("progress.completed", "500");
        ExcavationStatistics stats = new ExcavationStatistics(TaskCheckpoint.empty(), 64);
        stats.tick(0);
        stats.tick(60000);
        stats.broken(1);
        String text = ExcavationStatistics.describe(stats.attach(new TaskCheckpoint(1, values)));
        assertTrue(text.contains("Blocks/min: 1.0"));
        assertTrue(text.contains("Time remaining: 8h 20m"));
        assertTrue(text.contains("Broken: 1"));
    }

    @Test
    public void layerEstimateUsesTrailingWindowInsteadOfLifetimeLayerAverage() {
        ExcavationStatistics stats = new ExcavationStatistics(TaskCheckpoint.empty(), 60, 200, 60);
        stats.tick(0);
        stats.tick(10000);
        stats.broken(100);
        stats.tick(60000);
        stats.broken(5);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("progress.total", "400");
        values.put("progress.completed", "130");
        String text = ExcavationStatistics.describe(stats.attach(new TaskCheckpoint(1, values)));
        assertTrue(text.contains("Elapsed: 1m 0s : Time remaining: 2m 34s"));
        assertTrue(text.contains("Current Layer (Y 60)\n\nElapsed: 1m 0s : Time remaining: 7m 0s"));
        stats.tick(90000);
        text = ExcavationStatistics.describe(stats.attach(new TaskCheckpoint(2, values)));
        assertTrue(text.contains("Current Layer (Y 60)\n\nElapsed: 1m 30s : Time remaining: waiting for breaks"));
    }
}
