package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Persistable frontier that makes felling and replanting separately recoverable. */
public final class TreeWorkCheckpoint {

    private final NamedArea treeFarm;
    private final long workRevision;
    private final String treeId;
    private final String requiredSaplingFingerprint;
    private final BasePosition replantPosition;
    private final List<BasePosition> capturedBlocks;
    private final long expectedObservationRevision;
    private final String expectedObservationFingerprint;
    private final TreeWorkStage stage;

    private TreeWorkCheckpoint(NamedArea treeFarm, long workRevision, String treeId, String requiredSaplingFingerprint,
        BasePosition replantPosition, List<BasePosition> capturedBlocks, long expectedObservationRevision,
        String expectedObservationFingerprint, TreeWorkStage stage) {
        if (treeFarm == null || workRevision < 0L
            || treeId == null
            || treeId.trim()
                .isEmpty()
            || requiredSaplingFingerprint == null
            || requiredSaplingFingerprint.trim()
                .isEmpty()
            || replantPosition == null
            || capturedBlocks == null
            || capturedBlocks.isEmpty()
            || capturedBlocks.contains(null)
            || capturedBlocks.size() > TreeObservation.MAX_CAPTURED_BLOCKS
            || expectedObservationRevision < 0L
            || expectedObservationFingerprint == null
            || expectedObservationFingerprint.trim()
                .isEmpty()
            || stage == null) {
            throw new IllegalArgumentException("invalid tree work checkpoint");
        }
        this.treeFarm = treeFarm;
        this.workRevision = workRevision;
        this.treeId = treeId.trim();
        this.requiredSaplingFingerprint = requiredSaplingFingerprint.trim();
        this.replantPosition = replantPosition;
        this.capturedBlocks = Collections.unmodifiableList(new ArrayList<BasePosition>(capturedBlocks));
        this.expectedObservationRevision = expectedObservationRevision;
        this.expectedObservationFingerprint = expectedObservationFingerprint.trim();
        this.stage = stage;
    }

    public static TreeWorkCheckpoint start(NamedArea treeFarm, long workRevision, TreeObservation observation) {
        if (observation == null || observation.getState() != TreeObservationState.STANDING) {
            throw new IllegalArgumentException("tree work must start from a standing-tree observation");
        }
        return restore(
            treeFarm,
            workRevision,
            observation.getTreeId(),
            observation.getRequiredSaplingFingerprint(),
            observation.getReplantPosition(),
            observation.getTreeBlocks(),
            observation.getRevision(),
            observation.getObservationFingerprint(),
            TreeWorkStage.READY_TO_FELL);
    }

    public static TreeWorkCheckpoint restore(NamedArea treeFarm, long workRevision, String treeId,
        String requiredSaplingFingerprint, BasePosition replantPosition, List<BasePosition> capturedBlocks,
        long expectedObservationRevision, String expectedObservationFingerprint, TreeWorkStage stage) {
        return new TreeWorkCheckpoint(
            treeFarm,
            workRevision,
            treeId,
            requiredSaplingFingerprint,
            replantPosition,
            capturedBlocks,
            expectedObservationRevision,
            expectedObservationFingerprint,
            stage);
    }

    public TreeWorkCheckpoint advance(TreeDecision decision, TreeObservation beforeObservation,
        TreeObservation afterObservation, SaplingReserveEvidence currentReserveEvidence) {
        if (decision == null || beforeObservation == null
            || afterObservation == null
            || currentReserveEvidence == null) {
            throw new IllegalArgumentException(
                "decision, before/after observations, and reserve evidence are required");
        }
        if (stage == TreeWorkStage.COMPLETE) {
            throw new IllegalStateException("completed tree work cannot advance");
        }
        requireCurrentObservation(beforeObservation);
        if (!decision.isCurrentFor(this, beforeObservation, currentReserveEvidence)) {
            throw new IllegalStateException("tree decision is stale or belongs to another work frontier");
        }
        requireSameTree(afterObservation);
        if (afterObservation.getRevision() <= beforeObservation.getRevision()
            || afterObservation.getObservationFingerprint()
                .equals(beforeObservation.getObservationFingerprint())) {
            throw new IllegalStateException("tree mutation requires a newer, changed postcondition observation");
        }
        if (stage == TreeWorkStage.READY_TO_FELL) {
            if (decision.getAction() != TreeActionKind.FELL_CAPTURED_BLOCKS
                || afterObservation.getState() != TreeObservationState.FELLED_CLEAR) {
                throw new IllegalStateException("felling must prove the captured tree is clear before replanting");
            }
            return next(afterObservation, TreeWorkStage.READY_TO_REPLANT);
        }
        if (decision.getAction() != TreeActionKind.PLANT_SAPLING
            || afterObservation.getState() != TreeObservationState.SAPLING_PLANTED) {
            throw new IllegalStateException("replanting must prove the required sapling is planted");
        }
        return next(afterObservation, TreeWorkStage.COMPLETE);
    }

    void requireCurrentObservation(TreeObservation observation) {
        if (observation == null || !treeId.equals(observation.getTreeId())
            || expectedObservationRevision != observation.getRevision()
            || !expectedObservationFingerprint.equals(observation.getObservationFingerprint())
            || !requiredSaplingFingerprint.equals(observation.getRequiredSaplingFingerprint())
            || !replantPosition.equals(observation.getReplantPosition())) {
            throw new IllegalStateException("tree observation does not match the durable work frontier");
        }
        TreeObservationState expectedState = stage == TreeWorkStage.READY_TO_FELL ? TreeObservationState.STANDING
            : stage == TreeWorkStage.READY_TO_REPLANT ? TreeObservationState.FELLED_CLEAR
                : TreeObservationState.SAPLING_PLANTED;
        if (observation.getState() != expectedState) {
            throw new IllegalStateException("tree observation is in the wrong lifecycle state");
        }
        if (stage == TreeWorkStage.READY_TO_FELL && !capturedBlocks.equals(observation.getTreeBlocks())) {
            throw new IllegalStateException("standing-tree blocks do not match the bounded checkpoint payload");
        }
    }

    private void requireSameTree(TreeObservation observation) {
        if (!treeId.equals(observation.getTreeId())
            || !requiredSaplingFingerprint.equals(observation.getRequiredSaplingFingerprint())
            || !replantPosition.equals(observation.getReplantPosition())) {
            throw new IllegalStateException("tree postcondition changed identity, material, or replant target");
        }
    }

    private TreeWorkCheckpoint next(TreeObservation observation, TreeWorkStage nextStage) {
        return new TreeWorkCheckpoint(
            treeFarm,
            workRevision,
            treeId,
            requiredSaplingFingerprint,
            replantPosition,
            capturedBlocks,
            observation.getRevision(),
            observation.getObservationFingerprint(),
            nextStage);
    }

    public NamedArea getTreeFarm() {
        return treeFarm;
    }

    public long getWorkRevision() {
        return workRevision;
    }

    public String getTreeId() {
        return treeId;
    }

    public String getRequiredSaplingFingerprint() {
        return requiredSaplingFingerprint;
    }

    public BasePosition getReplantPosition() {
        return replantPosition;
    }

    public List<BasePosition> getCapturedBlocks() {
        return capturedBlocks;
    }

    public long getExpectedObservationRevision() {
        return expectedObservationRevision;
    }

    public String getExpectedObservationFingerprint() {
        return expectedObservationFingerprint;
    }

    public TreeWorkStage getStage() {
        return stage;
    }

    public boolean isComplete() {
        return stage == TreeWorkStage.COMPLETE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TreeWorkCheckpoint)) {
            return false;
        }
        TreeWorkCheckpoint that = (TreeWorkCheckpoint) other;
        return workRevision == that.workRevision && expectedObservationRevision == that.expectedObservationRevision
            && treeFarm.equals(that.treeFarm)
            && treeId.equals(that.treeId)
            && requiredSaplingFingerprint.equals(that.requiredSaplingFingerprint)
            && replantPosition.equals(that.replantPosition)
            && capturedBlocks.equals(that.capturedBlocks)
            && expectedObservationFingerprint.equals(that.expectedObservationFingerprint)
            && stage == that.stage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            treeFarm,
            workRevision,
            treeId,
            requiredSaplingFingerprint,
            replantPosition,
            capturedBlocks,
            expectedObservationRevision,
            expectedObservationFingerprint,
            stage);
    }
}
