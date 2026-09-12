package io.github.kaseyawolf2.horizonwright.core.task;

/** Presentation of scheduler time without advancing the scheduler clock. */
public final class ScheduleTiming {

    private ScheduleTiming() {}

    public static String describe(ScheduleSnapshot schedule, SchedulerSnapshot clock) {
        return describe(schedule, clock, 0);
    }

    public static String describe(ScheduleSnapshot schedule, SchedulerSnapshot clock, int travelLeadTicks) {
        if (schedule.getState() != ScheduleState.ACTIVE) return "Next activation: " + schedule.getState()
            .name()
            .toLowerCase(java.util.Locale.ROOT);
        ScheduleRule rule = schedule.getRule();
        if (rule.getTrigger() == ScheduleTrigger.CONNECTED_INTERVAL) {
            long remaining = Math.max(0L, schedule.getNextConnectedDueMillis() - clock.getConnectedElapsedMillis());
            return "Next activation: " + duration(remaining) + " connected time";
        }
        if (rule.getTrigger() == ScheduleTrigger.IDLE) return "Next activation: when idle and conditions are met";
        long world = clock.getLastWorldTimeTicks();
        if (world == ScheduleEnvironment.UNKNOWN_WORLD_TIME) return "Next activation: waiting for world time";
        long day = world / ScheduleRule.WORLD_DAY_TICKS;
        int lead = rule.getWindowStartTick() < rule.getWindowEndTick()
            ? Math.min(rule.getWindowStartTick(), Math.max(0, travelLeadTicks))
            : 0;
        long start = day * ScheduleRule.WORLD_DAY_TICKS + rule.getWindowStartTick() - lead;
        long end = day * ScheduleRule.WORLD_DAY_TICKS + rule.getWindowEndTick();
        if (rule.getWindowEndTick() <= rule.getWindowStartTick()) {
            if (world % ScheduleRule.WORLD_DAY_TICKS < rule.getWindowEndTick()) {
                day--;
                start -= ScheduleRule.WORLD_DAY_TICKS;
            } else end += ScheduleRule.WORLD_DAY_TICKS;
        }
        if (world >= start && world < end && day > schedule.getLastWorldOccurrence()) {
            return "Next activation: window open; waiting for eligibility";
        }
        if (start <= world || day <= schedule.getLastWorldOccurrence()) start += ScheduleRule.WORLD_DAY_TICKS;
        return lead > 0
            ? "Depart in ~" + duration(Math.max(0L, start - world) * 50L) + "; estimated trip " + duration(lead * 50L)
            : "Next activation: ~" + duration(Math.max(0L, start - world) * 50L) + " (world time)";
    }

    private static String duration(long millis) {
        long seconds = millis / 1000L + (millis % 1000L == 0 ? 0 : 1);
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }
}
