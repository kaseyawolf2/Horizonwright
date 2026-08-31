package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairPolicy;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationServiceRequirements;

/** Pure priority rule for pre-dig unload and repair suspension. */
final class ExcavationServiceTriggerEvaluator {

    static final int MINIMUM_EMPTY_SLOTS_BEFORE_DIG = 2;

    private final RepairPolicy repairPolicy;

    ExcavationServiceTriggerEvaluator(RepairPolicy repairPolicy) {
        if (repairPolicy == null) throw new IllegalArgumentException("repairPolicy must not be null");
        this.repairPolicy = repairPolicy;
    }

    ExcavationSuspensionReason evaluate(ExcavationBlockClassification classification,
        ExcavationServiceRequirements requirements, int emptyMainInventorySlots, RepairToolSnapshot tool) {
        if (classification == null || requirements == null
            || emptyMainInventorySlots < 0
            || emptyMainInventorySlots > 36) {
            throw new IllegalArgumentException("classification, requirements, and bounded capacity are required");
        }
        if (classification != ExcavationBlockClassification.BREAKABLE) return ExcavationSuspensionReason.NONE;
        if (requirements.isUnloadConfigured() && emptyMainInventorySlots < MINIMUM_EMPTY_SLOTS_BEFORE_DIG) {
            return ExcavationSuspensionReason.UNLOADING_REQUIRED;
        }
        if (requirements.isRepairConfigured()) {
            if (tool == null) throw new IllegalArgumentException("configured repair service requires tool evidence");
            if (tool.getReservedInventorySlot() != requirements.getReservedToolSlot()) {
                throw new IllegalArgumentException("tool evidence belongs to another reserved inventory slot");
            }
            if (repairPolicy.assess(tool, requirements.getPredictedWorkDamage())
                .isRepairRequired()) {
                return ExcavationSuspensionReason.REPAIR_REQUIRED;
            }
        }
        return ExcavationSuspensionReason.NONE;
    }
}
