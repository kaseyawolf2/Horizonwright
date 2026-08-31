package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Durable phase for a resumable unload service. */
final class UnloadTaskCheckpoint {

    enum Phase {
        READY,
        PREPARED,
        AWAITING_CONFIRMATION
    }

    private final long revision;
    private final Phase phase;
    private final int completedTransactions;
    private final String transactionId;
    private final String transactionFingerprint;

    UnloadTaskCheckpoint(long revision, Phase phase, int completedTransactions, String transactionId,
        String transactionFingerprint) {
        if (revision < 0L || phase == null || completedTransactions < 0 || (phase != Phase.READY && revision == 0L)) {
            throw new IllegalArgumentException("invalid unload checkpoint state");
        }
        boolean hasTransaction = transactionId != null && !transactionId.trim()
            .isEmpty()
            && transactionFingerprint != null
            && !transactionFingerprint.trim()
                .isEmpty();
        if ((phase == Phase.READY) == hasTransaction) {
            throw new IllegalArgumentException("only prepared or awaiting checkpoints carry a transaction binding");
        }
        this.revision = revision;
        this.phase = phase;
        this.completedTransactions = completedTransactions;
        this.transactionId = hasTransaction ? transactionId.trim() : "";
        this.transactionFingerprint = hasTransaction ? transactionFingerprint.trim() : "";
    }

    static UnloadTaskCheckpoint initial() {
        return new UnloadTaskCheckpoint(0L, Phase.READY, 0, null, null);
    }

    long getRevision() {
        return revision;
    }

    Phase getPhase() {
        return phase;
    }

    int getCompletedTransactions() {
        return completedTransactions;
    }

    String getTransactionId() {
        return transactionId;
    }

    String getTransactionFingerprint() {
        return transactionFingerprint;
    }
}
