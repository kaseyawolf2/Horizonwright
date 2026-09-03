package io.github.kaseyawolf2.horizonwright.core.container;

import java.util.Optional;

/**
 * Correlates one exposed non-idempotent container click with its exact outbound
 * transaction number and the matching server response.
 *
 * <p>
 * A timed-out or rejected click is terminal. In particular, this class never
 * returns the same click a second time, so callers cannot turn an uncertain
 * result into an accidental duplicate mutation.
 */
public final class ContainerClickCorrelation {

    public enum State {
        READY,
        AWAITING_WRITE,
        AWAITING_CONFIRMATION,
        SERVER_ACCEPTED,
        SERVER_REJECTED_AWAITING_SYNC,
        COMPLETED,
        ABORTED
    }

    public enum WriteObservation {
        NOT_APPLICABLE,
        MATCHED,
        ACTIVE_MISMATCH
    }

    public enum ConfirmationObservation {
        NOT_APPLICABLE,
        ACCEPTED,
        REJECTED_AWAITING_SYNC
    }

    private final ContainerTransaction transaction;
    private State state = State.READY;
    private VerifiedContainerClick outstanding;
    private long deadlineNanos;
    private int windowId;
    private short actionNumber;
    private boolean authoritativeWindowResyncObserved;
    private boolean authoritativeCursorResyncObserved;

    public ContainerClickCorrelation(ContainerTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction must not be null");
        }
        this.transaction = transaction;
    }

    public synchronized Optional<VerifiedContainerClick> prepare(ContainerSnapshot observed, long currentEpoch,
        long nowNanos, long timeoutNanos) {
        if (timeoutNanos <= 0L) {
            throw new IllegalArgumentException("timeoutNanos must be positive");
        }
        expire(nowNanos);
        if (state != State.READY) {
            return Optional.empty();
        }
        Optional<VerifiedContainerClick> next = transaction.nextClick(observed, currentEpoch);
        if (!next.isPresent()) {
            synchronizeTerminalState();
            return Optional.empty();
        }
        outstanding = next.get();
        deadlineNanos = saturatingAdd(nowNanos, timeoutNanos);
        state = State.AWAITING_WRITE;
        return next;
    }

    public synchronized WriteObservation observeWrite(int observedWindowId, int slot, int mouseButton, int clickMode,
        short observedActionNumber, long nowNanos) {
        expire(nowNanos);
        if (state == State.AWAITING_CONFIRMATION || state == State.SERVER_ACCEPTED
            || state == State.SERVER_REJECTED_AWAITING_SYNC) {
            cancel("another container click escaped while a server confirmation was outstanding");
            return WriteObservation.NOT_APPLICABLE;
        }
        if (state != State.AWAITING_WRITE) {
            return WriteObservation.NOT_APPLICABLE;
        }
        ContainerSnapshot before = outstanding.getExpectedBefore();
        if (observedWindowId != before.getWindowId() || slot != outstanding.getSlot()
            || mouseButton != outstanding.getMouseButton()
            || clickMode != outstanding.getClickMode()) {
            cancel("outbound click did not match the prepared click " + outstanding.getClickId());
            return WriteObservation.ACTIVE_MISMATCH;
        }
        windowId = observedWindowId;
        actionNumber = observedActionNumber;
        state = State.AWAITING_CONFIRMATION;
        return WriteObservation.MATCHED;
    }

    public synchronized ConfirmationObservation observeConfirmation(int observedWindowId, short observedActionNumber,
        boolean accepted, long nowNanos) {
        expire(nowNanos);
        if (state != State.AWAITING_CONFIRMATION || observedWindowId != windowId
            || observedActionNumber != actionNumber) {
            return ConfirmationObservation.NOT_APPLICABLE;
        }
        if (!accepted) {
            // In 1.7.10 this flag means that the client's predicted click result did not equal
            // Container.slotClick's return value. The server-side mutation may still have happened;
            // the server follows this response with an authoritative full-window synchronization.
            // Never replay the click, and do not advance until that synchronization exactly matches
            // the prepared after-snapshot.
            authoritativeWindowResyncObserved = false;
            authoritativeCursorResyncObserved = false;
            state = State.SERVER_REJECTED_AWAITING_SYNC;
            return ConfirmationObservation.REJECTED_AWAITING_SYNC;
        }
        state = State.SERVER_ACCEPTED;
        return ConfirmationObservation.ACCEPTED;
    }

    public synchronized void observeAuthoritativeResync(int observedWindowId, long nowNanos) {
        expire(nowNanos);
        if (state == State.SERVER_REJECTED_AWAITING_SYNC && observedWindowId == windowId) {
            authoritativeWindowResyncObserved = true;
        }
    }

    public synchronized void observeAuthoritativeCursorResync(long nowNanos) {
        expire(nowNanos);
        if (state == State.SERVER_REJECTED_AWAITING_SYNC) {
            authoritativeCursorResyncObserved = true;
        }
    }

    /**
     * Accepts the resulting state only after the matching server response. A
     * same-layout snapshot that has not converged yet is left pending until the
     * deadline; a different window or layout aborts immediately.
     */
    public synchronized boolean observeSynchronizedSnapshot(ContainerSnapshot observed, long currentEpoch,
        long nowNanos) {
        expire(nowNanos);
        if (state != State.SERVER_ACCEPTED && state != State.SERVER_REJECTED_AWAITING_SYNC) {
            return false;
        }
        if (state == State.SERVER_REJECTED_AWAITING_SYNC
            && (!authoritativeWindowResyncObserved || !authoritativeCursorResyncObserved)) {
            return false;
        }
        if (currentEpoch != transaction.getActionEpoch()) {
            cancel(
                "action epoch changed from " + transaction.getActionEpoch()
                    + " to "
                    + currentEpoch
                    + " before container synchronization");
            return false;
        }
        ContainerSnapshot expected = outstanding.getExpectedAfter();
        if (!expected.sameIdentityAndLayout(observed)) {
            cancel("container identity or layout changed after click " + outstanding.getClickId());
            return false;
        }
        if (!expected.equals(observed)) {
            return false;
        }
        boolean confirmed = transaction.confirm(outstanding.getClickId(), true, observed, currentEpoch);
        outstanding = null;
        synchronizeTerminalState();
        return confirmed;
    }

    public synchronized boolean expire(long nowNanos) {
        if ((state == State.AWAITING_WRITE || state == State.AWAITING_CONFIRMATION
            || state == State.SERVER_ACCEPTED
            || state == State.SERVER_REJECTED_AWAITING_SYNC) && nowNanos - deadlineNanos >= 0L) {
            cancel("container click confirmation timed out; the click will not be resent");
            return true;
        }
        return false;
    }

    public synchronized void cancel(String reason) {
        if (state == State.COMPLETED || state == State.ABORTED) {
            return;
        }
        transaction.cancel(reason);
        state = State.ABORTED;
        outstanding = null;
    }

    public ContainerTransaction getTransaction() {
        return transaction;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized Optional<VerifiedContainerClick> getOutstandingClick() {
        return Optional.ofNullable(outstanding);
    }

    public synchronized boolean isTerminal() {
        return state == State.COMPLETED || state == State.ABORTED;
    }

    private void synchronizeTerminalState() {
        if (transaction.getState() == ContainerTransactionState.COMPLETED) {
            state = State.COMPLETED;
        } else if (transaction.getState() == ContainerTransactionState.ABORTED) {
            state = State.ABORTED;
        } else {
            state = State.READY;
        }
    }

    private static long saturatingAdd(long value, long increment) {
        if (value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }
}
