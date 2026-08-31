package io.github.kaseyawolf2.horizonwright.runtime.task;

final class RepairTaskCheckpoint {

    enum Phase {
        READY,
        PREPARED,
        AWAITING_CONFIRMATION
    }

    private final long revision;
    private final Phase phase;
    private final int completedRepairs;
    private final String transactionId;
    private final String operationFingerprint;

    RepairTaskCheckpoint(long revision, Phase phase, int completedRepairs, String transactionId,
        String operationFingerprint) {
        boolean binding = transactionId != null && !transactionId.trim()
            .isEmpty()
            && operationFingerprint != null
            && !operationFingerprint.trim()
                .isEmpty();
        if (revision < 0L || phase == null
            || completedRepairs < 0
            || (phase != Phase.READY && revision == 0L)
            || ((phase == Phase.READY) == binding)) throw new IllegalArgumentException("invalid repair checkpoint");
        this.revision = revision;
        this.phase = phase;
        this.completedRepairs = completedRepairs;
        this.transactionId = binding ? transactionId.trim() : "";
        this.operationFingerprint = binding ? operationFingerprint.trim() : "";
    }

    static RepairTaskCheckpoint initial() {
        return new RepairTaskCheckpoint(0L, Phase.READY, 0, null, null);
    }

    long getRevision() {
        return revision;
    }

    Phase getPhase() {
        return phase;
    }

    int getCompletedRepairs() {
        return completedRepairs;
    }

    String getTransactionId() {
        return transactionId;
    }

    String getOperationFingerprint() {
        return operationFingerprint;
    }
}
