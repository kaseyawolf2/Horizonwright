package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.FarmDecision;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;

/** Minecraft-independent last-moment and post-action proof checks for vanilla harvesting. */
final class VanillaFarmMutationVerifier {

    boolean isUnchanged(CropObservation before, CropObservation after) {
        if (before == null || after == null) {
            throw new IllegalArgumentException("complete crop synchronization evidence is required");
        }
        return before.equals(after);
    }

    void requireCurrent(FarmDecision decision, CropObservation current, SeedReserveEvidence reserve,
        String hotbarSeedFingerprint) {
        if (decision == null || current == null || reserve == null) {
            throw new IllegalArgumentException("complete current farm authority evidence is required");
        }
        if (!decision.isCurrentFor(decision.getPlot(), current, reserve)) {
            throw new IllegalStateException("farm target or seed inventory changed after planning");
        }
        if (!current.isMaturityKnown() || !current.isMature() || current.isProtectedBlock()) {
            throw new IllegalStateException("only a verified mature unprotected crop may be mutated");
        }
        if (decision.getAction() == FarmActionKind.BREAK_AND_REPLANT && !decision.getRequiredSeedFingerprint()
            .equals(hotbarSeedFingerprint)) {
            throw new IllegalStateException("hotbar seed identity does not match the planned replant material");
        }
    }

    void requireReplacement(FarmDecision decision, CropObservation before, CropObservation after) {
        if (decision == null || before == null || after == null) {
            throw new IllegalArgumentException("complete before and after crop evidence is required");
        }
        if (!decision.getTarget()
            .equals(after.getPosition())
            || before.getFamily() != after.getFamily()
            || !decision.getRequiredSeedFingerprint()
                .equals(after.getRequiredSeedFingerprint())
            || !after.isMaturityKnown()
            || after.isMature()
            || after.isProtectedBlock()
            || before.getObservationFingerprint()
                .equals(after.getObservationFingerprint())) {
            throw new IllegalStateException("replacement crop does not satisfy the exact immature postcondition");
        }
    }

    /** A confirmed exact swap may change slot ordering, while material identity, quantity and reserve stay fixed. */
    void requireCurrentAfterVerifiedSeedSwap(FarmDecision decision, CropObservation current,
        SeedReserveEvidence reserve, String hotbarSeedFingerprint, ContainerTransaction transaction,
        ContainerSnapshot synchronizedInventory) {
        if (transaction == null || transaction.getState() != ContainerTransactionState.COMPLETED
            || transaction.getClicks()
                .size() != 1
            || transaction.getClicks()
                .get(0)
                .getClickMode() != 2
            || !transaction.getClicks()
                .get(0)
                .getExpectedAfter()
                .equals(synchronizedInventory))
            throw new IllegalStateException("The exact seed swap has not been confirmed by the server");
        SeedReserveEvidence original = decision.getReserveEvidence();
        if (reserve == null || !original.getSeedFingerprint()
            .equals(reserve.getSeedFingerprint())
            || original.getAvailableSeeds() != reserve.getAvailableSeeds()
            || original.getMinimumReserve() != reserve.getMinimumReserve()
            || original.getInventoryRevision() != reserve.getInventoryRevision()
            || !reserve.canReplantAndPreserveReserve())
            throw new IllegalStateException("The replant reserve changed during seed staging");
        requireCurrent(decision, current, original, hotbarSeedFingerprint);
    }
}
