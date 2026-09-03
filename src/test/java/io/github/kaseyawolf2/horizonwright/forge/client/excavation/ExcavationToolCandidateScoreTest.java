package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExcavationToolCandidateScoreTest {

    @Test
    public void usableCandidateBeatsBrokenCandidate() {
        assertTrue(
            score(1, true, true, 0.05F, true, 0.1D, false).isBetterThan(score(0, false, true, 1.0F, true, 1.0D, true)));
    }

    @Test
    public void harvestEligibilityBeatsDestructiveSpeed() {
        assertTrue(
            score(1, true, true, 0.02F, true, 0.5D, false)
                .isBetterThan(score(0, true, false, 1.0F, false, 1.0D, true)));
    }

    @Test
    public void actualBreakProgressChoosesFastestHarvestingTool() {
        assertTrue(
            score(1, true, true, 0.20F, true, 0.5D, false).isBetterThan(score(0, true, true, 0.05F, true, 1.0D, true)));
    }

    @Test
    public void effectiveToolClassBreaksEqualSpeedTie() {
        assertTrue(
            score(1, true, true, 0.10F, true, 0.5D, false)
                .isBetterThan(score(0, true, true, 0.10F, false, 1.0D, true)));
    }

    @Test
    public void remainingDurabilityBreaksOtherwiseEqualTie() {
        assertTrue(
            score(1, true, true, 0.10F, true, 0.8D, false).isBetterThan(score(0, true, true, 0.10F, true, 0.2D, true)));
    }

    @Test
    public void preferredSlotBreaksCompleteEvidenceTie() {
        assertTrue(
            score(1, true, true, 0.10F, true, 0.8D, true).isBetterThan(score(0, true, true, 0.10F, true, 0.8D, false)));
        assertFalse(
            score(0, true, true, 0.10F, true, 0.8D, false).isBetterThan(score(1, true, true, 0.10F, true, 0.8D, true)));
    }

    private static ExcavationToolCandidateScore score(int slot, boolean usable, boolean canHarvest, float progress,
        boolean effective, double remaining, boolean preferred) {
        return new ExcavationToolCandidateScore(slot, usable, canHarvest, progress, effective, remaining, preferred);
    }
}
