package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TreeDecision {

    private final NamedArea treeFarm;
    private final long workRevision;
    private final TreeWorkStage workStage;
    private final String treeId;
    private final long observationRevision;
    private final String observationFingerprint;
    private final String requiredSaplingFingerprint;
    private final List<BasePosition> capturedBlocks;
    private final BasePosition replantPosition;
    private final TreeActionKind action;
    private final String detail;
    private final SaplingReserveEvidence reserveEvidence;

    TreeDecision(NamedArea treeFarm, long workRevision, TreeWorkStage workStage, String treeId,
        long observationRevision, String observationFingerprint, String requiredSaplingFingerprint,
        List<BasePosition> capturedBlocks, BasePosition replantPosition, TreeActionKind action, String detail,
        SaplingReserveEvidence reserveEvidence) {
        if (treeFarm == null || workRevision < 0L
            || workStage == null
            || treeId == null
            || treeId.trim()
                .isEmpty()
            || observationRevision < 0L
            || observationFingerprint == null
            || observationFingerprint.trim()
                .isEmpty()
            || requiredSaplingFingerprint == null
            || requiredSaplingFingerprint.trim()
                .isEmpty()
            || capturedBlocks == null
            || capturedBlocks.contains(null)
            || capturedBlocks.size() > TreeObservation.MAX_CAPTURED_BLOCKS
            || replantPosition == null
            || action == null
            || detail == null
            || detail.trim()
                .isEmpty()
            || reserveEvidence == null) {
            throw new IllegalArgumentException("tree decision bindings are required");
        }
        this.treeFarm = treeFarm;
        this.workRevision = workRevision;
        this.workStage = workStage;
        this.treeId = treeId;
        this.observationRevision = observationRevision;
        this.observationFingerprint = observationFingerprint.trim();
        this.requiredSaplingFingerprint = requiredSaplingFingerprint.trim();
        this.capturedBlocks = Collections.unmodifiableList(new ArrayList<BasePosition>(capturedBlocks));
        this.replantPosition = replantPosition;
        this.action = action;
        this.detail = detail;
        this.reserveEvidence = reserveEvidence;
    }

    public NamedArea getTreeFarm() {
        return treeFarm;
    }

    public long getWorkRevision() {
        return workRevision;
    }

    public TreeWorkStage getWorkStage() {
        return workStage;
    }

    public String getTreeId() {
        return treeId;
    }

    public long getObservationRevision() {
        return observationRevision;
    }

    public String getObservationFingerprint() {
        return observationFingerprint;
    }

    public String getRequiredSaplingFingerprint() {
        return requiredSaplingFingerprint;
    }

    public List<BasePosition> getCapturedBlocks() {
        return capturedBlocks;
    }

    public BasePosition getReplantPosition() {
        return replantPosition;
    }

    public TreeActionKind getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public SaplingReserveEvidence getReserveEvidence() {
        return reserveEvidence;
    }

    public boolean requiresMutation() {
        return action == TreeActionKind.FELL_CAPTURED_BLOCKS || action == TreeActionKind.PLANT_SAPLING;
    }

    public boolean requiresPostconditionVerification() {
        return requiresMutation();
    }

    public boolean isCurrentFor(TreeWorkCheckpoint currentCheckpoint, TreeObservation currentObservation,
        SaplingReserveEvidence currentReserveEvidence) {
        return currentCheckpoint != null && currentObservation != null
            && currentReserveEvidence != null
            && treeFarm.equals(currentCheckpoint.getTreeFarm())
            && workRevision == currentCheckpoint.getWorkRevision()
            && workStage == currentCheckpoint.getStage()
            && treeId.equals(currentObservation.getTreeId())
            && observationRevision == currentObservation.getRevision()
            && observationFingerprint.equals(currentObservation.getObservationFingerprint())
            && requiredSaplingFingerprint.equals(currentObservation.getRequiredSaplingFingerprint())
            && requiredSaplingFingerprint.equals(currentCheckpoint.getRequiredSaplingFingerprint())
            && currentReserveEvidence.isForMaterial(requiredSaplingFingerprint)
            && reserveEvidence.isSameSnapshot(currentReserveEvidence);
    }
}
