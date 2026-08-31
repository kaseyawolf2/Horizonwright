package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Epoch- and checkpoint-bound request for current player/storage container evidence. */
public final class UnloadObservationRequest {

    private final String taskId;
    private final long checkpointRevision;
    private final long actionEpoch;
    private final String loadoutId;
    private final String storageId;

    public UnloadObservationRequest(String taskId, long checkpointRevision, long actionEpoch, String loadoutId,
        String storageId) {
        this.taskId = requireText(taskId, "taskId");
        if (checkpointRevision < 0L || actionEpoch <= 0L) {
            throw new IllegalArgumentException("checkpoint revision and action epoch are invalid");
        }
        this.checkpointRevision = checkpointRevision;
        this.actionEpoch = actionEpoch;
        this.loadoutId = requireText(loadoutId, "loadoutId");
        this.storageId = requireText(storageId, "storageId");
    }

    public String getTaskId() {
        return taskId;
    }

    public long getCheckpointRevision() {
        return checkpointRevision;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public String getLoadoutId() {
        return loadoutId;
    }

    public String getStorageId() {
        return storageId;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
