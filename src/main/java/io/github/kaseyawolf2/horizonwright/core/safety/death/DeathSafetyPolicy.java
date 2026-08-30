package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Immutable thresholds from the item-preservation plan. */
public final class DeathSafetyPolicy {

    private final double criticalHealthFraction;
    private final double recoveredHealthFraction;
    private final int recoveredHealthStableTicks;
    private final int respawnStableTicks;
    private final int graveStableTicks;
    private final int gravePlacementRadius;

    public DeathSafetyPolicy(double criticalHealthFraction, double recoveredHealthFraction,
        int recoveredHealthStableTicks, int respawnStableTicks, int graveStableTicks, int gravePlacementRadius) {
        if (!Double.isFinite(criticalHealthFraction) || criticalHealthFraction <= 0.0D
            || criticalHealthFraction >= 1.0D) {
            throw new IllegalArgumentException("criticalHealthFraction must be finite and between zero and one");
        }
        if (!Double.isFinite(recoveredHealthFraction) || recoveredHealthFraction <= criticalHealthFraction
            || recoveredHealthFraction > 1.0D) {
            throw new IllegalArgumentException("recoveredHealthFraction must exceed the critical fraction");
        }
        if (recoveredHealthStableTicks <= 0 || respawnStableTicks <= 0 || graveStableTicks <= 0) {
            throw new IllegalArgumentException("stable tick requirements must be positive");
        }
        if (gravePlacementRadius < 0) {
            throw new IllegalArgumentException("gravePlacementRadius must not be negative");
        }
        this.criticalHealthFraction = criticalHealthFraction;
        this.recoveredHealthFraction = recoveredHealthFraction;
        this.recoveredHealthStableTicks = recoveredHealthStableTicks;
        this.respawnStableTicks = respawnStableTicks;
        this.graveStableTicks = graveStableTicks;
        this.gravePlacementRadius = gravePlacementRadius;
    }

    public static DeathSafetyPolicy planDefaults(int gravePlacementRadius) {
        return new DeathSafetyPolicy(0.40D, 0.60D, 20, 20, 40, gravePlacementRadius);
    }

    public double getCriticalHealthFraction() {
        return criticalHealthFraction;
    }

    public double getRecoveredHealthFraction() {
        return recoveredHealthFraction;
    }

    public int getRecoveredHealthStableTicks() {
        return recoveredHealthStableTicks;
    }

    public int getRespawnStableTicks() {
        return respawnStableTicks;
    }

    public int getGraveStableTicks() {
        return graveStableTicks;
    }

    public int getGravePlacementRadius() {
        return gravePlacementRadius;
    }
}
