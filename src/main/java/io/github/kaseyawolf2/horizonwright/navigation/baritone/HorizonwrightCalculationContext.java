package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import baritone.api.IBaritone;
import baritone.pathing.movement.CalculationContext;

/** Per-request Baritone calculation context containing Horizonwright's integrated travel policy. */
final class HorizonwrightCalculationContext extends CalculationContext {

    private final CropsNhTravelPolicy cropsNh;

    HorizonwrightCalculationContext(IBaritone baritone, CropsNhTravelPolicy cropsNh) {
        super(baritone, true);
        if (cropsNh == null) throw new IllegalArgumentException("CropsNH travel policy is required");
        this.cropsNh = cropsNh;
    }

    @Override
    public double movementAdditionalCost(int srcX, int srcY, int srcZ, int destX, int destY, int destZ) {
        return cropsNh.landingPenalty(srcY, destY, getBlock(destX, destY, destZ));
    }
}
