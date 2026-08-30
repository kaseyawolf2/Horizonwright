package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable adapter result for one designated tree. */
public final class TreeObservation {

    public static final int MAX_CAPTURED_BLOCKS = 256;

    private final String treeId;
    private final long revision;
    private final String observationFingerprint;
    private final String requiredSaplingFingerprint;
    private final List<BasePosition> treeBlocks;
    private final BasePosition replantPosition;
    private final TreeObservationState state;
    private final boolean mature;
    private final boolean protectedTree;

    public TreeObservation(String treeId, long revision, String observationFingerprint,
        String requiredSaplingFingerprint, List<BasePosition> treeBlocks, BasePosition replantPosition,
        TreeObservationState state, boolean mature, boolean protectedTree) {
        if (treeId == null || treeId.trim()
            .isEmpty()
            || revision < 0L
            || observationFingerprint == null
            || observationFingerprint.trim()
                .isEmpty()
            || requiredSaplingFingerprint == null
            || requiredSaplingFingerprint.trim()
                .isEmpty()
            || treeBlocks == null
            || treeBlocks.contains(null)
            || treeBlocks.size() > MAX_CAPTURED_BLOCKS
            || replantPosition == null
            || state == null) {
            throw new IllegalArgumentException(
                "bounded tree identity, state, material, and replant position are required");
        }
        if (state == TreeObservationState.STANDING && treeBlocks.isEmpty()) {
            throw new IllegalArgumentException("a standing tree requires at least one captured block");
        }
        if (state != TreeObservationState.STANDING && (!treeBlocks.isEmpty() || mature || protectedTree)) {
            throw new IllegalArgumentException("post-fell tree observations must not retain standing-tree state");
        }
        this.treeId = treeId.trim();
        this.revision = revision;
        this.observationFingerprint = observationFingerprint.trim();
        this.requiredSaplingFingerprint = requiredSaplingFingerprint.trim();
        this.treeBlocks = Collections.unmodifiableList(new ArrayList<BasePosition>(treeBlocks));
        this.replantPosition = replantPosition;
        this.state = state;
        this.mature = mature;
        this.protectedTree = protectedTree;
    }

    public String getTreeId() {
        return treeId;
    }

    public long getRevision() {
        return revision;
    }

    /** Opaque adapter fingerprint covering the captured blocks and their relevant state. */
    public String getObservationFingerprint() {
        return observationFingerprint;
    }

    public String getRequiredSaplingFingerprint() {
        return requiredSaplingFingerprint;
    }

    public List<BasePosition> getTreeBlocks() {
        return treeBlocks;
    }

    public BasePosition getReplantPosition() {
        return replantPosition;
    }

    public TreeObservationState getState() {
        return state;
    }

    public boolean isMature() {
        return mature;
    }

    public boolean isProtectedTree() {
        return protectedTree;
    }
}
