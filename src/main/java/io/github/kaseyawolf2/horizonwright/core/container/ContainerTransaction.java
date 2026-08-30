package io.github.kaseyawolf2.horizonwright.core.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fail-closed verifier for a planned container mutation.
 *
 * <p>
 * The caller may execute only the click returned by {@link #nextClick}; no
 * subsequent click is exposed until the server accepts the first and the exact
 * expected snapshot is observed.
 */
public final class ContainerTransaction {

    private final String transactionId;
    private final long actionEpoch;
    private final List<VerifiedContainerClick> clicks;
    private ContainerTransactionState state;
    private int nextIndex;
    private String abortReason = "";

    public ContainerTransaction(String transactionId, long actionEpoch, List<VerifiedContainerClick> clicks) {
        if (transactionId == null || transactionId.trim()
            .isEmpty() || actionEpoch <= 0L || clicks == null || clicks.isEmpty() || clicks.contains(null)) {
            throw new IllegalArgumentException("transactionId, a positive epoch, and clicks are required");
        }
        List<VerifiedContainerClick> copy = new ArrayList<VerifiedContainerClick>(clicks);
        Set<String> clickIds = new HashSet<String>();
        for (VerifiedContainerClick click : copy) {
            if (!clickIds.add(click.getClickId())) {
                throw new IllegalArgumentException("click IDs must be unique");
            }
        }
        for (int index = 1; index < copy.size(); index++) {
            VerifiedContainerClick previous = copy.get(index - 1);
            VerifiedContainerClick current = copy.get(index);
            if (!previous.getExpectedAfter()
                .equals(current.getExpectedBefore())) {
                throw new IllegalArgumentException("adjacent click snapshots must form one exact chain");
            }
        }
        this.transactionId = transactionId.trim();
        this.actionEpoch = actionEpoch;
        this.clicks = Collections.unmodifiableList(copy);
        this.state = ContainerTransactionState.READY;
    }

    public synchronized Optional<VerifiedContainerClick> nextClick(ContainerSnapshot observed, long currentEpoch) {
        if (state == ContainerTransactionState.COMPLETED || state == ContainerTransactionState.ABORTED
            || state == ContainerTransactionState.AWAITING_CONFIRMATION) {
            return Optional.empty();
        }
        if (!validateEpoch(currentEpoch) || !clicks.get(nextIndex)
            .getExpectedBefore()
            .equals(observed)) {
            if (state != ContainerTransactionState.ABORTED) {
                abort(
                    "container changed before click " + clicks.get(nextIndex)
                        .getClickId());
            }
            return Optional.empty();
        }
        state = ContainerTransactionState.AWAITING_CONFIRMATION;
        return Optional.of(clicks.get(nextIndex));
    }

    public synchronized boolean confirm(String clickId, boolean accepted, ContainerSnapshot observed,
        long currentEpoch) {
        if (state != ContainerTransactionState.AWAITING_CONFIRMATION) {
            return false;
        }
        VerifiedContainerClick click = clicks.get(nextIndex);
        if (!click.getClickId()
            .equals(clickId)) {
            abort("confirmation did not match the outstanding click");
            return false;
        }
        if (!validateEpoch(currentEpoch)) {
            return false;
        }
        if (!accepted) {
            abort("server rejected click " + clickId);
            return false;
        }
        if (!click.getExpectedAfter()
            .equals(observed)) {
            abort("container changed unexpectedly after click " + clickId);
            return false;
        }
        nextIndex++;
        state = nextIndex == clicks.size() ? ContainerTransactionState.COMPLETED : ContainerTransactionState.READY;
        return true;
    }

    public synchronized void cancel(String reason) {
        if (state != ContainerTransactionState.COMPLETED && state != ContainerTransactionState.ABORTED) {
            abort(
                reason == null || reason.trim()
                    .isEmpty() ? "cancelled" : reason.trim());
        }
    }

    public String getTransactionId() {
        return transactionId;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public List<VerifiedContainerClick> getClicks() {
        return clicks;
    }

    public synchronized ContainerTransactionState getState() {
        return state;
    }

    public synchronized int getCompletedClickCount() {
        return nextIndex;
    }

    public synchronized String getAbortReason() {
        return abortReason;
    }

    private boolean validateEpoch(long currentEpoch) {
        if (currentEpoch != actionEpoch) {
            abort("action epoch changed from " + actionEpoch + " to " + currentEpoch);
            return false;
        }
        return true;
    }

    private void abort(String reason) {
        state = ContainerTransactionState.ABORTED;
        abortReason = reason;
    }
}
