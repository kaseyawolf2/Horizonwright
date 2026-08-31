package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Immutable service-trigger inputs carried with each exact excavation observation. */
public final class ExcavationServiceRequirements {

    private static final ExcavationServiceRequirements NONE = new ExcavationServiceRequirements(false, false, 0, 0);

    private final boolean unloadConfigured;
    private final boolean repairConfigured;
    private final int reservedToolSlot;
    private final int predictedWorkDamage;

    private ExcavationServiceRequirements(boolean unloadConfigured, boolean repairConfigured, int reservedToolSlot,
        int predictedWorkDamage) {
        if (reservedToolSlot < 0 || reservedToolSlot > 35 || predictedWorkDamage < 0) {
            throw new IllegalArgumentException("reserved tool slot or predicted work damage is invalid");
        }
        this.unloadConfigured = unloadConfigured;
        this.repairConfigured = repairConfigured;
        this.reservedToolSlot = reservedToolSlot;
        this.predictedWorkDamage = predictedWorkDamage;
    }

    public static ExcavationServiceRequirements none() {
        return NONE;
    }

    public static ExcavationServiceRequirements of(boolean unloadConfigured, boolean repairConfigured,
        int reservedToolSlot, int predictedWorkDamage) {
        if (!unloadConfigured && !repairConfigured) return NONE;
        return new ExcavationServiceRequirements(
            unloadConfigured,
            repairConfigured,
            reservedToolSlot,
            predictedWorkDamage);
    }

    public boolean isUnloadConfigured() {
        return unloadConfigured;
    }

    public boolean isRepairConfigured() {
        return repairConfigured;
    }

    public int getReservedToolSlot() {
        if (!repairConfigured) throw new IllegalStateException("repair service is not configured");
        return reservedToolSlot;
    }

    public int getPredictedWorkDamage() {
        if (!repairConfigured) throw new IllegalStateException("repair service is not configured");
        return predictedWorkDamage;
    }
}
