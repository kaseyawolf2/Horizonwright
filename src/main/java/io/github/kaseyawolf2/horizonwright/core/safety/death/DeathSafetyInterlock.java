package io.github.kaseyawolf2.horizonwright.core.safety.death;

/**
 * Synchronous fail-closed boundary implemented by the client runtime.
 *
 * <p>
 * The lethal network hook must call the controller before queueing the inbound packet. The controller invokes
 * {@link #latchDeath(DeathLatchRecord)} in that same call stack after storing the unresolved record and before it
 * returns. An implementation must complete every action listed by the record before returning.
 */
public interface DeathSafetyInterlock {

    void enterCriticalRestrictions();

    void releaseCriticalRestrictions();

    void latchDeath(DeathLatchRecord record);

    void reaffirmDeathLockdown(long deathEpoch);

    void releaseDeathLockdown(long deathEpoch);
}
