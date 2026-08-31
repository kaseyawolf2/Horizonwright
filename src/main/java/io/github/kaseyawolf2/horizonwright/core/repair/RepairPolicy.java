package io.github.kaseyawolf2.horizonwright.core.repair;

/** Repair trigger policy; the plan default is at or below fifteen percent remaining durability. */
public final class RepairPolicy {

    private final double remainingDurabilityThreshold;

    public RepairPolicy(double remainingDurabilityThreshold) {
        if (!Double.isFinite(remainingDurabilityThreshold) || remainingDurabilityThreshold < 0.0D
            || remainingDurabilityThreshold > 1.0D) {
            throw new IllegalArgumentException("remainingDurabilityThreshold must be finite and within [0,1]");
        }
        this.remainingDurabilityThreshold = remainingDurabilityThreshold;
    }

    public static RepairPolicy planDefaults() {
        return new RepairPolicy(0.15D);
    }

    public double getRemainingDurabilityThreshold() {
        return remainingDurabilityThreshold;
    }

    public RepairAssessment assess(RepairToolSnapshot tool, int predictedWorkDamage) {
        if (tool == null || predictedWorkDamage < 0) {
            throw new IllegalArgumentException("tool and non-negative predictedWorkDamage are required");
        }
        RepairTrigger trigger = tool.getRemainingFraction() <= remainingDurabilityThreshold
            ? RepairTrigger.BELOW_DURABILITY_THRESHOLD
            : predictedWorkDamage >= tool.getRemainingDurability() ? RepairTrigger.INSUFFICIENT_FOR_NEXT_WORK_UNIT
                : RepairTrigger.NOT_REQUIRED;
        return new RepairAssessment(trigger, tool.getRemainingDurability(), predictedWorkDamage);
    }
}
