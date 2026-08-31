package io.github.kaseyawolf2.horizonwright.core.repair;

/** Exact acceptance gate for a normal-container TConstruct/TGregworks repair result. */
public final class TinkersRepairVerifier {

    private TinkersRepairVerifier() {}

    public static RepairVerification verify(RepairToolSnapshot input, RepairToolSnapshot output,
        int repairMaterialConsumed, boolean recognizedStationLayout) {
        if (input == null || output == null || repairMaterialConsumed < 0) {
            throw new IllegalArgumentException("input, output, and non-negative material consumption are required");
        }
        if (!recognizedStationLayout) {
            return RepairVerification.rejected("container is not a recognized Tool Station or Tool Forge layout");
        }
        if (repairMaterialConsumed == 0) {
            return RepairVerification.rejected("no eligible repair material was consumed");
        }
        if (!input.getStableToolIdentity()
            .equals(output.getStableToolIdentity())) {
            return RepairVerification.rejected("repair output changed the stable tool identity");
        }
        if (input.getMaximumDamage() != output.getMaximumDamage()) {
            return RepairVerification.rejected("repair output changed the tool durability model");
        }
        if (input.getReservedInventorySlot() != output.getReservedInventorySlot()) {
            return RepairVerification.rejected("repair output is not bound to the reserved return slot");
        }
        if (output.getDamage() >= input.getDamage()) {
            return RepairVerification.rejected("InfiTool.Damage did not decrease");
        }
        return RepairVerification.accepted(input.getDamage() - output.getDamage());
    }
}
