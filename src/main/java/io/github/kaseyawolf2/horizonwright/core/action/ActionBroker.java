package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.Optional;
import java.util.Set;

public interface ActionBroker {

    Optional<ActionLease> tryAcquire(String owner, Set<ActionCapability> capabilities);

    long currentEpoch();

    /**
     * Aggregate automation-acquisition lock. This must never be used to gate direct player input or traffic;
     * packet-level death policy must consult {@link #isDeathSafetyLocked()}.
     */
    boolean isSafetyLocked();

    /** True only for the death/item-preservation packet lockdown, not an operator automation stop. */
    boolean isDeathSafetyLocked();

    /** Operator stop which prevents automation acquisition without restricting direct player traffic. */
    boolean isAutomationLocked();

    ActionBrokerSnapshot snapshot();

    void addRevocationListener(ActionRevocationListener listener);

    void removeRevocationListener(ActionRevocationListener listener);

    void revokeAll();

    /** Establishes a fresh epoch above a persisted floor in one bounded transition. */
    void advanceEpochPast(long floor);

    void enterAutomationLockdown();

    void leaveAutomationLockdown();

    /** Death/item-preservation lockdown at the tested packet boundary. */
    void enterSafetyLockdown();

    void leaveSafetyLockdown();
}
