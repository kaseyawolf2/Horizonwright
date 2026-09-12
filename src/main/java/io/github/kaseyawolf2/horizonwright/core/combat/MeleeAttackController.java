package io.github.kaseyawolf2.horizonwright.core.combat;

/**
 * Bounded melee request cadence for one action epoch. REQUEST_ATTACK is an intent, never proof of damage.
 * A live adapter must revalidate the target, weapon, input gate and lease immediately before dispatch.
 */
public final class MeleeAttackController {

    public enum Decision {
        REQUEST_ATTACK,
        WAIT,
        HOLD,
        FINISHED,
        STOP
    }

    private final long epoch;
    private final int intervalTicks;
    private final int maximumAttacks;
    private final CombatTargetPolicy reach;
    private long lastTick = -1;
    private long lastAttackTick = -1;
    private int requests;
    private boolean stopped;

    public MeleeAttackController(long epoch, int intervalTicks, int maximumAttacks, double reachBlocks) {
        if (epoch <= 0 || intervalTicks < 10
            || maximumAttacks < 1
            || maximumAttacks > 1000
            || !Double.isFinite(reachBlocks)
            || reachBlocks <= 0
            || reachBlocks > 3) {
            throw new IllegalArgumentException(
                "positive epoch, interval >= 10, bounded attack budget and reach <= 3 required");
        }
        this.epoch = epoch;
        this.intervalTicks = intervalTicks;
        this.maximumAttacks = maximumAttacks;
        this.reach = new CombatTargetPolicy(reachBlocks);
    }

    public Decision next(long currentEpoch, long tick, long observedTick, boolean authoritative,
        CombatThreatController.Result readiness, CombatTarget target, boolean targetHurt) {
        if (stopped || currentEpoch != epoch || !authoritative || tick < 0 || tick < lastTick) {
            stopped = true;
            return Decision.STOP;
        }
        lastTick = tick;
        if (readiness == null || readiness.getDecision() == CombatThreatController.Decision.STOP) {
            stopped = true;
            return Decision.STOP;
        }
        if (requests >= maximumAttacks) return Decision.FINISHED;
        if (readiness.getDecision() != CombatThreatController.Decision.READY || target == null
            || observedTick < 0
            || observedTick > tick
            || tick - observedTick > 1
            || reach.evaluate(target) != CombatTargetPolicy.Verdict.ELIGIBLE) return Decision.HOLD;
        if (targetHurt || lastAttackTick >= 0 && tick - lastAttackTick < intervalTicks) return Decision.WAIT;
        lastAttackTick = tick;
        requests++;
        return Decision.REQUEST_ATTACK;
    }

    /** Cancellation is terminal; resumption needs a fresh controller and action epoch. */
    public void cancel() {
        stopped = true;
    }

    public int getRequestedAttacks() {
        return requests;
    }
}
