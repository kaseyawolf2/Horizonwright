package io.github.kaseyawolf2.horizonwright.core.base;

/** Bounded designated-tree policy; never follows a trunk outside the registered farm. */
public final class TreePlanner {

    public TreeDecision plan(NamedArea treeFarm, TreeWorkCheckpoint checkpoint, TreeObservation tree,
        SaplingReserveEvidence reserveEvidence) {
        if (treeFarm == null || checkpoint == null || tree == null || reserveEvidence == null) {
            throw new IllegalArgumentException("tree farm, checkpoint, observation, and reserve evidence are required");
        }
        if (!treeFarm.equals(checkpoint.getTreeFarm())) {
            throw new IllegalStateException("tree checkpoint belongs to a different named farm");
        }
        if (checkpoint.isComplete()) {
            throw new IllegalStateException("tree work is already complete");
        }
        checkpoint.requireCurrentObservation(tree);
        if (checkpoint.getStage() == TreeWorkStage.READY_TO_REPLANT) {
            if (!reserveEvidence.isForMaterial(tree.getRequiredSaplingFingerprint())
                || !reserveEvidence.canReplantAndPreserveReserve()) {
                return decision(
                    treeFarm,
                    checkpoint,
                    tree,
                    TreeActionKind.HOLD_SAPLING_RESERVE,
                    "required sapling reserve is no longer current",
                    reserveEvidence);
            }
            return decision(
                treeFarm,
                checkpoint,
                tree,
                TreeActionKind.PLANT_SAPLING,
                "plant the bound sapling at the captured replant position, then verify",
                reserveEvidence);
        }
        for (BasePosition block : checkpoint.getCapturedBlocks()) {
            if (!treeFarm.contains(block)) {
                return decision(
                    treeFarm,
                    checkpoint,
                    tree,
                    TreeActionKind.SKIP_OUTSIDE_FARM,
                    "tree crosses the registered farm boundary",
                    reserveEvidence);
            }
        }
        if (!treeFarm.contains(checkpoint.getReplantPosition())) {
            return decision(
                treeFarm,
                checkpoint,
                tree,
                TreeActionKind.SKIP_OUTSIDE_FARM,
                "replant position is outside the farm",
                reserveEvidence);
        }
        if (tree.isProtectedTree()) {
            return decision(
                treeFarm,
                checkpoint,
                tree,
                TreeActionKind.SKIP_PROTECTED,
                "tree is protected by policy",
                reserveEvidence);
        }
        if (!tree.isMature()) {
            return decision(
                treeFarm,
                checkpoint,
                tree,
                TreeActionKind.WAIT_GROWING,
                "tree is not mature",
                reserveEvidence);
        }
        if (!reserveEvidence.isForMaterial(tree.getRequiredSaplingFingerprint())
            || !reserveEvidence.canReplantAndPreserveReserve()) {
            return decision(
                treeFarm,
                checkpoint,
                tree,
                TreeActionKind.HOLD_SAPLING_RESERVE,
                "no verified sapling is available above reserve",
                reserveEvidence);
        }
        return decision(
            treeFarm,
            checkpoint,
            tree,
            TreeActionKind.FELL_CAPTURED_BLOCKS,
            "fell only the bounded checkpoint payload, then verify the replant position is clear",
            reserveEvidence);
    }

    private static TreeDecision decision(NamedArea treeFarm, TreeWorkCheckpoint checkpoint, TreeObservation tree,
        TreeActionKind action, String detail, SaplingReserveEvidence reserveEvidence) {
        return new TreeDecision(
            treeFarm,
            checkpoint.getWorkRevision(),
            checkpoint.getStage(),
            tree.getTreeId(),
            tree.getRevision(),
            tree.getObservationFingerprint(),
            tree.getRequiredSaplingFingerprint(),
            checkpoint.getCapturedBlocks(),
            checkpoint.getReplantPosition(),
            action,
            detail,
            reserveEvidence);
    }
}
