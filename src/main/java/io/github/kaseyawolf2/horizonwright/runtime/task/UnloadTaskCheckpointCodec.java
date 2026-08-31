package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Strict checkpoint codec for the non-idempotent unload preparation boundary. */
final class UnloadTaskCheckpointCodec {

    private static final String PHASE = "phase";
    private static final String LOADOUT_ID = "loadoutId";
    private static final String STORAGE_ID = "storageId";
    private static final String COMPLETED_TRANSACTIONS = "completedTransactions";
    private static final String TRANSACTION_ID = "transactionId";
    private static final String TRANSACTION_FINGERPRINT = "transactionFingerprint";

    private UnloadTaskCheckpointCodec() {}

    static TaskCheckpoint encode(TaskSpec spec, UnloadTaskCheckpoint state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(
            PHASE,
            state.getPhase()
                .name());
        values.put(LOADOUT_ID, UnloadTask.loadoutId(spec));
        values.put(STORAGE_ID, UnloadTask.storageId(spec));
        values.put(COMPLETED_TRANSACTIONS, Integer.toString(state.getCompletedTransactions()));
        if (state.getPhase() != UnloadTaskCheckpoint.Phase.READY) {
            values.put(TRANSACTION_ID, state.getTransactionId());
            values.put(TRANSACTION_FINGERPRINT, state.getTransactionFingerprint());
        }
        return new TaskCheckpoint(state.getRevision(), values);
    }

    static UnloadTaskCheckpoint decode(TaskSpec spec, TaskCheckpoint checkpoint) {
        if (checkpoint.getRevision() == 0L && checkpoint.getValues()
            .isEmpty()) {
            return UnloadTaskCheckpoint.initial();
        }
        Map<String, String> values = checkpoint.getValues();
        if (!UnloadTask.loadoutId(spec)
            .equals(require(values, LOADOUT_ID))
            || !UnloadTask.storageId(spec)
                .equals(require(values, STORAGE_ID))) {
            throw new IllegalArgumentException("unload checkpoint belongs to another loadout or storage destination");
        }
        UnloadTaskCheckpoint.Phase phase;
        try {
            phase = UnloadTaskCheckpoint.Phase.valueOf(require(values, PHASE));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid unload checkpoint phase", failure);
        }
        int completed;
        try {
            completed = Integer.parseInt(require(values, COMPLETED_TRANSACTIONS));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid unload completed transaction count", failure);
        }
        String transactionId = phase == UnloadTaskCheckpoint.Phase.READY ? null : require(values, TRANSACTION_ID);
        String fingerprint = phase == UnloadTaskCheckpoint.Phase.READY ? null
            : require(values, TRANSACTION_FINGERPRINT);
        return new UnloadTaskCheckpoint(checkpoint.getRevision(), phase, completed, transactionId, fingerprint);
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("missing unload checkpoint field " + key);
        }
        return value.trim();
    }
}
