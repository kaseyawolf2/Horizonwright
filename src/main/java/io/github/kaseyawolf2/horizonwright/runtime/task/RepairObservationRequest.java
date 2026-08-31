package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Bound request for exact pinned-layout repair evidence and click predictions. */
public final class RepairObservationRequest {

    private final String taskId;
    private final long checkpointRevision;
    private final long actionEpoch;
    private final String stationId;
    private final int reservedInventorySlot;

    public RepairObservationRequest(String taskId, long checkpointRevision, long actionEpoch, String stationId,
        int reservedInventorySlot) {
        if (taskId == null || taskId.trim()
            .isEmpty()
            || checkpointRevision < 0L
            || actionEpoch <= 0L
            || stationId == null
            || stationId.trim()
                .isEmpty()
            || reservedInventorySlot < 0) {
            throw new IllegalArgumentException("valid repair observation binding is required");
        }
        this.taskId = taskId.trim();
        this.checkpointRevision = checkpointRevision;
        this.actionEpoch = actionEpoch;
        this.stationId = stationId.trim();
        this.reservedInventorySlot = reservedInventorySlot;
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

    public String getStationId() {
        return stationId;
    }

    public int getReservedInventorySlot() {
        return reservedInventorySlot;
    }
}
