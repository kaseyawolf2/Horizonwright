package io.github.kaseyawolf2.horizonwright.core.base;

/** Conservative walking estimate, expressed in game ticks; terrain can make the actual route longer. */
public final class SleepTravelEstimate {

    public static final int MAX_LEAD_TICKS = 6000;

    private SleepTravelEstimate() {}

    public static int ticks(double horizontalBlocks, double verticalBlocks) {
        if (!Double.isFinite(horizontalBlocks) || !Double.isFinite(verticalBlocks) || horizontalBlocks < 0) return 0;
        double seconds = horizontalBlocks * 0.45 + Math.abs(verticalBlocks) * 1.5 + 5.0;
        return (int) Math.min(MAX_LEAD_TICKS, Math.ceil(seconds * 20.0));
    }

    public static boolean beforeTonight(long worldTime, long departureTime) {
        return worldTime / 24000L == departureTime / 24000L && worldTime % 24000L < 12542L;
    }
}
