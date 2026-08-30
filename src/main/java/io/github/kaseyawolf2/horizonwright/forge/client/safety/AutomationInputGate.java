package io.github.kaseyawolf2.horizonwright.forge.client.safety;

/** Synchronous producer-side latch which every automated input owner must consult. */
public final class AutomationInputGate {

    public enum Mode {
        ACTIVE,
        CRITICAL_MOVEMENT_ONLY,
        DEATH_LOCKDOWN
    }

    private Mode mode = Mode.ACTIVE;
    private long deathEpoch;

    public synchronized void enterCriticalRestrictions() {
        if (mode == Mode.ACTIVE) {
            mode = Mode.CRITICAL_MOVEMENT_ONLY;
        }
    }

    public synchronized void releaseCriticalRestrictions() {
        if (mode == Mode.CRITICAL_MOVEMENT_ONLY) {
            mode = Mode.ACTIVE;
        }
    }

    public synchronized void latchDeath(long requestedDeathEpoch) {
        if (requestedDeathEpoch <= 0L) {
            throw new IllegalArgumentException("death epoch must be positive");
        }
        if (mode == Mode.DEATH_LOCKDOWN && deathEpoch != requestedDeathEpoch) {
            throw new IllegalStateException("a different death epoch is already latched");
        }
        deathEpoch = requestedDeathEpoch;
        mode = Mode.DEATH_LOCKDOWN;
    }

    public synchronized void reaffirmDeath(long requestedDeathEpoch) {
        latchDeath(requestedDeathEpoch);
    }

    public synchronized void releaseDeath(long resolvedDeathEpoch) {
        if (mode != Mode.DEATH_LOCKDOWN || deathEpoch != resolvedDeathEpoch) {
            throw new IllegalStateException("only the currently latched death epoch may be released");
        }
        deathEpoch = 0L;
        mode = Mode.ACTIVE;
    }

    public synchronized Mode getMode() {
        return mode;
    }

    public synchronized long getDeathEpoch() {
        return deathEpoch;
    }

    public synchronized boolean allowsGenericAutomationInput() {
        return mode == Mode.ACTIVE;
    }

    public synchronized boolean allowsMovementOnlyRetreat() {
        return mode == Mode.ACTIVE || mode == Mode.CRITICAL_MOVEMENT_ONLY;
    }

    public synchronized boolean blocksAllAutomationInputOwners() {
        return mode == Mode.DEATH_LOCKDOWN;
    }
}
