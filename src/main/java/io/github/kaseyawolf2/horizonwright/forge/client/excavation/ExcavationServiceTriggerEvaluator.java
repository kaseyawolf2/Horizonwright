package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
        if (tool != null && requirements.isRepairConfigured()
            && tool.getReservedInventorySlot() != requirements.getReservedToolSlot()) {
            throw new IllegalArgumentException("tool evidence belongs to another reserved inventory slot");
        }
        return evaluateAll(
            classification,
            requirements,
            emptyMainInventorySlots,
            tool == null ? Collections.<RepairToolSnapshot>emptyList() : Collections.singletonList(tool));
    }

    ExcavationSuspensionReason evaluateAll(ExcavationBlockClassification classification,
        ExcavationServiceRequirements requirements, int emptyMainInventorySlots, List<RepairToolSnapshot> tools) {
        if (classification == null || requirements == null
            || tools == null
            || tools.contains(null)
            || emptyMainInventorySlots < 0
            || emptyMainInventorySlots > 36) {
            throw new IllegalArgumentException("classification, requirements, and bounded capacity are required");
        }
        if (classification != ExcavationBlockClassification.BREAKABLE) return ExcavationSuspensionReason.NONE;
        if (requirements.isUnloadConfigured() && emptyMainInventorySlots < MINIMUM_EMPTY_SLOTS_BEFORE_DIG) {
            return ExcavationSuspensionReason.UNLOADING_REQUIRED;
        }
        if (requirements.isRepairConfigured()) {
            if (repairRequiredTool(requirements, tools).isPresent()) return ExcavationSuspensionReason.REPAIR_REQUIRED;
        }
        return ExcavationSuspensionReason.NONE;
    }

    Optional<RepairToolSnapshot> repairRequiredTool(ExcavationServiceRequirements requirements,
        List<RepairToolSnapshot> tools) {
        if (requirements == null || tools == null || tools.contains(null)) {
            throw new IllegalArgumentException("requirements and tool evidence are required");
        }
        if (!requirements.isRepairConfigured()) return Optional.empty();
        RepairToolSnapshot mostDamaged = null;
        for (RepairToolSnapshot tool : tools) {
            int slot = tool.getReservedInventorySlot();
            if (slot < 0 || slot > 35) throw new IllegalArgumentException("tool evidence has an invalid slot");
            if (!repairPolicy.assess(tool, requirements.getPredictedWorkDamage())
                .isRepairRequired()) continue;
            if (mostDamaged == null || remaining(tool) < remaining(mostDamaged)
                || (remaining(tool) == remaining(mostDamaged) && slot < mostDamaged.getReservedInventorySlot())) {
                mostDamaged = tool;
            }
        }
        return Optional.ofNullable(mostDamaged);
    }

    private static long remaining(RepairToolSnapshot tool) {
        return (long) tool.getMaximumDamage() - tool.getDamage();
    }
}
