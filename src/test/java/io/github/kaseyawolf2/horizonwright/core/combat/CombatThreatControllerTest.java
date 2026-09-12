package io.github.kaseyawolf2.horizonwright.core.combat;

import static org.junit.Assert.*;

import org.junit.Test;

public class CombatThreatControllerTest {

    @Test
    public void criticalHealthAndDeathOverrideOtherwiseReadyEquipment() {
        CombatThreatController controller = CombatThreatController.defaults();
        assertResult(
            controller.evaluate(resources(0.4, 20, 20, 1, 10, false), true),
            CombatThreatController.Decision.RETREAT,
            CombatThreatController.Reason.CRITICAL_HEALTH);
        assertResult(
            controller.evaluate(resources(0, 20, 20, 1, 10, false), true),
            CombatThreatController.Decision.STOP,
            CombatThreatController.Reason.DEAD);
    }

    @Test
    public void recoveryHysteresisPreventsHealthBoundaryOscillation() {
        CombatThreatController controller = CombatThreatController.defaults();
        assertEquals(
            CombatThreatController.Reason.LOW_HEALTH,
            controller.evaluate(resources(0.5, 4, 6, 1, 0, false), true)
                .getReason());
        assertResult(
            controller.evaluate(resources(0.69, 4, 6, 1, 0, false), true),
            CombatThreatController.Decision.RETREAT,
            CombatThreatController.Reason.RECOVERING);
        assertEquals(
            CombatThreatController.Decision.READY,
            controller.evaluate(resources(0.7, 4, 6, 1, 0, false), true)
                .getDecision());
        assertEquals(
            CombatThreatController.Decision.READY,
            controller.evaluate(resources(0.6, 4, 6, 1, 0, false), true)
                .getDecision());
    }

    @Test
    public void resourceThresholdsAreIndependentAndAmmoOnlyAppliesToRangedWeapons() {
        assertReason(resources(1, 3, 20, 1, 10, false), CombatThreatController.Reason.LOW_ARMOR);
        assertReason(resources(1, 20, 5, 1, 10, false), CombatThreatController.Reason.LOW_FOOD);
        assertReason(resources(1, 20, 20, 0.1, 10, false), CombatThreatController.Reason.WEAPON_DEPLETED);
        assertReason(resources(1, 20, 20, 1, 0, true), CombatThreatController.Reason.LOW_AMMUNITION);
        assertReason(resources(1, 4, 6, 0.11, 0, false), CombatThreatController.Reason.READY);
        assertReason(resources(1, 4, 6, 0.11, 1, true), CombatThreatController.Reason.READY);
    }

    @Test
    public void lowResourcesWithoutThreatsHoldInsteadOfInventingARetreatRoute() {
        assertResult(
            CombatThreatController.defaults()
                .evaluate(resources(0.3, 4, 6, 1, 0, false), false),
            CombatThreatController.Decision.HOLD,
            CombatThreatController.Reason.CRITICAL_HEALTH);
    }

    @Test
    public void absentEvidenceAndLockdownStopAllCombat() {
        CombatThreatController controller = CombatThreatController.defaults();
        assertResult(
            controller.evaluate(null, true),
            CombatThreatController.Decision.STOP,
            CombatThreatController.Reason.UNAVAILABLE);
        assertResult(
            controller.evaluate(new CombatReadiness(false, true, true, 1, 20, 20, 1, 0, false, false), true),
            CombatThreatController.Decision.STOP,
            CombatThreatController.Reason.UNAVAILABLE);
        assertResult(
            controller.evaluate(new CombatReadiness(true, false, true, 1, 20, 20, 1, 0, false, false), true),
            CombatThreatController.Decision.STOP,
            CombatThreatController.Reason.INPUT_BLOCKED);
    }

    @Test
    public void criticalInputGateAndExplosionAllowOnlyWithdrawal() {
        assertResult(
            CombatThreatController.defaults()
                .evaluate(new CombatReadiness(true, true, false, 1, 20, 20, 1, 0, false, false), true),
            CombatThreatController.Decision.RETREAT,
            CombatThreatController.Reason.INPUT_BLOCKED);
        assertResult(
            CombatThreatController.defaults()
                .evaluate(new CombatReadiness(true, true, true, 1, 20, 20, 1, 0, false, true), true),
            CombatThreatController.Decision.RETREAT,
            CombatThreatController.Reason.EXPLOSION);
    }

    @Test
    public void customThresholdsWorkWithoutRelaxingCriticalFloor() {
        CombatThreatController cautious = new CombatThreatController(0.6, 0.8, 8, 10, 0.25, 8);
        assertEquals(
            CombatThreatController.Reason.LOW_HEALTH,
            cautious.evaluate(resources(0.6, 20, 20, 1, 10, true), true)
                .getReason());
        assertThrows(IllegalArgumentException.class, () -> new CombatThreatController(0.39, 0.7, 4, 6, 0.1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CombatThreatController(0.5, 0.5, 4, 6, 0.1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CombatThreatController(Double.NaN, 0.7, 4, 6, 0.1, 1));
        assertThrows(IllegalArgumentException.class, () -> resources(Double.NaN, 20, 20, 1, 1, false));
    }

    static CombatReadiness resources(double health, int armor, int food, double durability, int ammo, boolean ranged) {
        return new CombatReadiness(true, true, true, health, armor, food, durability, ammo, ranged, false);
    }

    private static void assertReason(CombatReadiness resources, CombatThreatController.Reason reason) {
        assertEquals(
            reason,
            CombatThreatController.defaults()
                .evaluate(resources, true)
                .getReason());
    }

    private static void assertResult(CombatThreatController.Result result, CombatThreatController.Decision decision,
        CombatThreatController.Reason reason) {
        assertEquals(decision, result.getDecision());
        assertEquals(reason, result.getReason());
    }
}
