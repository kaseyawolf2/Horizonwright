package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;

public class ExcavationBlockClassifierTest {

    private static final BlockPosition POSITION = new BlockPosition(1, 64, 2);

    @Test
    public void graveAndInfrastructureAlwaysOutrankBreakability() {
        assertClassification(
            ExcavationBlockClassification.PROTECTED_GRAVE,
            evidence(true, false, false, false, true, false, true));
        assertClassification(
            ExcavationBlockClassification.PROTECTED_INFRASTRUCTURE,
            evidence(true, false, false, false, false, true, true));
    }

    @Test
    public void fluidsAreNeverTreatedAsBlindDiggingTargets() {
        assertClassification(
            ExcavationBlockClassification.FLUID_SOURCE_UNREACHABLE,
            evidence(true, false, true, true, false, false, false));
        assertClassification(
            ExcavationBlockClassification.FLUID_FLOWING,
            evidence(true, false, true, false, false, false, false));
    }

    @Test
    public void onlyLoadedOrdinaryBreakableBlocksBecomeDigIntents() {
        assertClassification(
            ExcavationBlockClassification.BREAKABLE,
            evidence(true, false, false, false, false, false, true));
        assertClassification(
            ExcavationBlockClassification.AIR,
            evidence(true, true, false, false, false, false, false));
        assertClassification(
            ExcavationBlockClassification.UNREACHABLE,
            evidence(false, false, false, false, false, false, false));
        assertClassification(
            ExcavationBlockClassification.UNREACHABLE,
            evidence(true, false, false, false, false, false, false));
    }

    @Test
    public void contradictoryEvidenceIsRejectedBeforePlanning() {
        assertThrows(IllegalArgumentException.class, () -> evidence(true, true, false, false, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> evidence(false, false, false, false, false, false, true));
    }

    private static void assertClassification(ExcavationBlockClassification expected, ExcavationBlockEvidence evidence) {
        assertEquals(
            expected,
            ExcavationBlockClassifier.classify(evidence)
                .getClassification());
    }

    private static ExcavationBlockEvidence evidence(boolean loaded, boolean air, boolean fluid, boolean source,
        boolean grave, boolean infrastructure, boolean breakable) {
        return new ExcavationBlockEvidence(
            POSITION,
            loaded ? "minecraft:stone@0" : "unloaded",
            loaded,
            air,
            fluid,
            source,
            grave,
            infrastructure,
            breakable);
    }
}
