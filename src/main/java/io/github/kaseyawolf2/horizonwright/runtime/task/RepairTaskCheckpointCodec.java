package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

final class RepairTaskCheckpointCodec {

    private RepairTaskCheckpointCodec() {}

    static TaskCheckpoint encode(TaskSpec spec, RepairTaskCheckpoint state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(
            "phase",
            state.getPhase()
                .name());
        values.put("stationId", RepairTask.stationId(spec));
        values.put("reservedInventorySlot", Integer.toString(RepairTask.reservedInventorySlot(spec)));
        values.put("predictedWorkDamage", Integer.toString(RepairTask.predictedWorkDamage(spec)));
        values.put("completedRepairs", Integer.toString(state.getCompletedRepairs()));
        if (state.getPhase() != RepairTaskCheckpoint.Phase.READY) {
            values.put("transactionId", state.getTransactionId());
            values.put("operationFingerprint", state.getOperationFingerprint());
        }
        return new TaskCheckpoint(state.getRevision(), values);
    }

    static RepairTaskCheckpoint decode(TaskSpec spec, TaskCheckpoint checkpoint) {
        if (checkpoint.getRevision() == 0L && checkpoint.getValues()
            .isEmpty()) return RepairTaskCheckpoint.initial();
        Map<String, String> values = checkpoint.getValues();
        if (!RepairTask.stationId(spec)
            .equals(require(values, "stationId"))
            || RepairTask.reservedInventorySlot(spec) != integer(values, "reservedInventorySlot")
            || RepairTask.predictedWorkDamage(spec) != integer(values, "predictedWorkDamage")) {
            throw new IllegalArgumentException("repair checkpoint belongs to another task configuration");
        }
        RepairTaskCheckpoint.Phase phase;
        try {
            phase = RepairTaskCheckpoint.Phase.valueOf(require(values, "phase"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid repair phase", failure);
        }
        int completed = integer(values, "completedRepairs");
        String transactionId = phase == RepairTaskCheckpoint.Phase.READY ? null : require(values, "transactionId");
        String fingerprint = phase == RepairTaskCheckpoint.Phase.READY ? null : require(values, "operationFingerprint");
        return new RepairTaskCheckpoint(checkpoint.getRevision(), phase, completed, transactionId, fingerprint);
    }

    private static int integer(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(require(values, key));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid repair field " + key, failure);
        }
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException("missing repair field " + key);
        return value.trim();
    }
}
