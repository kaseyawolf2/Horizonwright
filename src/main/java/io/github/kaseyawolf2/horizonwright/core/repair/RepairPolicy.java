package io.github.kaseyawolf2.horizonwright.core.repair;

/** Repair trigger policy; the Tinkers plan default waits until the tool enters its repairable broken state. */
public final class RepairPolicy {

    private static final int DEFAULT_MAXIMUM_UNREPAIRED_PERCENT = 5;
    private final double remainingDurabilityThreshold;

    public RepairPolicy(double remainingDurabilityThreshold) {
        if (!Double.isFinite(remainingDurabilityThreshold) || remainingDurabilityThreshold < 0.0D
            || remainingDurabilityThreshold > 1.0D) {
            throw new IllegalArgumentException("remainingDurabilityThreshold must be finite and within [0,1]");
        }
        this.remainingDurabilityThreshold = remainingDurabilityThreshold;
    }

    public static RepairPolicy planDefaults() {
        return new RepairPolicy(0.0D);
    }

    public double getRemainingDurabilityThreshold() {
        return remainingDurabilityThreshold;
    }

    /** A repair trip may finish without wasting another material on the final five percent. */
    public boolean isRepairGoalSatisfied(RepairToolSnapshot tool) {
        if (tool == null) throw new IllegalArgumentException("tool is required");
        return (long) tool.getDamage() * 100L <= (long) tool.getMaximumDamage() * DEFAULT_MAXIMUM_UNREPAIRED_PERCENT;
    }

    public RepairAssessment assess(RepairToolSnapshot tool, int predictedWorkDamage) {
        if (tool == null || predictedWorkDamage < 0) {
            throw new IllegalArgumentException("tool and non-negative predictedWorkDamage are required");
        }
        RepairTrigger trigger = tool.getRemainingFraction() <= remainingDurabilityThreshold
            ? RepairTrigger.BELOW_DURABILITY_THRESHOLD
            : RepairTrigger.NOT_REQUIRED;
        return new RepairAssessment(trigger, tool.getRemainingDurability(), predictedWorkDamage);
    }
}
