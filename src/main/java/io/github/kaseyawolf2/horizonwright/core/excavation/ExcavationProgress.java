package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.List;
import java.util.Objects;

/** Immutable mutually exclusive progress counters for a fixed raw cylinder volume. */
public final class ExcavationProgress {

    private final long total;
    private final long completed;
    private final long protectedBlocks;
    private final long unreachable;
    private final long fluidContained;
    private final long failed;

    public ExcavationProgress(long total, long completed, long protectedBlocks, long unreachable, long fluidContained,
        long failed) {
        if (total < 0L || completed < 0L
            || protectedBlocks < 0L
            || unreachable < 0L
            || fluidContained < 0L
            || failed < 0L) {
            throw new IllegalArgumentException("excavation progress counters must not be negative");
        }
        long processed = addExact(completed, protectedBlocks, unreachable, fluidContained, failed);
        if (processed > total) {
            throw new IllegalArgumentException("processed excavation counters exceed total volume");
        }
        this.total = total;
        this.completed = completed;
        this.protectedBlocks = protectedBlocks;
        this.unreachable = unreachable;
        this.fluidContained = fluidContained;
        this.failed = failed;
    }

    public static ExcavationProgress empty(long total) {
        return new ExcavationProgress(total, 0L, 0L, 0L, 0L, 0L);
    }

    public long getTotal() {
        return total;
    }

    public long getCompleted() {
        return completed;
    }

    public long getRemaining() {
        return total - getProcessed();
    }

    public long getProtectedBlocks() {
        return protectedBlocks;
    }

    public long getUnreachable() {
        return unreachable;
    }

    public long getFluidContained() {
        return fluidContained;
    }

    public long getFailed() {
        return failed;
    }

    public long getProcessed() {
        return addExact(completed, protectedBlocks, unreachable, fluidContained, failed);
    }

    ExcavationProgress advance(List<ExcavationTargetResult> results) {
        long nextCompleted = completed;
        long nextProtected = protectedBlocks;
        long nextUnreachable = unreachable;
        long nextFluidContained = fluidContained;
        long nextFailed = failed;
        for (ExcavationTargetResult result : results) {
            switch (result.getOutcome()) {
                case COMPLETED:
                    nextCompleted = Math.addExact(nextCompleted, 1L);
                    break;
                case PROTECTED:
                    nextProtected = Math.addExact(nextProtected, 1L);
                    break;
                case UNREACHABLE:
                    nextUnreachable = Math.addExact(nextUnreachable, 1L);
                    break;
                case FLUID_CONTAINED:
                    nextFluidContained = Math.addExact(nextFluidContained, 1L);
                    break;
                case FAILED:
                    nextFailed = Math.addExact(nextFailed, 1L);
                    break;
                default:
                    throw new IllegalStateException("unhandled excavation outcome");
            }
        }
        return new ExcavationProgress(
            total,
            nextCompleted,
            nextProtected,
            nextUnreachable,
            nextFluidContained,
            nextFailed);
    }

    private static long addExact(long... values) {
        long result = 0L;
        for (long value : values) {
            result = Math.addExact(result, value);
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExcavationProgress)) {
            return false;
        }
        ExcavationProgress that = (ExcavationProgress) other;
        return total == that.total && completed == that.completed
            && protectedBlocks == that.protectedBlocks
            && unreachable == that.unreachable
            && fluidContained == that.fluidContained
            && failed == that.failed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(total, completed, protectedBlocks, unreachable, fluidContained, failed);
    }
}
