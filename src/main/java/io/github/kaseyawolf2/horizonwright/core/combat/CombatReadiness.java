package io.github.kaseyawolf2.horizonwright.core.combat;

/** Resource evidence for the selected weapon adapter. Unknown evidence must be marked unavailable. */
public final class CombatReadiness {

    final boolean available;
    final boolean movementAllowed;
    final boolean attacksAllowed;
    final double healthFraction;
    final int armorPoints;
    final int food;
    final double weaponRemaining;
    final int ammunition;
    final boolean ranged;
    final boolean imminentExplosion;

    public CombatReadiness(boolean available, boolean movementAllowed, boolean attacksAllowed, double healthFraction,
        int armorPoints, int food, double weaponRemaining, int ammunition, boolean ranged, boolean imminentExplosion) {
        if (!Double.isFinite(healthFraction) || healthFraction < 0
            || healthFraction > 1
            || armorPoints < 0
            || food < 0
            || food > 20
            || !Double.isFinite(weaponRemaining)
            || weaponRemaining < 0
            || weaponRemaining > 1
            || ammunition < 0) {
            throw new IllegalArgumentException("combat resources must be finite and within their valid bounds");
        }
        this.available = available;
        this.movementAllowed = movementAllowed;
        this.attacksAllowed = attacksAllowed;
        this.healthFraction = healthFraction;
        this.armorPoints = armorPoints;
        this.food = food;
        this.weaponRemaining = weaponRemaining;
        this.ammunition = ammunition;
        this.ranged = ranged;
        this.imminentExplosion = imminentExplosion;
    }
}
