package io.github.kaseyawolf2.horizonwright.core.repair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TinkersRepairPolicyAndVerifierTest {

    @Test
    public void defaultTinkersPolicyWaitsForRepairableBrokenState() {
        RepairPolicy policy = RepairPolicy.planDefaults();

        assertEquals(
            RepairTrigger.NOT_REQUIRED,
            policy.assess(tool("pick-a", 850, 1000, 2), 1)
                .getTrigger());
        assertEquals(
            RepairTrigger.NOT_REQUIRED,
            policy.assess(tool("pick-a", 999, 1000, 2), 300)
                .getTrigger());
        assertEquals(
            RepairTrigger.BELOW_DURABILITY_THRESHOLD,
            policy.assess(tool("pick-a", 1000, 1000, 2), 1)
                .getTrigger());
    }

    @Test
    public void acceptsOnlyRecognizedMaterialBackedDamageReductionOfSameReservedTool() {
        RepairToolSnapshot input = tool("pick-a", 700, 1000, 2);
        RepairToolSnapshot output = tool("pick-a", 500, 1000, 2);

        RepairVerification accepted = TinkersRepairVerifier.verify(input, output, 1, true);

        assertTrue(accepted.isAccepted());
        assertEquals(200, accepted.getRepairedDamage());
        assertFalse(
            TinkersRepairVerifier.verify(input, output, 0, true)
                .isAccepted());
        assertFalse(
            TinkersRepairVerifier.verify(input, output, 1, false)
                .isAccepted());
    }

    @Test
    public void identityDurabilitySlotAndDamageRegressionsAreRejectedIndependently() {
        RepairToolSnapshot input = tool("pick-a", 700, 1000, 2);

        assertFalse(
            TinkersRepairVerifier.verify(input, tool("pick-b", 500, 1000, 2), 1, true)
                .isAccepted());
        assertFalse(
            TinkersRepairVerifier.verify(input, tool("pick-a", 500, 900, 2), 1, true)
                .isAccepted());
        assertFalse(
            TinkersRepairVerifier.verify(input, tool("pick-a", 500, 1000, 3), 1, true)
                .isAccepted());
        assertFalse(
            TinkersRepairVerifier.verify(input, tool("pick-a", 700, 1000, 2), 1, true)
                .isAccepted());
        assertFalse(
            TinkersRepairVerifier.verify(input, tool("pick-a", 800, 1000, 2), 1, true)
                .isAccepted());
    }

    private static RepairToolSnapshot tool(String identity, int damage, int maximumDamage, int reservedSlot) {
        return new RepairToolSnapshot(identity, damage, maximumDamage, reservedSlot);
    }
}
