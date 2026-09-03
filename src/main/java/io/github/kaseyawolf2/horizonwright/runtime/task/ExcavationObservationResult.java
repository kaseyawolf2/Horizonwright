package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;

/** Immutable observation tagged with the exact request authority that produced it. */
public final class ExcavationObservationResult {

    private final long taskRevision;
    private final long actionEpoch;
    private final String geometryKey;
    private final ExcavationFrontier startFrontier;
    private final ExcavationObservation observation;
    private final ExcavationSuspensionReason suspensionReason;
    private final int repairToolSlot;

    public ExcavationObservationResult(long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, ExcavationObservation observation) {
        this(taskRevision, actionEpoch, geometryKey, startFrontier, observation, ExcavationSuspensionReason.NONE, -1);
    }

    public ExcavationObservationResult(long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, ExcavationObservation observation,
        ExcavationSuspensionReason suspensionReason) {
        this(taskRevision, actionEpoch, geometryKey, startFrontier, observation, suspensionReason, -1);
    }

    public ExcavationObservationResult(long taskRevision, long actionEpoch, String geometryKey,
        ExcavationFrontier startFrontier, ExcavationObservation observation,
        ExcavationSuspensionReason suspensionReason, int repairToolSlot) {
        if (taskRevision < 1L) {
            throw new IllegalArgumentException("taskRevision must be positive");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        this.taskRevision = taskRevision;
        this.actionEpoch = actionEpoch;
        this.geometryKey = requireText(geometryKey, "geometryKey");
        this.startFrontier = Objects.requireNonNull(startFrontier, "startFrontier");
        this.observation = Objects.requireNonNull(observation, "observation");
        this.suspensionReason = Objects.requireNonNull(suspensionReason, "suspensionReason");
        if (suspensionReason != ExcavationSuspensionReason.REPAIR_REQUIRED && repairToolSlot >= 0) {
            throw new IllegalArgumentException("only a repair suspension may identify a repair tool slot");
        }
        if (repairToolSlot > 35) throw new IllegalArgumentException("repair tool slot must be from 0 to 35");
        this.repairToolSlot = repairToolSlot;
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

    public ExcavationObservation getObservation() {
        return observation;
    }

    public ExcavationSuspensionReason getSuspensionReason() {
        return suspensionReason;
    }

    public int getRepairToolSlot() {
        if (suspensionReason != ExcavationSuspensionReason.REPAIR_REQUIRED) {
            throw new IllegalStateException("observation did not request tool repair");
        }
        return repairToolSlot;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
