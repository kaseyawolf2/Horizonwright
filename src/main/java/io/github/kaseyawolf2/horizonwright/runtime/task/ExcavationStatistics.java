package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;

/** Active-time totals and trailing 30-second break samples, persisted with task progress. */
public final class ExcavationStatistics {

    private long activeMillis, layerMillis, broken, layerBroken, layers, lastLayerMillis, bestLayerMillis;
    private long lastTick = -1;
    private final long processedBaseline;
    private long layerProcessedBaseline, layerEtaStartMillis;
    private long layerRateStartMillis;
    private boolean layerCountsKnown;
    private int layerY;
    private final long columns;
    private final int topY;
    private final Rolling overallRolling, layerRolling;

    ExcavationStatistics(TaskCheckpoint checkpoint, int initialY) {
        this(checkpoint, initialY, 0, initialY);
    }

    ExcavationStatistics(TaskCheckpoint checkpoint, int initialY, long columns, int topY) {
        Map<String, String> v = checkpoint.getValues();
        this.columns = columns;
        this.topY = topY;
        activeMillis = number(v, "stats.activeMs");
        layerMillis = number(v, "stats.layerMs");
        broken = number(v, "stats.broken");
        layerBroken = number(v, "stats.layerBroken");
        layerCountsKnown = v.containsKey("stats.layerCountsKnown")
            ? Boolean.parseBoolean(v.get("stats.layerCountsKnown"))
            : activeMillis == 0;
        layers = number(v, "stats.layers");
        lastLayerMillis = number(v, "stats.lastLayerMs");
        bestLayerMillis = number(v, "stats.bestLayerMs");
        processedBaseline = v.containsKey("stats.processedBaseline") ? number(v, "stats.processedBaseline")
            : processed(v);
        layerY = v.containsKey("stats.layerY") ? (int) number(v, "stats.layerY") : initialY;
        layerProcessedBaseline = v.containsKey("stats.layerProcessedBaseline")
            ? number(v, "stats.layerProcessedBaseline")
            : layerProcessed(processed(v));
        layerEtaStartMillis = v.containsKey("stats.layerEtaStartMs") ? number(v, "stats.layerEtaStartMs") : layerMillis;
        layerRateStartMillis = Math.min(
            layerMillis,
            v.containsKey("stats.layerRateStartMs") ? number(v, "stats.layerRateStartMs")
                : layerCountsKnown ? 0 : v.containsKey("stats.layerBroken") ? layerEtaStartMillis : layerMillis);
        overallRolling = new Rolling(v, "stats.rolling", activeMillis);
        layerRolling = new Rolling(v, "stats.layerRolling", activeMillis);
    }

    void tick(long now) {
        if (lastTick >= 0 && now >= lastTick) {
            activeMillis = Math.addExact(activeMillis, now - lastTick);
            layerMillis = Math.addExact(layerMillis, now - lastTick);
        }
        lastTick = now;
    }

    void pause() {
        lastTick = -1;
    }

    void broken(int count) {
        broken(count, layerY);
    }

    void broken(int count, int y) {
        if (count < 0) throw new IllegalArgumentException("negative break count");
        broken = Math.addExact(broken, count);
        overallRolling.add(activeMillis, count);
        if (y == layerY) {
            layerBroken = Math.addExact(layerBroken, count);
            layerRolling.add(activeMillis, count);
        }
    }

    void layer(int nextY) {
        if (nextY == layerY) return;
        if (nextY < layerY) {
            layers++;
            lastLayerMillis = layerMillis;
            if (layerMillis > 0 && (bestLayerMillis == 0 || layerMillis < bestLayerMillis))
                bestLayerMillis = layerMillis;
        }
        layerY = nextY;
        layerMillis = 0;
        layerBroken = 0;
        layerCountsKnown = true;
        layerProcessedBaseline = 0;
        layerEtaStartMillis = 0;
        layerRateStartMillis = 0;
        layerRolling.reset(activeMillis);
    }

    private long layerProcessed(long total) {
        return Math.min(columns, Math.max(0, total - Math.max(0L, (long) topY - layerY) * columns));
    }

    TaskCheckpoint attach(TaskCheckpoint checkpoint) {
        Map<String, String> v = new LinkedHashMap<>(checkpoint.getValues());
        v.put("stats.activeMs", Long.toString(activeMillis));
        v.put("stats.layerMs", Long.toString(layerMillis));
        v.put("stats.layerY", Integer.toString(layerY));
        v.put("stats.broken", Long.toString(broken));
        v.put("stats.layerBroken", Long.toString(layerBroken));
        v.put("stats.layerCountsKnown", Boolean.toString(layerCountsKnown));
        v.put("stats.layers", Long.toString(layers));
        v.put("stats.lastLayerMs", Long.toString(lastLayerMillis));
        v.put("stats.bestLayerMs", Long.toString(bestLayerMillis));
        v.put("stats.processedBaseline", Long.toString(processedBaseline));
        v.put("stats.layerProcessedBaseline", Long.toString(layerProcessedBaseline));
        v.put("stats.layerEtaStartMs", Long.toString(layerEtaStartMillis));
        v.put("stats.layerRateStartMs", Long.toString(layerRateStartMillis));
        long checked = layerProcessed(processed(v));
        v.put("stats.layerProcessed", Long.toString(checked));
        v.put("stats.layerRemaining", Long.toString(Math.max(0, columns - checked)));
        overallRolling.write(v, "stats.rolling", activeMillis);
        layerRolling.write(v, "stats.layerRolling", activeMillis);
        return new TaskCheckpoint(checkpoint.getRevision(), v);
    }

    public static String describe(TaskCheckpoint checkpoint) {
        Map<String, String> v = checkpoint.getValues();
        if (!v.containsKey("stats.activeMs")) return "Mining stats available after the task starts";
        long ms = number(v, "stats.activeMs"), broken = number(v, "stats.broken");
        long remaining = Math.max(0, number(v, "progress.total") - processed(v));
        boolean complete = "true".equals(v.get("frontier.complete"));
        long layerMs = number(v, "stats.layerMs"), layerRemaining = complete ? 0 : number(v, "stats.layerRemaining");
        boolean known = Boolean.parseBoolean(v.get("stats.layerCountsKnown"));
        String totalEta = eta(ms, broken, remaining, complete);
        String layerEta = eta(
            Math.min(30000, Math.max(0, ms - number(v, "stats.layerRolling.start"))),
            number(v, "stats.layerRolling.count"),
            layerRemaining,
            complete);
        String last = complete ? duration(layerMs)
            : number(v, "stats.layers") > 0 ? duration(number(v, "stats.lastLayerMs")) : "--";
        return "Overall Total\n\n" + "Elapsed: "
            + duration(ms)
            + " : Time remaining: "
            + totalEta
            + "\n"
            + "Broken: "
            + broken
            + " : Remaining*: "
            + remaining
            + "\n"
            + "Blocks/min: "
            + rate(broken, ms)
            + " : 30s avg: "
            + rollingRate(v, "stats.rolling", ms)
            + "\n\nCurrent Layer (Y "
            + v.get("stats.layerY")
            + ")\n\n"
            + "Elapsed: "
            + duration(layerMs)
            + " : Time remaining: "
            + layerEta
            + "\n"
            + "Broken: "
            + number(v, "stats.layerBroken")
            + (known ? "" : "+")
            + " : Remaining*: "
            + layerRemaining
            + "\n"
            + "Blocks/min: "
            + rate(number(v, "stats.layerBroken"), Math.max(0, layerMs - number(v, "stats.layerRateStartMs")))
            + " : 30s avg: "
            + rollingRate(v, "stats.layerRolling", ms)
            + "\n\n* Remaining positions may include air."
            + (known ? "" : "\n+ Layer breaks counted since tracking began.")
            + "\nLast layer time: "
            + last;
    }

    private static String eta(long ms, long measured, long remaining, boolean complete) {
        if (complete) return "0s";
        if (remaining == 0) return "verifying";
        return measured > 0 && ms > 0 ? duration((long) ((double) ms * remaining / measured)) : "waiting for breaks";
    }

    private static String rate(long count, long ms) {
        return ms > 0 ? String.format(Locale.ROOT, "%.1f", count * 60000D / ms) : "0.0";
    }

    private static String rollingRate(Map<String, String> v, String prefix, long now) {
        return rate(number(v, prefix + ".count"), Math.min(30000, Math.max(0, now - number(v, prefix + ".start"))));
    }

    private static final class Rolling {

        private final Deque<long[]> samples = new ArrayDeque<>();
        private long start;

        Rolling(Map<String, String> v, String prefix, long now) {
            start = v.containsKey(prefix + ".start") ? Math.min(now, number(v, prefix + ".start")) : now;
            try {
                for (String entry : v.getOrDefault(prefix + ".samples", "")
                    .split(",")) {
                    if (entry.isEmpty()) continue;
                    String[] parts = entry.split(":");
                    long time = Long.parseLong(parts[0]), count = Long.parseLong(parts[1]);
                    if (time >= start && time <= now && count > 0) samples.addLast(new long[] { time, count });
                }
            } catch (RuntimeException invalid) {
                reset(now);
            }
            expire(now);
        }

        void reset(long now) {
            samples.clear();
            start = now;
        }

        void expire(long now) {
            while (!samples.isEmpty() && samples.peekFirst()[0] <= now - 30000) samples.removeFirst();
        }

        void add(long now, int count) {
            expire(now);
            if (count == 0) return;
            if (!samples.isEmpty() && samples.peekLast()[0] == now) samples.peekLast()[1] += count;
            else samples.addLast(new long[] { now, count });
        }

        void write(Map<String, String> v, String prefix, long now) {
            expire(now);
            long count = 0;
            StringBuilder encoded = new StringBuilder();
            for (long[] sample : samples) {
                if (encoded.length() > 0) encoded.append(',');
                encoded.append(sample[0])
                    .append(':')
                    .append(sample[1]);
                count += sample[1];
            }
            v.put(prefix + ".start", Long.toString(start));
            v.put(prefix + ".samples", encoded.toString());
            v.put(prefix + ".count", Long.toString(count));
        }
    }

    private static long processed(Map<String, String> v) {
        return number(v, "progress.completed") + number(v, "progress.protected")
            + number(v, "progress.unreachable")
            + number(v, "progress.fluidContained")
            + number(v, "progress.failed");
    }

    private static long number(Map<String, String> v, String key) {
        try {
            return Math.max(0, Long.parseLong(v.getOrDefault(key, "0")));
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    private static String duration(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? s / 3600 + "h " + s % 3600 / 60 + "m" : s >= 60 ? s / 60 + "m " + s % 60 + "s" : s + "s";
    }
}
