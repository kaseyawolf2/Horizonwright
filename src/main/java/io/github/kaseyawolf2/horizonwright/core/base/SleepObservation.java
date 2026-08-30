package io.github.kaseyawolf2.horizonwright.core.base;

public final class SleepObservation {

    private final long revision;
    private final String observationFingerprint;
    private final int currentDimension;
    private final long worldTime;
    private final boolean sleepValidDimension;
    private final boolean danger;
    private final SleepProviderKind provider;
    private final BasePosition registeredBed;
    private final boolean providerAvailable;
    private final boolean loadedAndReachable;

    public SleepObservation(long revision, String observationFingerprint, int currentDimension, long worldTime,
        boolean sleepValidDimension, boolean danger, SleepProviderKind provider, BasePosition registeredBed,
        boolean providerAvailable, boolean loadedAndReachable) {
        if (revision < 0L || observationFingerprint == null
            || observationFingerprint.trim()
                .isEmpty()
            || worldTime < 0L
            || provider == null) {
            throw new IllegalArgumentException("revision, fingerprint, world time, and provider are required");
        }
        if (provider == SleepProviderKind.REGISTERED_BED && registeredBed == null) {
            throw new IllegalArgumentException("registered bed provider requires a bed position");
        }
        if (provider != SleepProviderKind.REGISTERED_BED && registeredBed != null) {
            throw new IllegalArgumentException("portable sleep providers must not carry a registered bed target");
        }
        this.revision = revision;
        this.observationFingerprint = observationFingerprint.trim();
        this.currentDimension = currentDimension;
        this.worldTime = worldTime;
        this.sleepValidDimension = sleepValidDimension;
        this.danger = danger;
        this.provider = provider;
        this.registeredBed = registeredBed;
        this.providerAvailable = providerAvailable;
        this.loadedAndReachable = loadedAndReachable;
    }

    public long getRevision() {
        return revision;
    }

    public String getObservationFingerprint() {
        return observationFingerprint;
    }

    public int getCurrentDimension() {
        return currentDimension;
    }

    public long getWorldTime() {
        return worldTime;
    }

    /** Adapter evidence that normal sleeping is permitted in the current dimension. */
    public boolean isSleepValidDimension() {
        return sleepValidDimension;
    }

    public boolean isDanger() {
        return danger;
    }

    public SleepProviderKind getProvider() {
        return provider;
    }

    public BasePosition getRegisteredBed() {
        return registeredBed;
    }

    public boolean isProviderAvailable() {
        return providerAvailable;
    }

    public boolean isLoadedAndReachable() {
        return loadedAndReachable;
    }
}
