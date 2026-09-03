package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairPolicy;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationServiceRequirements;

public class ExcavationServiceTriggerEvaluatorTest {

    private final ExcavationServiceTriggerEvaluator evaluator = new ExcavationServiceTriggerEvaluator(
        RepairPolicy.planDefaults());

    @Test
    public void lowCapacityPreemptsRepairBeforeAnyBreakLease() {
        ExcavationServiceRequirements requirements = ExcavationServiceRequirements.of(true, true, 4, 10);
        RepairToolSnapshot damaged = new RepairToolSnapshot("tool", 900, 1000, 4);

        assertEquals(
            ExcavationSuspensionReason.UNLOADING_REQUIRED,
            evaluator.evaluate(ExcavationBlockClassification.BREAKABLE, requirements, 1, damaged));
    }

    @Test
    public void thresholdAndNextWorkDamageBothTriggerRepair() {
        ExcavationServiceRequirements requirements = ExcavationServiceRequirements.of(false, true, 4, 10);
        assertEquals(
            ExcavationSuspensionReason.REPAIR_REQUIRED,
            evaluator.evaluate(
                ExcavationBlockClassification.BREAKABLE,
                requirements,
                20,
                new RepairToolSnapshot("tool", 850, 1000, 4)));
        assertEquals(
            ExcavationSuspensionReason.REPAIR_REQUIRED,
            evaluator.evaluate(
                ExcavationBlockClassification.BREAKABLE,
                requirements,
                20,
                new RepairToolSnapshot("tool", 991, 1000, 4)));
    }

    @Test
    public void healthyToolAndSafeCapacityContinueWithoutSuspension() {
        ExcavationServiceRequirements requirements = ExcavationServiceRequirements.of(true, true, 4, 10);
        assertEquals(
            ExcavationSuspensionReason.NONE,
            evaluator.evaluate(
                ExcavationBlockClassification.BREAKABLE,
                requirements,
                2,
                new RepairToolSnapshot("tool", 100, 1000, 4)));
    }

    @Test
    public void nonBreakingTargetsNeverCreateServiceChurn() {
        ExcavationServiceRequirements requirements = ExcavationServiceRequirements.of(true, true, 4, 10);
        assertEquals(
            ExcavationSuspensionReason.NONE,
            evaluator.evaluate(ExcavationBlockClassification.AIR, requirements, 0, null));
        assertEquals(
            ExcavationSuspensionReason.NONE,
            evaluator.evaluate(ExcavationBlockClassification.PROTECTED_GRAVE, requirements, 0, null));
    }

    @Test
    public void mismatchedToolSlotEvidenceIsRejected() {
        ExcavationServiceRequirements requirements = ExcavationServiceRequirements.of(false, true, 4, 10);
        assertThrows(
            IllegalArgumentException.class,
            () -> evaluator.evaluate(
                ExcavationBlockClassification.BREAKABLE,
                requirements,
                20,
                new RepairToolSnapshot("tool", 100, 1000, 5)));
    }

    @Test
    public void selectsMostDamagedRepairableToolAcrossInventory() {
        ExcavationServiceRequirements requirements = ExcavationServiceRequirements.of(false, true, 0, 10);
        RepairToolSnapshot pickaxe = new RepairToolSnapshot("pickaxe", 860, 1000, 0);
        RepairToolSnapshot shovel = new RepairToolSnapshot("shovel", 490, 500, 7);

        assertEquals(
            7,
            evaluator.repairRequiredTool(requirements, Arrays.asList(pickaxe, shovel))
                .get()
                .getReservedInventorySlot());
        assertEquals(
            ExcavationSuspensionReason.REPAIR_REQUIRED,
            evaluator.evaluateAll(
                ExcavationBlockClassification.BREAKABLE,
                requirements,
                20,
                Arrays.asList(pickaxe, shovel)));
    }
}
