package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;

/**
 * Forge-runtime operations which remain outside the pure death kernel.
 *
 * <p>
 * The emergency-stop method runs in the lethal signal call stack. The cleanup methods may only enqueue work on
 * the Minecraft client thread; the producer gate and action broker have already been latched before they are called.
 */
public interface ClientDeathInterlockDelegate {

    void onCriticalRestrictionsEntered();

    void onCriticalRestrictionsReleased();

    void performSynchronousEmergencyStop(DeathLatchRecord record);

    void scheduleClientThreadCleanup(DeathLatchRecord record);

    void scheduleClientThreadLockdownReaffirmation(long deathEpoch);

    void beforeDeathLockdownReleased(long deathEpoch);
}
