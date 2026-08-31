package io.github.kaseyawolf2.horizonwright.core.repair;

/** Exact reason and durability evidence for a repair decision. */
public final class RepairAssessment {

    private final RepairTrigger trigger;
    private final int remainingDurability;
    private final int predictedWorkDamage;

    RepairAssessment(RepairTrigger trigger, int remainingDurability, int predictedWorkDamage) {
        this.trigger = trigger;
        this.remainingDurability = remainingDurability;
        this.predictedWorkDamage = predictedWorkDamage;
    }

    public RepairTrigger getTrigger() {
        return trigger;
    }

    public int getRemainingDurability() {
        return remainingDurability;
    }

    public int getPredictedWorkDamage() {
        return predictedWorkDamage;
    }

    public boolean isRepairRequired() {
        return trigger != RepairTrigger.NOT_REQUIRED;
    }
}
