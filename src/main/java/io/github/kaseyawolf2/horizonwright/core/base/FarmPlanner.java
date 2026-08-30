package io.github.kaseyawolf2.horizonwright.core.base;

/** Pure policy for one finite named-plot observation. */
public final class FarmPlanner {

    public FarmDecision plan(NamedArea plot, FarmPassCheckpoint checkpoint, CropObservation crop,
        SeedReserveEvidence reserveEvidence) {
        if (plot == null || checkpoint == null || crop == null || reserveEvidence == null) {
            throw new IllegalArgumentException("plot, checkpoint, crop, and seed reserve evidence are required");
        }
        checkpoint.requireCurrentObservation(plot, crop);
        BasePosition target = crop.getPosition();
        if (!plot.contains(target)) {
            return decision(
                checkpoint,
                crop,
                FarmActionKind.SKIP_OUTSIDE_PLOT,
                "crop is outside named plot",
                reserveEvidence);
        }
        if (crop.isProtectedBlock()) {
            return decision(
                checkpoint,
                crop,
                FarmActionKind.SKIP_PROTECTED,
                "crop is protected by policy",
                reserveEvidence);
        }
        if (!crop.isMaturityKnown()) {
            return decision(
                checkpoint,
                crop,
                FarmActionKind.HOLD_FOR_ADAPTER,
                "adapter could not prove maturity",
                reserveEvidence);
        }
        if (!crop.isMature()) {
            return decision(checkpoint, crop, FarmActionKind.WAIT_GROWING, "crop is not mature", reserveEvidence);
        }
        if (usesNonDestructiveHarvest(crop.getFamily())) {
            return decision(
                checkpoint,
                crop,
                FarmActionKind.RIGHT_CLICK_HARVEST,
                crop.getFamily() == CropFamily.PAM_FRUITING_LOG
                    ? "right-click mature fruit; never break the fruiting log"
                    : "right-click mature adapter crop",
                reserveEvidence);
        }
        if (!reserveEvidence.isForMaterial(crop.getRequiredSeedFingerprint())
            || !reserveEvidence.canReplantAndPreserveReserve()) {
            return decision(
                checkpoint,
                crop,
                FarmActionKind.HOLD_REPLANT_RESERVE,
                "no verified seed is available above reserve",
                reserveEvidence);
        }
        return decision(
            checkpoint,
            crop,
            FarmActionKind.BREAK_AND_REPLANT,
            "break, replant, then verify both states",
            reserveEvidence);
    }

    private static FarmDecision decision(FarmPassCheckpoint checkpoint, CropObservation crop, FarmActionKind action,
        String detail, SeedReserveEvidence reserveEvidence) {
        return new FarmDecision(
            checkpoint.getPlot(),
            checkpoint.getPassRevision(),
            checkpoint.getNextObservationIndex(),
            crop.getObservationFingerprint(),
            crop.getRequiredSeedFingerprint(),
            crop.getPosition(),
            action,
            detail,
            reserveEvidence);
    }

    private static boolean usesNonDestructiveHarvest(CropFamily family) {
        return family == CropFamily.PAM_CROP || family == CropFamily.PAM_HANGING_FRUIT
            || family == CropFamily.PAM_FRUITING_LOG
            || family == CropFamily.CROPS_NH;
    }
}
