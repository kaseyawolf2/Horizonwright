package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Objects;

/** Immutable execution token bound to every decision-relevant sleep observation field. */
public final class SleepDecision {

    private final SleepActionKind action;
    private final SleepWindow sleepWindow;
    private final long observationRevision;
    private final String observationFingerprint;
    private final int currentDimension;
    private final long worldTime;
    private final boolean sleepValidDimension;
    private final boolean danger;
    private final SleepProviderKind provider;
    private final BasePosition registeredBed;
    private final boolean providerAvailable;
    private final boolean loadedAndReachable;

    SleepDecision(SleepActionKind action, SleepWindow sleepWindow, SleepObservation observation) {
        if (action == null || sleepWindow == null || observation == null) {
            throw new IllegalArgumentException("action, sleep window, and observation are required");
        }
        this.action = action;
        this.sleepWindow = sleepWindow;
        observationRevision = observation.getRevision();
        observationFingerprint = observation.getObservationFingerprint();
        currentDimension = observation.getCurrentDimension();
        worldTime = observation.getWorldTime();
        sleepValidDimension = observation.isSleepValidDimension();
        danger = observation.isDanger();
        provider = observation.getProvider();
        registeredBed = observation.getRegisteredBed();
        providerAvailable = observation.isProviderAvailable();
        loadedAndReachable = observation.isLoadedAndReachable();
    }

    public SleepActionKind getAction() {
        return action;
    }

    public long getObservationRevision() {
        return observationRevision;
    }

    public String getObservationFingerprint() {
        return observationFingerprint;
    }

    public long getWorldTime() {
        return worldTime;
    }

    public int getCurrentDimension() {
        return currentDimension;
    }

    public boolean isDanger() {
        return danger;
    }

    public SleepProviderKind getProvider() {
        return provider;
    }

    public boolean requiresInteraction() {
        return action == SleepActionKind.USE_REGISTERED_BED || action == SleepActionKind.USE_PORTABLE_PROVIDER;
    }

    /** Must pass immediately before interaction; a changed world tick, danger state, or provider invalidates it. */
    public boolean isCurrentFor(SleepWindow currentWindow, SleepObservation currentObservation) {
        return currentWindow != null && currentObservation != null
            && sleepWindow.equals(currentWindow)
            && observationRevision == currentObservation.getRevision()
            && observationFingerprint.equals(currentObservation.getObservationFingerprint())
            && currentDimension == currentObservation.getCurrentDimension()
            && worldTime == currentObservation.getWorldTime()
            && sleepValidDimension == currentObservation.isSleepValidDimension()
            && danger == currentObservation.isDanger()
            && provider == currentObservation.getProvider()
            && Objects.equals(registeredBed, currentObservation.getRegisteredBed())
            && providerAvailable == currentObservation.isProviderAvailable()
            && loadedAndReachable == currentObservation.isLoadedAndReachable();
    }
}
