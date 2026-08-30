package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Objects;

/** Configurable inclusive-start, exclusive-end sleep window within a repeating world day. */
public final class SleepWindow {

    private static final long VANILLA_DAY_LENGTH = 24000L;
    private static final long VANILLA_NIGHT_START = 12542L;
    private static final long VANILLA_NIGHT_END_EXCLUSIVE = 23461L;

    private final long dayLength;
    private final long startInclusive;
    private final long endExclusive;

    public SleepWindow(long dayLength, long startInclusive, long endExclusive) {
        if (dayLength <= 1L || startInclusive < 0L
            || startInclusive >= dayLength
            || endExclusive < 0L
            || endExclusive >= dayLength
            || startInclusive == endExclusive) {
            throw new IllegalArgumentException("sleep window must be a non-empty interval within one world day");
        }
        this.dayLength = dayLength;
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
    }

    public static SleepWindow vanilla() {
        return new SleepWindow(VANILLA_DAY_LENGTH, VANILLA_NIGHT_START, VANILLA_NIGHT_END_EXCLUSIVE);
    }

    public long getDayLength() {
        return dayLength;
    }

    public long getStartInclusive() {
        return startInclusive;
    }

    public long getEndExclusive() {
        return endExclusive;
    }

    public boolean contains(long worldTime) {
        if (worldTime < 0L) {
            throw new IllegalArgumentException("worldTime must not be negative");
        }
        long timeOfDay = worldTime % dayLength;
        if (startInclusive < endExclusive) {
            return timeOfDay >= startInclusive && timeOfDay < endExclusive;
        }
        return timeOfDay >= startInclusive || timeOfDay < endExclusive;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SleepWindow)) {
            return false;
        }
        SleepWindow that = (SleepWindow) other;
        return dayLength == that.dayLength && startInclusive == that.startInclusive
            && endExclusive == that.endExclusive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dayLength, startInclusive, endExclusive);
    }
}
