package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ActionBrokerSnapshot {

    private final long epoch;
    private final boolean safetyLocked;
    private final Map<ActionCapability, String> activeOwners;

    ActionBrokerSnapshot(long epoch, boolean safetyLocked, Map<ActionCapability, String> activeOwners) {
        this.epoch = epoch;
        this.safetyLocked = safetyLocked;
        this.activeOwners = Collections.unmodifiableMap(new EnumMap<>(activeOwners));
    }

    public long getEpoch() {
        return epoch;
    }

    public boolean isSafetyLocked() {
        return safetyLocked;
    }

    public Map<ActionCapability, String> getActiveOwners() {
        return activeOwners;
    }
}
