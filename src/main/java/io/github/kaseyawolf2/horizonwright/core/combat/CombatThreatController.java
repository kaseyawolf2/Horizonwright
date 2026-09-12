package io.github.kaseyawolf2.horizonwright.core.combat;

/** Movement-only retreat decisions with health hysteresis; this class never drives navigation. */
public final class CombatThreatController {

    public enum Decision {
        READY,
        RETREAT,
        HOLD,
        STOP
    }

    public enum Reason {
        READY,
        UNAVAILABLE,
        INPUT_BLOCKED,
        DEAD,
        CRITICAL_HEALTH,
        LOW_HEALTH,
        RECOVERING,
        LOW_ARMOR,
        LOW_FOOD,
        WEAPON_DEPLETED,
        LOW_AMMUNITION,
        EXPLOSION
    }

    public static final class Result {

        private final Decision decision;
        private final Reason reason;

        private Result(Decision decision, Reason reason) {
            this.decision = decision;
            this.reason = reason;
        }

        public Decision getDecision() {
            return decision;
        }

        public Reason getReason() {
            return reason;
        }
    }

    private final double retreatHealth;
    private final double resumeHealth;
    private final int minimumArmor;
    private final int minimumFood;
    private final double minimumWeaponRemaining;
    private final int minimumAmmunition;
    private boolean recovering;

    public CombatThreatController(double retreatHealth, double resumeHealth, int minimumArmor, int minimumFood,
        double minimumWeaponRemaining, int minimumAmmunition) {
        if (!Double.isFinite(retreatHealth) || !Double.isFinite(resumeHealth)
            || retreatHealth < 0.4
            || resumeHealth <= retreatHealth
            || resumeHealth > 1
            || minimumArmor < 0
            || minimumFood < 0
            || minimumFood > 20
            || !Double.isFinite(minimumWeaponRemaining)
            || minimumWeaponRemaining < 0
            || minimumWeaponRemaining >= 1
            || minimumAmmunition < 1) {
            throw new IllegalArgumentException(
                "invalid combat thresholds; critical health cannot be relaxed below 40%");
        }
        this.retreatHealth = retreatHealth;
        this.resumeHealth = resumeHealth;
        this.minimumArmor = minimumArmor;
        this.minimumFood = minimumFood;
        this.minimumWeaponRemaining = minimumWeaponRemaining;
        this.minimumAmmunition = minimumAmmunition;
    }

    public static CombatThreatController defaults() {
        return new CombatThreatController(0.5, 0.7, 4, 6, 0.1, 1);
    }

    public Result evaluate(CombatReadiness resources, boolean threatPresent) {
        if (resources == null || !resources.available) return result(Decision.STOP, Reason.UNAVAILABLE);
        if (!resources.movementAllowed) return result(Decision.STOP, Reason.INPUT_BLOCKED);
        if (resources.healthFraction <= 0) return result(Decision.STOP, Reason.DEAD);
        if (resources.healthFraction <= retreatHealth) recovering = true;
        if (resources.healthFraction <= 0.4) return withdraw(threatPresent, Reason.CRITICAL_HEALTH);
        if (resources.healthFraction <= retreatHealth) return withdraw(threatPresent, Reason.LOW_HEALTH);
        if (!resources.attacksAllowed) return withdraw(threatPresent, Reason.INPUT_BLOCKED);
        if (resources.imminentExplosion) return withdraw(threatPresent, Reason.EXPLOSION);
        if (resources.armorPoints < minimumArmor) return withdraw(threatPresent, Reason.LOW_ARMOR);
        if (resources.food < minimumFood) return withdraw(threatPresent, Reason.LOW_FOOD);
        if (resources.weaponRemaining <= minimumWeaponRemaining) return withdraw(threatPresent, Reason.WEAPON_DEPLETED);
        if (resources.ranged && resources.ammunition < minimumAmmunition)
            return withdraw(threatPresent, Reason.LOW_AMMUNITION);
        if (recovering && resources.healthFraction < resumeHealth) return withdraw(threatPresent, Reason.RECOVERING);
        recovering = false;
        return result(Decision.READY, Reason.READY);
    }

    private static Result withdraw(boolean threatPresent, Reason reason) {
        return result(threatPresent ? Decision.RETREAT : Decision.HOLD, reason);
    }

    private static Result result(Decision decision, Reason reason) {
        return new Result(decision, reason);
    }
}
