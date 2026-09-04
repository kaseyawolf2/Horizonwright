package io.github.kaseyawolf2.horizonwright.core.base;

/** One deterministic farm decision; mutations require a fresh post-action observation. */
public final class FarmDecision {

    private final NamedArea plot;
    private final long passRevision;
    private final int observationIndex;
    private final String observationFingerprint;
    private final String requiredSeedFingerprint;
    private final BasePosition target;
    private final FarmActionKind action;
    private final String detail;
    private final SeedReserveEvidence reserveEvidence;

    FarmDecision(NamedArea plot, long passRevision, int observationIndex, String observationFingerprint,
        String requiredSeedFingerprint, BasePosition target, FarmActionKind action, String detail,
        SeedReserveEvidence reserveEvidence) {
        if (plot == null || passRevision < 0L
            || observationIndex < 0
            || observationFingerprint == null
            || observationFingerprint.trim()
                .isEmpty()
            || requiredSeedFingerprint == null
            || requiredSeedFingerprint.trim()
                .isEmpty()
            || target == null
            || action == null
            || detail == null
            || detail.trim()
                .isEmpty()
            || reserveEvidence == null) {
            throw new IllegalArgumentException("target, action, and detail are required");
        }
        this.plot = plot;
        this.passRevision = passRevision;
        this.observationIndex = observationIndex;
        this.observationFingerprint = observationFingerprint.trim();
        this.requiredSeedFingerprint = requiredSeedFingerprint.trim();
        this.target = target;
        this.action = action;
        this.detail = detail.trim();
        this.reserveEvidence = reserveEvidence;
    }

    public NamedArea getPlot() {
        return plot;
    }

    public long getPassRevision() {
        return passRevision;
    }

    public int getObservationIndex() {
        return observationIndex;
    }

    public String getObservationFingerprint() {
        return observationFingerprint;
    }

    public String getRequiredSeedFingerprint() {
        return requiredSeedFingerprint;
    }

    public BasePosition getTarget() {
        return target;
    }

    public FarmActionKind getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public SeedReserveEvidence getReserveEvidence() {
        return reserveEvidence;
    }

    public boolean requiresMutation() {
        return action == FarmActionKind.BREAK_AND_REPLANT || action == FarmActionKind.RIGHT_CLICK_HARVEST;
    }

    public boolean requiresPostconditionVerification() {
        return requiresMutation();
    }

    /** Exact pre-action authority check; it must be repeated immediately before execution. */
    public boolean isCurrentFor(NamedArea currentPlot, CropObservation currentObservation,
        SeedReserveEvidence currentReserveEvidence) {
        boolean reserveIsCurrent = action != FarmActionKind.BREAK_AND_REPLANT
            || currentReserveEvidence != null && currentReserveEvidence.isForMaterial(requiredSeedFingerprint)
                && reserveEvidence.isSameSnapshot(currentReserveEvidence);
        return currentPlot != null && currentObservation != null
            && plot.equals(currentPlot)
            && target.equals(currentObservation.getPosition())
            && observationFingerprint.equals(currentObservation.getObservationFingerprint())
            && requiredSeedFingerprint.equals(currentObservation.getRequiredSeedFingerprint())
            && reserveIsCurrent;
    }
}
