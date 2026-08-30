package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ActionBrokerSnapshot {

    private final long epoch;
    private final boolean safetyLocked;
    private final boolean automationLocked;
    private final boolean deathSafetyLocked;
    private final Map<ActionCapability, String> activeOwners;

    ActionBrokerSnapshot(long epoch, boolean automationLocked, boolean deathSafetyLocked,
        Map<ActionCapability, String> activeOwners) {
        this.epoch = epoch;
        this.automationLocked = automationLocked;
        this.deathSafetyLocked = deathSafetyLocked;
        this.safetyLocked = automationLocked || deathSafetyLocked;
        this.activeOwners = Collections.unmodifiableMap(new EnumMap<>(activeOwners));
    }

    public long getEpoch() {
        return epoch;
    }

    /** Aggregate automation-acquisition lock; never a direct-player packet policy. */
    public boolean isSafetyLocked() {
        return safetyLocked;
    }

    public boolean isAutomationLocked() {
        return automationLocked;
    }

    public boolean isDeathSafetyLocked() {
        return deathSafetyLocked;
    }

    public Map<ActionCapability, String> getActiveOwners() {
        return activeOwners;
    }
}
