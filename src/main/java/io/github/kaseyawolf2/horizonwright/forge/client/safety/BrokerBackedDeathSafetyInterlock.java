package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.action.ActionBroker;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyInterlock;

/**
 * Fail-closed bridge from the death kernel to action authority and client cleanup.
 *
 * <p>
 * The producer gate is latched first and the action broker second, both before any cleanup delegate observes the
 * death. This makes the network-thread lethal hook an immediate action-reacquisition barrier even though Minecraft
 * keybinding cleanup must be scheduled onto the client thread.
 */
public final class BrokerBackedDeathSafetyInterlock implements DeathSafetyInterlock {

    private final ActionBroker actionBroker;
    private final AutomationInputGate inputGate;
    private final ClientDeathInterlockDelegate delegate;
    private volatile DeathLatchRecord latestLatch;

    public BrokerBackedDeathSafetyInterlock(ActionBroker actionBroker, AutomationInputGate inputGate,
        ClientDeathInterlockDelegate delegate) {
        if (actionBroker == null || inputGate == null || delegate == null) {
            throw new IllegalArgumentException("action broker, input gate, and delegate must not be null");
        }
        this.actionBroker = actionBroker;
        this.inputGate = inputGate;
        this.delegate = delegate;
    }

    @Override
    public void enterCriticalRestrictions() {
        inputGate.enterCriticalRestrictions();
        delegate.onCriticalRestrictionsEntered();
    }

    @Override
    public void releaseCriticalRestrictions() {
        delegate.onCriticalRestrictionsReleased();
        inputGate.releaseCriticalRestrictions();
    }

    @Override
    public void latchDeath(DeathLatchRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        latestLatch = record;
        inputGate.latchDeath(record.getDeathEpoch());
        RuntimeException failure = runAndCollect(null, new Runnable() {

            @Override
            public void run() {
                actionBroker.enterSafetyLockdown();
            }
        });
        failure = runAndCollect(failure, new Runnable() {

            @Override
            public void run() {
                delegate.performSynchronousEmergencyStop(record);
            }
        });
        failure = runAndCollect(failure, new Runnable() {

            @Override
            public void run() {
                delegate.scheduleClientThreadCleanup(record);
            }
        });
        rethrow(failure);
    }

    @Override
    public void reaffirmDeathLockdown(long deathEpoch) {
        inputGate.reaffirmDeath(deathEpoch);
        RuntimeException failure = runAndCollect(null, new Runnable() {

            @Override
            public void run() {
                actionBroker.enterSafetyLockdown();
            }
        });
        failure = runAndCollect(failure, new Runnable() {

            @Override
            public void run() {
                delegate.scheduleClientThreadLockdownReaffirmation(deathEpoch);
            }
        });
        rethrow(failure);
    }

    @Override
    public void releaseDeathLockdown(long deathEpoch) {
        delegate.beforeDeathLockdownReleased(deathEpoch);
        actionBroker.leaveSafetyLockdown();
        inputGate.releaseDeath(deathEpoch);
        DeathLatchRecord record = latestLatch;
        if (record != null && record.getDeathEpoch() == deathEpoch) {
            latestLatch = null;
        }
    }

    public Optional<DeathLatchRecord> latestLatch() {
        return Optional.ofNullable(latestLatch);
    }

    private static RuntimeException runAndCollect(RuntimeException first, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            if (first == null) {
                return failure;
            }
            first.addSuppressed(failure);
        }
        return first;
    }

    private static void rethrow(RuntimeException failure) {
        if (failure != null) {
            throw failure;
        }
    }
}
