package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;

/** Immutable world evidence for one planned infrastructure position. */
public final class ManagedQuarryObservationResult {

    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final BlockPosition position;
    private final String blockFingerprint;
    private final boolean approvedMaterialPresent;

    public ManagedQuarryObservationResult(long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, BlockPosition position, String blockFingerprint,
        boolean approvedMaterialPresent) {
        if (taskRevision < 1L) throw new IllegalArgumentException("taskRevision must be positive");
        if (actionEpoch < 1L) throw new IllegalArgumentException("actionEpoch must be positive");
        this.taskRevision = taskRevision;
        this.actionEpoch = actionEpoch;
        this.geometryKey = requireText(geometryKey, "geometryKey");
        this.startFrontier = Objects.requireNonNull(startFrontier, "startFrontier");
        this.position = Objects.requireNonNull(position, "position");
        this.blockFingerprint = requireText(blockFingerprint, "blockFingerprint");
        this.approvedMaterialPresent = approvedMaterialPresent;
    }

    public long getTaskRevision() {
        return taskRevision;
    }

    public long getActionEpoch() {
        return actionEpoch;
    }

    public String getGeometryKey() {
        return geometryKey;
    }

    public ExcavationFrontier getStartFrontier() {
        return startFrontier;
    }

    public BlockPosition getPosition() {
        return position;
    }

    public String getBlockFingerprint() {
        return blockFingerprint;
    }

    public boolean isApprovedMaterialPresent() {
        return approvedMaterialPresent;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
