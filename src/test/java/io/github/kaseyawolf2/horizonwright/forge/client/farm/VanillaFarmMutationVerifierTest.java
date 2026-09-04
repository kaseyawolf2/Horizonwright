package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertThrows;

import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmDecision;
import io.github.kaseyawolf2.horizonwright.core.base.FarmPassCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.base.FarmPlanner;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;

public class VanillaFarmMutationVerifierTest {

    private static final BasePosition POSITION = new BasePosition(0, 2, 64, 4);
    private static final NamedArea PLOT = new NamedArea("field", "Field", POSITION, POSITION);
    private static final CropObservation MATURE = crop(CropFamily.VANILLA, "wheat-7", true, false);
    private static final SeedReserveEvidence RESERVE = new SeedReserveEvidence(
        1L,
        "inventory",
        "minecraft:wheat_seeds|meta=0|nbt=none",
        10,
        2);
    private static final FarmDecision DECISION = new FarmPlanner()
        .plan(PLOT, FarmPassCheckpoint.start(PLOT, 1L, Collections.singletonList(MATURE)), MATURE, RESERVE);
    private final VanillaFarmMutationVerifier verifier = new VanillaFarmMutationVerifier();

    @Test
    public void exactCurrentEvidenceAndImmatureReplacementAreAccepted() {
        verifier.requireCurrent(DECISION, MATURE, RESERVE, null);
        verifier.requireReplacement(DECISION, MATURE, crop(CropFamily.VANILLA, "wheat-0", false, false));
    }

    @Test
    public void rightClickHarvestIgnoresSeedsButRejectsProtectedTarget() {
        SeedReserveEvidence changed = new SeedReserveEvidence(
            1L,
            "changed",
            MATURE.getRequiredSeedFingerprint(),
            10,
            2);
        verifier.requireCurrent(DECISION, MATURE, changed, null);
        verifier.requireCurrent(DECISION, MATURE, RESERVE, "minecraft:carrot|meta=0|nbt=none");
        assertThrows(
            IllegalStateException.class,
            () -> verifier.requireCurrent(DECISION, crop(CropFamily.VANILLA, "wheat-7", true, true), RESERVE, null));
    }

    @Test
    public void matureWrongFamilyWrongSeedOrUnchangedReplacementIsRejected() {
        assertThrows(
            IllegalStateException.class,
            () -> verifier.requireReplacement(DECISION, MATURE, crop(CropFamily.VANILLA, "wheat-7", true, false)));
        assertThrows(
            IllegalStateException.class,
            () -> verifier.requireReplacement(DECISION, MATURE, crop(CropFamily.PAM_CROP, "pam-0", false, false)));
        CropObservation wrongSeed = new CropObservation(
            POSITION,
            CropFamily.VANILLA,
            "carrot-0",
            "minecraft:carrot|meta=0|nbt=none",
            true,
            false,
            false);
        assertThrows(IllegalStateException.class, () -> verifier.requireReplacement(DECISION, MATURE, wrongSeed));
    }

    private static CropObservation crop(CropFamily family, String fingerprint, boolean mature, boolean protectedCrop) {
        return new CropObservation(
            POSITION,
            family,
            fingerprint,
            "minecraft:wheat_seeds|meta=0|nbt=none",
            true,
            mature,
            protectedCrop);
    }
}
