package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Immutable payload delivered synchronously to the client safety interlock. */
public final class DeathLatchRecord {

    private static final Set<EmergencyStopAction> REQUIRED_ACTIONS = Collections
        .unmodifiableSet(EnumSet.allOf(EmergencyStopAction.class));

    private final long deathEpoch;
    private final long connectionEpoch;
    private final long clientTick;
    private final DeathSignal signal;
    private final String serverIdentity;
    private final String worldIdentity;
    private final DeathContext context;

    DeathLatchRecord(long deathEpoch, long connectionEpoch, long clientTick, DeathSignal signal, String serverIdentity,
        String worldIdentity, DeathContext context) {
        this.deathEpoch = deathEpoch;
        this.connectionEpoch = connectionEpoch;
        this.clientTick = clientTick;
        this.signal = signal;
        this.serverIdentity = serverIdentity;
        this.worldIdentity = worldIdentity;
        this.context = context;
    }

    public long getDeathEpoch() {
        return deathEpoch;
    }

    public long getConnectionEpoch() {
        return connectionEpoch;
    }

    public long getClientTick() {
        return clientTick;
    }

    public DeathSignal getSignal() {
        return signal;
    }

    public String getServerIdentity() {
        return serverIdentity;
    }

    public String getWorldIdentity() {
        return worldIdentity;
    }

    public DimensionBlockPosition getDeathPosition() {
        return context.getDeathPosition();
    }

    public String getOldPlayerIdentity() {
        return context.getOldPlayerIdentity();
    }

    public Optional<String> getActiveTaskId() {
        return context.getActiveTaskId();
    }

    public String getPreDeathInventoryFingerprint() {
        return context.getPreDeathInventory()
            .getContentFingerprint();
    }

    public Set<EmergencyStopAction> getRequiredActions() {
        return REQUIRED_ACTIONS;
    }
}
