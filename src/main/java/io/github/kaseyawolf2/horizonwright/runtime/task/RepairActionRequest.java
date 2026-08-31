package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;

public final class RepairActionRequest {

    private final String requestId;
    private final long checkpointRevision;
    private final long actionEpoch;
    private final String transactionFingerprint;
    private final ContainerTransaction transaction;
    private final RepairToolSnapshot inputTool;

    public RepairActionRequest(String requestId, long checkpointRevision, long actionEpoch,
        String transactionFingerprint, ContainerTransaction transaction, RepairToolSnapshot inputTool) {
        if (requestId == null || requestId.trim()
            .isEmpty()
            || checkpointRevision <= 0L
            || actionEpoch <= 0L
            || transactionFingerprint == null
            || transactionFingerprint.trim()
                .isEmpty()
            || transaction == null
            || transaction.getActionEpoch() != actionEpoch
            || inputTool == null) {
            throw new IllegalArgumentException("complete repair action request is required");
        }
        this.requestId = requestId.trim();
        this.checkpointRevision = checkpointRevision;
        this.actionEpoch = actionEpoch;
        this.transactionFingerprint = transactionFingerprint.trim();
        this.transaction = transaction;
        this.inputTool = inputTool;
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

    public RepairToolSnapshot getInputTool() {
        return inputTool;
    }
}
