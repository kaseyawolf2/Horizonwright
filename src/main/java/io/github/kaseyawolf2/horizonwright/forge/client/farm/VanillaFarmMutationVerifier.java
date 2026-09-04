package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.FarmDecision;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;

/** Minecraft-independent last-moment and post-action proof checks for vanilla harvesting. */
final class VanillaFarmMutationVerifier {

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
}
