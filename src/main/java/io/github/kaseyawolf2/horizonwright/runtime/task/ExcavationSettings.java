package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Validation and checkpoint migration for the operator settings editor. */
public final class ExcavationSettings {

    private ExcavationSettings() {}

    public static TaskCheckpoint checkpointFor(TaskSpec original, TaskSpec replacement, TaskCheckpoint checkpoint) {
        CylinderExcavationSpec before = ExcavationTask.parse(original), after = ExcavationTask.parse(replacement);
        if (!original.getId()
            .equals(replacement.getId()) || original.getLane() != replacement.getLane())
            throw new IllegalArgumentException("Settings cannot change task identity or priority lane.");
        java.util.Set<String> editable = new java.util.HashSet<>(
            java.util.Arrays.asList(
                "traversal",
                "spiralWidth",
                "movingMining",
                "service.loadoutId",
                "service.storageId",
                "service.repairStationId",
                "service.reservedToolSlot",
                "service.predictedWorkDamage"));
        java.util.Map<String, String> oldFixed = new java.util.LinkedHashMap<>(original.getParameters());
        java.util.Map<String, String> newFixed = new java.util.LinkedHashMap<>(replacement.getParameters());
        editable.forEach(key -> {
            oldFixed.remove(key);
            newFixed.remove(key);
        });
        if (!oldFixed.equals(newFixed))
            throw new IllegalArgumentException("Area and quarry infrastructure cannot change during a run.");
        String walking = replacement.getParameters()
            .getOrDefault("movingMining", "false");
        if (!"true".equals(walking) && !"false".equals(walking))
            throw new IllegalArgumentException("Invalid walking setting.");
        ExcavationTask.servicePolicy(replacement);
        ExcavationTask.managedConfiguration(replacement);
        if (InventoryPreparingTaskRunner.isPreparing(checkpoint))
            throw new IllegalStateException("Finish inventory recovery before editing settings.");
        if (before.getGeometryKey()
            .equals(after.getGeometryKey())) return checkpoint;
        TaskCheckpoint inner = InventoryPreparingTaskRunner.unwrap(checkpoint);
        ExcavationCheckpoint old = ExcavationTaskCheckpointCodec.decode(before, inner);
        // A new order cannot reuse the old prefix: rescan existing air under fresh, monotonic authority.
        return ExcavationTaskCheckpointCodec.encode(
            after,
            ExcavationCheckpoint
                .start(after, checkpoint.getRevision() + 1, old == null ? 1 : old.getActionEpoch() + 1));
    }
}
