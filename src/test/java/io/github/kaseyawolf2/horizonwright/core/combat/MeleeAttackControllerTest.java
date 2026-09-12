package io.github.kaseyawolf2.horizonwright.core.combat;

import static org.junit.Assert.*;

import java.util.EnumSet;

import org.junit.Test;

public class MeleeAttackControllerTest {

    private final CombatThreatController.Result ready = CombatThreatController.defaults()
        .evaluate(CombatThreatControllerTest.resources(1, 20, 20, 1, 0, false), true);
    private final CombatTarget target = CombatTargetPolicyTest.target("zombie", true, true, 4);

    @Test
    public void duplicateTicksTargetSwitchesAndHurtFramesCannotBypassCadence() {
        MeleeAttackController controller = controller();
        assertEquals(MeleeAttackController.Decision.REQUEST_ATTACK, next(controller, 0, target));
        assertEquals(MeleeAttackController.Decision.WAIT, next(controller, 0, target));
        assertEquals(
            MeleeAttackController.Decision.WAIT,
            next(controller, 9, CombatTargetPolicyTest.target("other", true, true, 1)));
        assertEquals(MeleeAttackController.Decision.WAIT, controller.next(1, 10, 10, true, ready, target, true));
        assertEquals(MeleeAttackController.Decision.REQUEST_ATTACK, next(controller, 11, target));
        assertEquals(2, controller.getRequestedAttacks());
    }

    @Test
    public void staleFutureMissingProtectedOccludedAndOutOfReachTargetsHold() {
        MeleeAttackController controller = controller();
        assertEquals(MeleeAttackController.Decision.HOLD, controller.next(1, 10, 8, true, ready, target, false));
        assertEquals(MeleeAttackController.Decision.HOLD, controller.next(1, 10, 11, true, ready, target, false));
        assertEquals(MeleeAttackController.Decision.HOLD, next(controller, 10, null));
        assertEquals(
            MeleeAttackController.Decision.HOLD,
            next(controller, 10, CombatTargetPolicyTest.target("zombie", true, false, 1)));
        assertEquals(
            MeleeAttackController.Decision.HOLD,
            next(controller, 10, CombatTargetPolicyTest.target("zombie", true, true, 9.01)));
        assertEquals(
            MeleeAttackController.Decision.HOLD,
            next(
                controller,
                10,
                new CombatTarget(
                    "zombie",
                    CombatTarget.Kind.HOSTILE,
                    EnumSet.of(CombatTarget.Protection.NAMED),
                    true,
                    true,
                    1)));
        assertEquals(0, controller.getRequestedAttacks());
        assertEquals(
            MeleeAttackController.Decision.REQUEST_ATTACK,
            controller.next(1, 10, 9, true, ready, target, false));
    }

    @Test
    public void epochChangeAuthorityLossCancellationAndClockRewindAreTerminal() {
        MeleeAttackController epoch = controller();
        assertEquals(MeleeAttackController.Decision.STOP, epoch.next(2, 0, 0, true, ready, target, false));
        assertEquals(MeleeAttackController.Decision.STOP, next(epoch, 1, target));
        MeleeAttackController revoked = controller();
        assertEquals(MeleeAttackController.Decision.STOP, revoked.next(1, 0, 0, false, ready, target, false));
        assertEquals(MeleeAttackController.Decision.STOP, next(revoked, 1, target));
        MeleeAttackController cancelled = controller();
        cancelled.cancel();
        assertEquals(MeleeAttackController.Decision.STOP, next(cancelled, 0, target));
        MeleeAttackController rewind = controller();
        next(rewind, 20, target);
        assertEquals(MeleeAttackController.Decision.STOP, next(rewind, 19, target));
        assertEquals(MeleeAttackController.Decision.STOP, next(rewind, 30, target));
    }

    @Test
    public void retreatSuppressesAttacksAndLockdownCannotBeResumedInSameController() {
        MeleeAttackController controller = controller();
        CombatThreatController.Result retreat = CombatThreatController.defaults()
            .evaluate(CombatThreatControllerTest.resources(0.4, 20, 20, 1, 0, false), true);
        assertEquals(MeleeAttackController.Decision.HOLD, controller.next(1, 0, 0, true, retreat, target, false));
        assertEquals(0, controller.getRequestedAttacks());
        assertEquals(MeleeAttackController.Decision.STOP, controller.next(1, 1, 1, true, null, target, false));
        assertEquals(MeleeAttackController.Decision.STOP, next(controller, 2, target));
    }

    @Test
    public void exhaustsBudgetWithoutClaimingConfirmedHits() {
        MeleeAttackController controller = new MeleeAttackController(1, 10, 1, 3);
        assertEquals(MeleeAttackController.Decision.REQUEST_ATTACK, next(controller, 0, target));
        assertEquals(MeleeAttackController.Decision.FINISHED, next(controller, 10, target));
        assertEquals(1, controller.getRequestedAttacks());
    }

    @Test
    public void disallowsUnboundedFastOrExtendedReachControllers() {
        assertThrows(IllegalArgumentException.class, () -> new MeleeAttackController(0, 10, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new MeleeAttackController(1, 9, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new MeleeAttackController(1, 10, 1001, 3));
        assertThrows(IllegalArgumentException.class, () -> new MeleeAttackController(1, 10, 1, 3.01));
    }

    private MeleeAttackController.Decision next(MeleeAttackController controller, long tick, CombatTarget candidate) {
        return controller.next(1, tick, tick, true, ready, candidate, false);
    }

    private static MeleeAttackController controller() {
        return new MeleeAttackController(1, 10, 10, 3);
    }
}
