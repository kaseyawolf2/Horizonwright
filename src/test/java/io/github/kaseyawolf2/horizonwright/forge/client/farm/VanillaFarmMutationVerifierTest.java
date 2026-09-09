package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmDecision;
import io.github.kaseyawolf2.horizonwright.core.base.FarmPassCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.base.FarmPlanner;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

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
    public void confirmedSeedSwapPermitsOnlyTheProvenSlotOrderingChange() {
        CropObservation mature = crop(CropFamily.VANILLA_BREAK_REPLANT, "wheat-7", true, false);
        FarmDecision decision = new FarmPlanner()
            .plan(PLOT, FarmPassCheckpoint.start(PLOT, 1L, Collections.singletonList(mature)), mature, RESERVE);
        ContainerTransaction swap = seedSwap();
        VerifiedContainerClick click = swap.nextClick(
            swap.getClicks()
                .get(0)
                .getExpectedBefore(),
            7L)
            .get();
        assertTrue(swap.confirm(click.getClickId(), true, click.getExpectedAfter(), 7L));
        SeedReserveEvidence rearranged = new SeedReserveEvidence(
            1L,
            "verified-different-slot-order",
            RESERVE.getSeedFingerprint(),
            10,
            2);
        verifier.requireCurrentAfterVerifiedSeedSwap(
            decision,
            mature,
            rearranged,
            RESERVE.getSeedFingerprint(),
            swap,
            click.getExpectedAfter());
        assertThrows(
            IllegalStateException.class,
            () -> verifier.requireCurrent(decision, mature, rearranged, RESERVE.getSeedFingerprint()));
    }

    @Test
    public void seedStagingCannotAuthorizeHarvestBeforeConfirmationOrAfterAnotherInventoryChange() {
        CropObservation mature = crop(CropFamily.VANILLA_BREAK_REPLANT, "wheat-7", true, false);
        FarmDecision decision = new FarmPlanner()
            .plan(PLOT, FarmPassCheckpoint.start(PLOT, 1L, Collections.singletonList(mature)), mature, RESERVE);
        ContainerTransaction swap = seedSwap();
        VerifiedContainerClick click = swap.nextClick(
            swap.getClicks()
                .get(0)
                .getExpectedBefore(),
            7L)
            .get();
        assertThrows(
            IllegalStateException.class,
            () -> verifier.requireCurrentAfterVerifiedSeedSwap(
                decision,
                mature,
                RESERVE,
                RESERVE.getSeedFingerprint(),
                swap,
                click.getExpectedAfter()));
        assertTrue(swap.confirm(click.getClickId(), true, click.getExpectedAfter(), 7L));
        assertThrows(
            IllegalStateException.class,
            () -> verifier.requireCurrentAfterVerifiedSeedSwap(
                decision,
                mature,
                RESERVE,
                RESERVE.getSeedFingerprint(),
                swap,
                click.getExpectedBefore()));
        SeedReserveEvidence lostSeeds = new SeedReserveEvidence(1L, "changed", RESERVE.getSeedFingerprint(), 9, 2);
        assertThrows(
            IllegalStateException.class,
            () -> verifier.requireCurrentAfterVerifiedSeedSwap(
                decision,
                mature,
                lostSeeds,
                RESERVE.getSeedFingerprint(),
                swap,
                click.getExpectedAfter()));
    }

    private static ContainerTransaction seedSwap() {
        List<ItemFingerprint> slots = new ArrayList<>(Collections.nCopies(45, null));
        slots.set(22, new ItemFingerprint("minecraft:wheat_seeds", 0, "none", 10));
        ContainerSnapshot before = new ContainerSnapshot(
            0,
            "net.minecraft.inventory.ContainerPlayer",
            "slots",
            0L,
            slots,
            null);
        return PlayerInventoryHotbarSwap.plan("seed", 7L, before, 22, 4);
    }

    @Test
    public void exactCurrentEvidenceAndImmatureReplacementAreAccepted() {
        verifier.requireCurrent(DECISION, MATURE, RESERVE, null);
        CropObservation replacement = crop(CropFamily.VANILLA, "wheat-0", false, false);
        assertTrue(verifier.isUnchanged(MATURE, MATURE));
        assertFalse(verifier.isUnchanged(MATURE, replacement));
        verifier.requireReplacement(DECISION, MATURE, replacement);
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
