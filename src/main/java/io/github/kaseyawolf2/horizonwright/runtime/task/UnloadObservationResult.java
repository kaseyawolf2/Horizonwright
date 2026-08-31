package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadClickPrediction;

/** Immutable client-thread evidence and adapter predictions for one unload observation. */
public final class UnloadObservationResult {

    private final String taskId;
    private final long checkpointRevision;
    private final long actionEpoch;
    private final String storageId;
    private final NamedLoadout loadout;
    private final List<ItemFingerprint> playerSlots;
    private final StorageItemFilter destinationFilter;
    private final List<UnloadClickPrediction> predictions;

    public UnloadObservationResult(String taskId, long checkpointRevision, long actionEpoch, String storageId,
        NamedLoadout loadout, List<ItemFingerprint> playerSlots, StorageItemFilter destinationFilter,
        List<UnloadClickPrediction> predictions) {
        this.taskId = requireText(taskId, "taskId");
        if (checkpointRevision < 0L || actionEpoch <= 0L
            || loadout == null
            || playerSlots == null
            || destinationFilter == null
            || predictions == null
            || predictions.contains(null)) {
            throw new IllegalArgumentException("complete unload observation evidence is required");
        }
        this.checkpointRevision = checkpointRevision;
        this.actionEpoch = actionEpoch;
        this.storageId = requireText(storageId, "storageId");
        this.loadout = loadout;
        this.playerSlots = Collections.unmodifiableList(new ArrayList<>(playerSlots));
        this.destinationFilter = destinationFilter;
        this.predictions = Collections.unmodifiableList(new ArrayList<>(predictions));
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

    public String getStorageId() {
        return storageId;
    }

    public NamedLoadout getLoadout() {
        return loadout;
    }

    public List<ItemFingerprint> getPlayerSlots() {
        return playerSlots;
    }

    public StorageItemFilter getDestinationFilter() {
        return destinationFilter;
    }

    public List<UnloadClickPrediction> getPredictions() {
        return predictions;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
