package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

/** Ordered evidence for choosing one hotbar slot without sacrificing harvest eligibility. */
final class ExcavationToolCandidateScore {

    private static final float PROGRESS_EPSILON = 0.000001F;

    private final int slot;
    private final boolean usable;
    private final boolean canHarvest;
    private final float progressPerTick;
    private final boolean effectiveToolClass;
    private final double remainingFraction;
    private final boolean preferred;

    ExcavationToolCandidateScore(int slot, boolean usable, boolean canHarvest, float progressPerTick,
        boolean effectiveToolClass, double remainingFraction, boolean preferred) {
        if (slot < 0 || slot > 8
            || Float.isNaN(progressPerTick)
            || progressPerTick < 0.0F
            || Double.isNaN(remainingFraction)
            || remainingFraction < 0.0D
            || remainingFraction > 1.0D) {
            throw new IllegalArgumentException("valid hotbar tool evidence is required");
        }
        this.slot = slot;
        this.usable = usable;
        this.canHarvest = canHarvest;
        this.progressPerTick = progressPerTick;
        this.effectiveToolClass = effectiveToolClass;
        this.remainingFraction = remainingFraction;
        this.preferred = preferred;
    }

    boolean isBetterThan(ExcavationToolCandidateScore other) {
        if (other == null) return true;
        if (usable != other.usable) return usable;
        if (canHarvest != other.canHarvest) return canHarvest;
        if (Math.abs(progressPerTick - other.progressPerTick) > PROGRESS_EPSILON) {
            return progressPerTick > other.progressPerTick;
        }
        if (effectiveToolClass != other.effectiveToolClass) return effectiveToolClass;
        if (Double.compare(remainingFraction, other.remainingFraction) != 0) {
            return remainingFraction > other.remainingFraction;
        }
        if (preferred != other.preferred) return preferred;
        return slot < other.slot;
    }

    int getSlot() {
        return slot;
    }

    boolean isUsable() {
        return usable;
    }

    boolean canHarvest() {
        return canHarvest;
    }

    float getProgressPerTick() {
        return progressPerTick;
    }

    boolean isEffectiveToolClass() {
        return effectiveToolClass;
    }

    double getRemainingFraction() {
        return remainingFraction;
    }

    boolean isPreferred() {
        return preferred;
    }
}
