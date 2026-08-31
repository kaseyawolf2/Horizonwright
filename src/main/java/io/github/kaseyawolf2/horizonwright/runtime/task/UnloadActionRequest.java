package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;

/** Exact prepared transaction submitted only after its fingerprint was checkpointed. */
public final class UnloadActionRequest {

    private final String requestId;
    private final long checkpointRevision;
    private final long actionEpoch;
    private final String transactionFingerprint;
    private final ContainerTransaction transaction;

    public UnloadActionRequest(String requestId, long checkpointRevision, long actionEpoch,
        String transactionFingerprint, ContainerTransaction transaction) {
        this.requestId = requireText(requestId, "requestId");
        if (checkpointRevision <= 0L || actionEpoch <= 0L
            || transaction == null
            || transaction.getActionEpoch() != actionEpoch) {
            throw new IllegalArgumentException("valid checkpoint, epoch, and transaction are required");
        }
        this.checkpointRevision = checkpointRevision;
        this.actionEpoch = actionEpoch;
        this.transactionFingerprint = requireText(transactionFingerprint, "transactionFingerprint");
        this.transaction = transaction;
    }

    public String getRequestId() {
        return requestId;
    }

    public long getCheckpointRevision() {
        return checkpointRevision;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public String getTransactionFingerprint() {
        return transactionFingerprint;
    }

    public ContainerTransaction getTransaction() {
        return transaction;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
