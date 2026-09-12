package io.github.kaseyawolf2.horizonwright.core.combat;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Hostile-only foundation. Neutral aggression and hunting require separate, explicit adapters. */
public final class CombatTargetPolicy {

    public enum Verdict {
        ELIGIBLE,
        DEAD,
        PROTECTED,
        PLAYER,
        NOT_HOSTILE,
        OUT_OF_RANGE,
        NOT_VISIBLE
    }

    private final double rangeSquared;

    public CombatTargetPolicy(double range) {
        if (!Double.isFinite(range) || range <= 0 || range > 32) {
            throw new IllegalArgumentException("combat observation range must be in (0, 32]");
        }
        rangeSquared = range * range;
    }

    public Verdict evaluate(CombatTarget target) {
        if (target == null) throw new IllegalArgumentException("target is required");
        if (!target.isAlive()) return Verdict.DEAD;
        if (target.getKind() == CombatTarget.Kind.PLAYER) return Verdict.PLAYER;
        if (!target.getProtections()
            .isEmpty()) return Verdict.PROTECTED;
        if (target.getKind() != CombatTarget.Kind.HOSTILE) return Verdict.NOT_HOSTILE;
        if (target.getDistanceSquared() > rangeSquared) return Verdict.OUT_OF_RANGE;
        if (!target.isVisible()) return Verdict.NOT_VISIBLE;
        return Verdict.ELIGIBLE;
    }

    /** Retains a still-eligible target; otherwise uses nearest distance then stable identity. */
    public Optional<CombatTarget> select(Collection<CombatTarget> targets, String previousIdentity) {
        if (targets == null) throw new IllegalArgumentException("targets are required");
        return targets.stream()
            .filter(target -> evaluate(target) == Verdict.ELIGIBLE)
            .min(
                Comparator.comparingInt(
                    (CombatTarget target) -> target.getIdentity()
                        .equals(previousIdentity) ? 0 : 1)
                    .thenComparingDouble(CombatTarget::getDistanceSquared)
                    .thenComparing(CombatTarget::getIdentity));
    }
}
