package io.github.kaseyawolf2.horizonwright.core.combat;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import org.junit.Test;

public class CombatTargetPolicyTest {

    private final CombatTargetPolicy policy = new CombatTargetPolicy(12);

    @Test
    public void everyProtectionOverridesHostility() {
        for (CombatTarget.Protection protection : CombatTarget.Protection.values()) {
            CombatTarget target = new CombatTarget(
                "mob",
                CombatTarget.Kind.HOSTILE,
                EnumSet.of(protection),
                true,
                true,
                1);
            assertEquals(protection.name(), CombatTargetPolicy.Verdict.PROTECTED, policy.evaluate(target));
        }
    }

    @Test
    public void excludesPlayersNeutralPassiveAndUnknownEntities() {
        for (CombatTarget.Kind kind : CombatTarget.Kind.values()) {
            if (kind == CombatTarget.Kind.HOSTILE) continue;
            assertNotEquals(
                CombatTargetPolicy.Verdict.ELIGIBLE,
                policy.evaluate(new CombatTarget("mob", kind, Collections.emptySet(), true, true, 1)));
        }
    }

    @Test
    public void requiresAliveVisibleAndWithinInclusiveObservationRange() {
        assertEquals(CombatTargetPolicy.Verdict.DEAD, policy.evaluate(target("dead", false, true, 1)));
        assertEquals(CombatTargetPolicy.Verdict.NOT_VISIBLE, policy.evaluate(target("hidden", true, false, 1)));
        assertEquals(CombatTargetPolicy.Verdict.OUT_OF_RANGE, policy.evaluate(target("far", true, true, 144.01)));
        assertEquals(CombatTargetPolicy.Verdict.ELIGIBLE, policy.evaluate(target("edge", true, true, 144)));
    }

    @Test
    public void selectionIsStableAndRetainsOnlyStillEligibleTargets() {
        CombatTarget a = target("a", true, true, 4);
        CombatTarget b = target("b", true, true, 4);
        assertSame(
            a,
            policy.select(Arrays.asList(b, a), null)
                .get());
        assertSame(
            b,
            policy.select(Arrays.asList(a, b), "b")
                .get());
        assertSame(
            a,
            policy.select(Arrays.asList(a, target("b", true, false, 1)), "b")
                .get());
        assertFalse(
            policy.select(Collections.emptyList(), "b")
                .isPresent());
    }

    @Test
    public void missingRetainedTargetFallsBackToNearest() {
        CombatTarget near = target("z", true, true, 1);
        assertSame(
            near,
            policy.select(Arrays.asList(target("a", true, true, 4), near), "gone")
                .get());
    }

    @Test
    public void protectionEvidenceIsCopiedAndImmutable() {
        EnumSet<CombatTarget.Protection> flags = EnumSet.of(CombatTarget.Protection.NAMED);
        CombatTarget target = new CombatTarget("mob", CombatTarget.Kind.HOSTILE, flags, true, true, 1);
        flags.clear();
        assertEquals(CombatTargetPolicy.Verdict.PROTECTED, policy.evaluate(target));
        assertThrows(
            UnsupportedOperationException.class,
            () -> target.getProtections()
                .clear());
    }

    @Test
    public void malformedDistanceAndUnboundedRangeAreRejected() {
        for (double distance : new double[] { Double.NaN, Double.POSITIVE_INFINITY, -1 }) {
            assertThrows(IllegalArgumentException.class, () -> target("mob", true, true, distance));
        }
        for (double range : new double[] { Double.NaN, Double.POSITIVE_INFINITY, 0, -1, 32.01 }) {
            assertThrows(IllegalArgumentException.class, () -> new CombatTargetPolicy(range));
        }
    }

    static CombatTarget target(String id, boolean alive, boolean visible, double distanceSquared) {
        return new CombatTarget(id, CombatTarget.Kind.HOSTILE, Collections.emptySet(), alive, visible, distanceSquared);
    }
}
