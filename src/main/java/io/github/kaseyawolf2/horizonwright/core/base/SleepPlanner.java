package io.github.kaseyawolf2.horizonwright.core.base;

/** Normal-interaction sleep policy. */
public final class SleepPlanner {

    private final SleepWindow sleepWindow;

    public SleepPlanner() {
        this(SleepWindow.vanilla());
    }

    public SleepPlanner(SleepWindow sleepWindow) {
        if (sleepWindow == null) {
            throw new IllegalArgumentException("sleepWindow must not be null");
        }
        this.sleepWindow = sleepWindow;
    }

    public SleepWindow getSleepWindow() {
        return sleepWindow;
    }

    public SleepDecision plan(SleepObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation must not be null");
        }
        if (!sleepWindow.contains(observation.getWorldTime())) {
            return decision(SleepActionKind.SKIP_DAYTIME, observation);
        }
        if (!observation.isSleepValidDimension()) {
            return decision(SleepActionKind.HOLD_INVALID_DIMENSION, observation);
        }
        if (observation.isDanger()) {
            return decision(SleepActionKind.HOLD_DANGER, observation);
        }
        if (observation.getProvider() == SleepProviderKind.REGISTERED_BED && observation.getRegisteredBed()
            .getDimensionId() != observation.getCurrentDimension()) {
            return decision(SleepActionKind.HOLD_WRONG_DIMENSION, observation);
        }
        if (!observation.isProviderAvailable()) {
            return decision(SleepActionKind.HOLD_PROVIDER_UNAVAILABLE, observation);
        }
        if (!observation.isLoadedAndReachable()) {
            return decision(SleepActionKind.HOLD_UNLOADED_OR_UNREACHABLE, observation);
        }
        return decision(
            observation.getProvider() == SleepProviderKind.REGISTERED_BED ? SleepActionKind.USE_REGISTERED_BED
                : SleepActionKind.USE_PORTABLE_PROVIDER,
            observation);
    }

    private SleepDecision decision(SleepActionKind action, SleepObservation observation) {
        return new SleepDecision(action, sleepWindow, observation);
    }
}
