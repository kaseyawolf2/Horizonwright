package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;

/** Exact current and predicted repair evidence from the pinned Tinkers adapter. */
public final class RepairObservationResult {

    private final String taskId;
    private final long checkpointRevision;
    private final long actionEpoch;
    private final String stationId;
    private final int windowId;
    private final int stationSlotCount;
    private final int reservedContainerSlot;
    private final List<Integer> approvedMaterialContainerSlots;
    private final boolean recognizedLayout;
    private final RepairToolSnapshot inputTool;
    private final RepairToolSnapshot predictedOutput;
    private final int predictedMaterialConsumed;
    private final ContainerTransaction transaction;

    public RepairObservationResult(String taskId, long checkpointRevision, long actionEpoch, String stationId,
        int windowId, int stationSlotCount, int reservedContainerSlot, List<Integer> approvedMaterialContainerSlots,
        boolean recognizedLayout, RepairToolSnapshot inputTool, RepairToolSnapshot predictedOutput,
        int predictedMaterialConsumed, ContainerTransaction transaction) {
        if (taskId == null || taskId.trim()
            .isEmpty()
            || checkpointRevision < 0L
            || actionEpoch <= 0L
            || stationId == null
            || stationId.trim()
                .isEmpty()
            || windowId < 0
            || stationSlotCount < 1
            || reservedContainerSlot < 0
            || approvedMaterialContainerSlots == null
            || approvedMaterialContainerSlots.contains(null)
            || inputTool == null
            || predictedMaterialConsumed < 0) {
            throw new IllegalArgumentException("complete repair observation evidence is required");
        }
        if (new HashSet<>(approvedMaterialContainerSlots).size() != approvedMaterialContainerSlots.size()) {
            throw new IllegalArgumentException("approved repair material slots must be unique");
        }
        for (Integer slot : approvedMaterialContainerSlots) if (slot < 0) {
            throw new IllegalArgumentException("approved repair material slots must not be negative");
        }
        if ((transaction == null) != (predictedOutput == null)) {
            throw new IllegalArgumentException(
                "predicted output and transaction must either both exist or both be absent");
        }
        this.taskId = taskId.trim();
        this.checkpointRevision = checkpointRevision;
        this.actionEpoch = actionEpoch;
        this.stationId = stationId.trim();
        this.windowId = windowId;
        this.stationSlotCount = stationSlotCount;
        this.reservedContainerSlot = reservedContainerSlot;
        this.approvedMaterialContainerSlots = Collections
            .unmodifiableList(new ArrayList<>(approvedMaterialContainerSlots));
        this.recognizedLayout = recognizedLayout;
        this.inputTool = inputTool;
        this.predictedOutput = predictedOutput;
        this.predictedMaterialConsumed = predictedMaterialConsumed;
        this.transaction = transaction;
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

    public int getWindowId() {
        return windowId;
    }

    public int getStationSlotCount() {
        return stationSlotCount;
    }

    public int getReservedContainerSlot() {
        return reservedContainerSlot;
    }

    public List<Integer> getApprovedMaterialContainerSlots() {
        return approvedMaterialContainerSlots;
    }

    public boolean isRecognizedLayout() {
        return recognizedLayout;
    }

    public RepairToolSnapshot getInputTool() {
        return inputTool;
    }

    public RepairToolSnapshot getPredictedOutput() {
        return predictedOutput;
    }

    public int getPredictedMaterialConsumed() {
        return predictedMaterialConsumed;
    }

    public ContainerTransaction getTransaction() {
        return transaction;
    }
}
