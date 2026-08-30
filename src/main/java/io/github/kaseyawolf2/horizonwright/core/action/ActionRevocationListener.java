package io.github.kaseyawolf2.horizonwright.core.action;

/**
 * Receives synchronous notification after an action epoch has been invalidated.
 *
 * <p>
 * Implementations must release owned inputs and cancel backend work before
 * returning. A listener must never try to reacquire an action lease from this
 * callback.
 * </p>
 */
public interface ActionRevocationListener {

    void onActionEpochRevoked(ActionRevocation revocation);
}
