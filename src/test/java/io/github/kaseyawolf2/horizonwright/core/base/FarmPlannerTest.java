package io.github.kaseyawolf2.horizonwright.core.base;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class FarmPlannerTest {

    private static final String WHEAT_SEEDS = "minecraft:wheat_seeds";
    private static final String OAK_SAPLING = "minecraft:sapling:0";

    private final FarmPlanner planner = new FarmPlanner();
    private final NamedArea plot = new NamedArea(
        "farm",
        "Farm",
        new BasePosition(0, -4, 60, -4),
        new BasePosition(0, 4, 70, 4));

    @Test
    public void ordinaryMatureCropsRequireMatchingSeedEvidenceAboveReserve() {
        CropObservation mature = crop(CropFamily.VANILLA, "wheat:7", WHEAT_SEEDS, true, true, false);

        assertEquals(FarmActionKind.HOLD_REPLANT_RESERVE, plan(mature, seeds(WHEAT_SEEDS, 4, 4)).getAction());
        assertEquals(FarmActionKind.HOLD_REPLANT_RESERVE, plan(mature, seeds("minecraft:carrot", 10, 1)).getAction());
        FarmDecision harvest = plan(mature, seeds(WHEAT_SEEDS, 5, 4));
        assertEquals(FarmActionKind.BREAK_AND_REPLANT, harvest.getAction());
        assertEquals(WHEAT_SEEDS, harvest.getRequiredSeedFingerprint());
        assertTrue(harvest.requiresPostconditionVerification());
    }

    @Test
    public void genericAndNonDestructiveCropsRetainConservativePolicy() {
        FarmDecision unknown = plan(
            crop(CropFamily.GENERIC_IGROWABLE, "growable:unknown", WHEAT_SEEDS, false, false, false),
            seeds(WHEAT_SEEDS, 10, 1));
        FarmDecision young = plan(
            crop(CropFamily.GENERIC_IGROWABLE, "growable:young", WHEAT_SEEDS, true, false, false),
            seeds(WHEAT_SEEDS, 10, 1));
        assertEquals(FarmActionKind.HOLD_FOR_ADAPTER, unknown.getAction());
        assertEquals(FarmActionKind.WAIT_GROWING, young.getAction());
        assertFalse(unknown.requiresMutation());
        assertFalse(young.requiresMutation());

        for (CropFamily family : new CropFamily[] { CropFamily.PAM_CROP, CropFamily.PAM_HANGING_FRUIT,
            CropFamily.PAM_FRUITING_LOG, CropFamily.CROPS_NH }) {
            FarmDecision decision = plan(
                crop(family, family.name(), "adapter:" + family.name(), true, true, false),
                seeds("adapter:" + family.name(), 0, 0));
            assertEquals(FarmActionKind.RIGHT_CLICK_HARVEST, decision.getAction());
        }
    }

    @Test
    public void finitePassAcceptsExplicitChangedPostconditionAndPersistsFrontier() {
        CropObservation young = crop(CropFamily.VANILLA, "first:young", WHEAT_SEEDS, true, false, false);
        CropObservation mature = cropAt(
            new BasePosition(0, 1, 64, 0),
            CropFamily.VANILLA,
            "second:mature",
            WHEAT_SEEDS,
            true,
            true,
            false);
        FarmPassCheckpoint checkpoint = FarmPassCheckpoint.start(plot, 41L, Arrays.asList(young, mature));
        SeedReserveEvidence reserve = seeds(WHEAT_SEEDS, 5, 1);

        FarmDecision wait = planner.plan(plot, checkpoint, young, reserve);
        checkpoint = checkpoint.advance(wait, young, young, reserve);
        assertEquals(checkpoint, FarmPassCheckpoint.restore(plot, 41L, Arrays.asList(young, mature), 1, 0));

        FarmDecision harvest = planner.plan(plot, checkpoint, mature, reserve);
        CropObservation replanted = cropAt(
            mature.getPosition(),
            CropFamily.VANILLA,
            "second:replanted:age0",
            WHEAT_SEEDS,
            true,
            false,
            false);
        checkpoint = checkpoint.advance(harvest, mature, replanted, reserve);
        assertTrue(checkpoint.isComplete());
        assertEquals(1, checkpoint.getVerifiedMutations());
    }

    @Test
    public void farmTransitionRejectsStaleReserveMaterialReplayAndFalsePostconditions() {
        CropObservation mature = crop(CropFamily.VANILLA, "wheat:7:a", WHEAT_SEEDS, true, true, false);
        FarmPassCheckpoint checkpoint = checkpoint(mature);
        SeedReserveEvidence plannedReserve = seeds(WHEAT_SEEDS, 5, 1);
        FarmDecision decision = planner.plan(plot, checkpoint, mature, plannedReserve);
        CropObservation replanted = crop(CropFamily.VANILLA, "wheat:0:after", WHEAT_SEEDS, true, false, false);

        assertRejected(
            () -> checkpoint.advance(
                decision,
                crop(CropFamily.VANILLA, "changed-before", WHEAT_SEEDS, true, true, false),
                replanted,
                plannedReserve));
        assertRejected(() -> checkpoint.advance(decision, mature, replanted, seeds(WHEAT_SEEDS, 6, 1)));
        assertRejected(
            () -> checkpoint.advance(
                decision,
                mature,
                crop(CropFamily.VANILLA, "after:wrong-material", "minecraft:carrot", true, false, false),
                plannedReserve));
        assertRejected(() -> checkpoint.advance(decision, mature, mature, plannedReserve));

        FarmPassCheckpoint completed = checkpoint.advance(decision, mature, replanted, plannedReserve);
        assertRejected(() -> completed.advance(decision, mature, replanted, plannedReserve));
    }

    @Test
    public void finitePassSafelyAdvancesPastAnExternallyReplantedCrop() {
        CropObservation mature = crop(CropFamily.VANILLA, "wheat:7", WHEAT_SEEDS, true, true, false);
        CropObservation replanted = crop(CropFamily.VANILLA, "wheat:0", WHEAT_SEEDS, true, false, false);
        FarmPassCheckpoint checkpoint = checkpoint(mature);

        FarmPassCheckpoint completed = checkpoint.advanceExternallyReplanted(mature, replanted);

        assertTrue(completed.isComplete());
        assertEquals(0, completed.getVerifiedMutations());
        assertRejected(
            () -> checkpoint.advanceExternallyReplanted(
                mature,
                crop(CropFamily.VANILLA, "wheat:changed-mature", WHEAT_SEEDS, true, true, false)));
        assertRejected(
            () -> checkpoint.advanceExternallyReplanted(
                mature,
                crop(CropFamily.VANILLA, "carrot:0", "minecraft:carrot", true, false, false)));
    }

    @Test
    public void treeFellAndReplantAreSeparateRecoverableBoundedTransitions() {
        TreePlanner trees = new TreePlanner();
        TreeObservation standing = standingTree("oak-1", 10L, "oak:standing", OAK_SAPLING, insideTreeBlocks());
        TreeWorkCheckpoint checkpoint = TreeWorkCheckpoint.start(plot, 9L, standing);

        assertEquals(
            TreeActionKind.HOLD_SAPLING_RESERVE,
            trees.plan(plot, checkpoint, standing, saplings(OAK_SAPLING, 2, 2))
                .getAction());
        assertEquals(
            TreeActionKind.HOLD_SAPLING_RESERVE,
            trees.plan(plot, checkpoint, standing, saplings("minecraft:birch_sapling", 5, 2))
                .getAction());

        SaplingReserveEvidence reserve = saplings(OAK_SAPLING, 3, 2);
        TreeDecision fell = trees.plan(plot, checkpoint, standing, reserve);
        assertEquals(TreeActionKind.FELL_CAPTURED_BLOCKS, fell.getAction());
        assertEquals(insideTreeBlocks(), fell.getCapturedBlocks());
        assertTrue(fell.isCurrentFor(checkpoint, standing, reserve));

        TreeObservation clear = treeState("oak-1", 11L, "oak:clear", OAK_SAPLING, TreeObservationState.FELLED_CLEAR);
        checkpoint = checkpoint.advance(fell, standing, clear, reserve);
        assertEquals(TreeWorkStage.READY_TO_REPLANT, checkpoint.getStage());
        assertEquals(
            checkpoint,
            TreeWorkCheckpoint.restore(
                plot,
                9L,
                "oak-1",
                OAK_SAPLING,
                insideTreeBase(),
                insideTreeBlocks(),
                11L,
                "oak:clear",
                TreeWorkStage.READY_TO_REPLANT));

        TreeDecision plant = trees.plan(plot, checkpoint, clear, reserve);
        assertEquals(TreeActionKind.PLANT_SAPLING, plant.getAction());
        TreeObservation sapling = treeState(
            "oak-1",
            12L,
            "oak:sapling",
            OAK_SAPLING,
            TreeObservationState.SAPLING_PLANTED);
        TreeWorkCheckpoint completed = checkpoint.advance(plant, clear, sapling, reserve);
        assertTrue(completed.isComplete());
        assertRejected(() -> completed.advance(plant, clear, sapling, reserve));
        final TreeWorkCheckpoint replantCheckpoint = checkpoint;
        assertRejected(() -> replantCheckpoint.advance(fell, standing, clear, reserve));
    }

    @Test
    public void treePayloadAndFarmContainmentFailClosed() {
        TreePlanner trees = new TreePlanner();
        TreeObservation outside = standingTree(
            "outside",
            1L,
            "outside",
            OAK_SAPLING,
            Arrays.asList(insideTreeBase(), new BasePosition(0, 20, 65, 0)));
        TreeWorkCheckpoint outsideCheckpoint = TreeWorkCheckpoint.start(plot, 1L, outside);
        assertEquals(
            TreeActionKind.SKIP_OUTSIDE_FARM,
            trees.plan(plot, outsideCheckpoint, outside, saplings(OAK_SAPLING, 3, 2))
                .getAction());

        List<BasePosition> oversized = new ArrayList<BasePosition>();
        for (int index = 0; index <= TreeObservation.MAX_CAPTURED_BLOCKS; index++) {
            oversized.add(insideTreeBase());
        }
        assertArgumentRejected(() -> standingTree("huge", 1L, "huge", OAK_SAPLING, oversized));
    }

    private FarmDecision plan(CropObservation observation, SeedReserveEvidence evidence) {
        return planner.plan(plot, checkpoint(observation), observation, evidence);
    }

    private FarmPassCheckpoint checkpoint(CropObservation observation) {
        return FarmPassCheckpoint.start(plot, 7L, Collections.singletonList(observation));
    }

    private static CropObservation crop(CropFamily family, String fingerprint, String seedFingerprint, boolean known,
        boolean mature, boolean protectedBlock) {
        return cropAt(
            new BasePosition(0, 0, 64, 0),
            family,
            fingerprint,
            seedFingerprint,
            known,
            mature,
            protectedBlock);
    }

    private static CropObservation cropAt(BasePosition position, CropFamily family, String fingerprint,
        String seedFingerprint, boolean known, boolean mature, boolean protectedBlock) {
        return new CropObservation(position, family, fingerprint, seedFingerprint, known, mature, protectedBlock);
    }

    private static SeedReserveEvidence seeds(String material, int available, int reserve) {
        return new SeedReserveEvidence(3L, "inventory:3:" + available, material, available, reserve);
    }

    private static SaplingReserveEvidence saplings(String material, int available, int reserve) {
        return new SaplingReserveEvidence(5L, "inventory:5:" + available, material, available, reserve);
    }

    private static BasePosition insideTreeBase() {
        return new BasePosition(0, 0, 64, 0);
    }

    private static List<BasePosition> insideTreeBlocks() {
        return Arrays.asList(insideTreeBase(), new BasePosition(0, 0, 65, 0));
    }

    private static TreeObservation standingTree(String id, long revision, String fingerprint, String material,
        List<BasePosition> blocks) {
        return new TreeObservation(
            id,
            revision,
            fingerprint,
            material,
            blocks,
            insideTreeBase(),
            TreeObservationState.STANDING,
            true,
            false);
    }

    private static TreeObservation treeState(String id, long revision, String fingerprint, String material,
        TreeObservationState state) {
        return new TreeObservation(
            id,
            revision,
            fingerprint,
            material,
            Collections.<BasePosition>emptyList(),
            insideTreeBase(),
            state,
            false,
            false);
    }

    private static void assertRejected(Runnable operation) {
        try {
            operation.run();
            fail("expected stale or unauthorized transition to be rejected");
        } catch (IllegalStateException expected) {
            assertFalse(
                expected.getMessage()
                    .isEmpty());
        }
    }

    private static void assertArgumentRejected(Runnable operation) {
        try {
            operation.run();
            fail("expected invalid bounded payload to be rejected");
        } catch (IllegalArgumentException expected) {
            assertFalse(
                expected.getMessage()
                    .isEmpty());
        }
    }
}
