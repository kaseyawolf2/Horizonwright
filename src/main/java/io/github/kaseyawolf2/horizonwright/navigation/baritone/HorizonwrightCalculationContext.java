package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import baritone.api.IBaritone;
import baritone.api.pathing.movement.ActionCosts;
import baritone.pathing.movement.CalculationContext;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

/** Per-request Baritone calculation context containing Horizonwright's integrated travel policy. */
final class HorizonwrightCalculationContext extends CalculationContext {

    private final CropsNhTravelPolicy cropsNh;
    private ScaffoldLedger scaffolds;
    private java.util.List<BlockPosition> exclusions = java.util.Collections.emptyList();

    HorizonwrightCalculationContext withScaffolds(ScaffoldLedger ledger, java.util.List<BlockPosition> excluded) {
        scaffolds = ledger;
        exclusions = excluded;
        return this;
    }

    @Override
    public double costOfPlacingAt(int x, int y, int z, baritone.compat.IBlockState state) {
        // Recordable scaffolds occupy previously empty cells; do not replace plants or snow.
        if (scaffolds != null && state.getBlock() != net.minecraft.init.Blocks.air) return ActionCosts.COST_INF;
        for (BlockPosition pos : exclusions) if (x == pos.getX() && z == pos.getZ()) return ActionCosts.COST_INF;
        return super.costOfPlacingAt(x, y, z, state);
    }

    @Override
    public double breakCostMultiplierAt(int x, int y, int z, baritone.compat.IBlockState state) {
        if (scaffolds != null
            && scaffolds.matches(
                new BlockPosition(x, y, z),
                net.minecraft.block.Block.blockRegistry.getNameForObject(state.getBlock()) + ":" + state.getMeta())
            && !isPossiblyProtected(x, y, z)) return 1;
        return super.breakCostMultiplierAt(x, y, z, state);
    }

    HorizonwrightCalculationContext(IBaritone baritone, CropsNhTravelPolicy cropsNh) {
        super(baritone, true);
        if (cropsNh == null) throw new IllegalArgumentException("CropsNH travel policy is required");
        this.cropsNh = cropsNh;
    }

    @Override
    public double movementAdditionalCost(int srcX, int srcY, int srcZ, int destX, int destY, int destZ) {
        if (QuicksandTravelPolicy.crossesQuicksand(
            srcX,
            srcY,
            srcZ,
            destX,
            destY,
            destZ,
            (x, y, z) -> QuicksandTravelPolicy.isHazard(get(x, y, z).getBlock(), get(x, y, z).getMeta()))) {
            return ActionCosts.COST_INF;
        }
        return cropsNh.landingPenalty(srcY, destY, getBlock(destX, destY, destZ));
    }
}
