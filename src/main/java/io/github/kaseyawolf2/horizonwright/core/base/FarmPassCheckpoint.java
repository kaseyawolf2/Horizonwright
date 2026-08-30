package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Exact finite-pass frontier. A mutating decision advances only after its postcondition is verified. */
public final class FarmPassCheckpoint {

    private final NamedArea plot;
    private final long passRevision;
    private final List<BasePosition> observationTargets;
    private final List<String> observationFingerprints;
    private final List<String> requiredSeedFingerprints;
    private final int nextObservationIndex;
    private final int verifiedMutations;

    private FarmPassCheckpoint(NamedArea plot, long passRevision, List<BasePosition> observationTargets,
        List<String> observationFingerprints, List<String> requiredSeedFingerprints, int nextObservationIndex,
        int verifiedMutations) {
        if (plot == null || passRevision < 0L
            || observationTargets == null
            || observationFingerprints == null
            || requiredSeedFingerprints == null
            || observationTargets.size() != observationFingerprints.size()
            || observationTargets.size() != requiredSeedFingerprints.size()
            || observationTargets.contains(null)
            || observationFingerprints.contains(null)
            || requiredSeedFingerprints.contains(null)
            || nextObservationIndex < 0
            || nextObservationIndex > observationFingerprints.size()
            || verifiedMutations < 0
            || verifiedMutations > nextObservationIndex) {
            throw new IllegalArgumentException("invalid farm pass checkpoint");
        }
        this.plot = plot;
        this.passRevision = passRevision;
        this.observationTargets = Collections.unmodifiableList(new ArrayList<BasePosition>(observationTargets));
        this.observationFingerprints = Collections.unmodifiableList(new ArrayList<String>(observationFingerprints));
        this.requiredSeedFingerprints = Collections.unmodifiableList(new ArrayList<String>(requiredSeedFingerprints));
        this.nextObservationIndex = nextObservationIndex;
        this.verifiedMutations = verifiedMutations;
    }

    public static FarmPassCheckpoint start(NamedArea plot, long passRevision, List<CropObservation> observations) {
        return restore(plot, passRevision, observations, 0, 0);
    }

    public static FarmPassCheckpoint restore(NamedArea plot, long passRevision, List<CropObservation> observations,
        int nextObservationIndex, int verifiedMutations) {
        if (plot == null || passRevision < 0L || observations == null || observations.contains(null)) {
            throw new IllegalArgumentException("plot, pass revision, and observations are required");
        }
        List<BasePosition> targets = new ArrayList<BasePosition>(observations.size());
        List<String> fingerprints = new ArrayList<String>(observations.size());
        List<String> seedFingerprints = new ArrayList<String>(observations.size());
        for (CropObservation observation : observations) {
            targets.add(observation.getPosition());
            fingerprints.add(observation.getObservationFingerprint());
            seedFingerprints.add(observation.getRequiredSeedFingerprint());
        }
        return new FarmPassCheckpoint(
            plot,
            passRevision,
            targets,
            fingerprints,
            seedFingerprints,
            nextObservationIndex,
            verifiedMutations);
    }

    public FarmPassCheckpoint advance(FarmDecision decision, CropObservation beforeObservation,
        CropObservation afterObservation, SeedReserveEvidence currentReserveEvidence) {
        if (decision == null || beforeObservation == null
            || afterObservation == null
            || currentReserveEvidence == null) {
            throw new IllegalArgumentException(
                "decision, before/after observations, and reserve evidence are required");
        }
        if (isComplete()) {
            throw new IllegalStateException("a completed farm pass cannot advance");
        }
        if (!plot.equals(decision.getPlot()) || passRevision != decision.getPassRevision()
            || nextObservationIndex != decision.getObservationIndex()) {
            throw new IllegalStateException("farm decision does not belong to the current plot pass frontier");
        }
        if (!expectedTarget().equals(decision.getTarget()) || !expectedTarget().equals(beforeObservation.getPosition())
            || !expectedFingerprint().equals(decision.getObservationFingerprint())
            || !expectedFingerprint().equals(beforeObservation.getObservationFingerprint())
            || !expectedSeedFingerprint().equals(decision.getRequiredSeedFingerprint())
            || !expectedSeedFingerprint().equals(beforeObservation.getRequiredSeedFingerprint())
            || !decision.isCurrentFor(plot, beforeObservation, currentReserveEvidence)) {
            throw new IllegalStateException("farm observation changed after the decision was planned");
        }
        requireValidPostcondition(decision, beforeObservation, afterObservation);
        return new FarmPassCheckpoint(
            plot,
            passRevision,
            observationTargets,
            observationFingerprints,
            requiredSeedFingerprints,
            nextObservationIndex + 1,
            verifiedMutations + (decision.requiresMutation() ? 1 : 0));
    }

    private void requireValidPostcondition(FarmDecision decision, CropObservation beforeObservation,
        CropObservation afterObservation) {
        if (!expectedTarget().equals(afterObservation.getPosition())
            || beforeObservation.getFamily() != afterObservation.getFamily()
            || !expectedSeedFingerprint().equals(afterObservation.getRequiredSeedFingerprint())) {
            throw new IllegalStateException("farm postcondition belongs to a different crop target or material");
        }
        boolean changed = !beforeObservation.getObservationFingerprint()
            .equals(afterObservation.getObservationFingerprint());
        if (decision.requiresMutation()) {
            if (!changed || !afterObservation.isMaturityKnown() || afterObservation.isMature()) {
                throw new IllegalStateException("mutating farm action requires a changed, verified immature crop");
            }
        } else if (changed) {
            throw new IllegalStateException("a non-mutating farm decision cannot consume a changed observation");
        }
    }

    public NamedArea getPlot() {
        return plot;
    }

    public String getPlotId() {
        return plot.getId();
    }

    public long getPassRevision() {
        return passRevision;
    }

    public int getNextObservationIndex() {
        return nextObservationIndex;
    }

    public int getVerifiedMutations() {
        return verifiedMutations;
    }

    public int getObservationCount() {
        return observationFingerprints.size();
    }

    public List<BasePosition> getObservationTargets() {
        return observationTargets;
    }

    public List<String> getObservationFingerprints() {
        return observationFingerprints;
    }

    public List<String> getRequiredSeedFingerprints() {
        return requiredSeedFingerprints;
    }

    public boolean isComplete() {
        return nextObservationIndex == observationFingerprints.size();
    }

    void requireCurrentObservation(NamedArea candidatePlot, CropObservation observation) {
        if (candidatePlot == null || observation == null) {
            throw new IllegalArgumentException("plot and observation must not be null");
        }
        if (isComplete()) {
            throw new IllegalStateException("farm pass is already complete");
        }
        if (!plot.equals(candidatePlot)) {
            throw new IllegalStateException("farm checkpoint belongs to a different named plot");
        }
        if (!expectedTarget().equals(observation.getPosition())
            || !expectedFingerprint().equals(observation.getObservationFingerprint())
            || !expectedSeedFingerprint().equals(observation.getRequiredSeedFingerprint())) {
            throw new IllegalStateException("crop observation does not match the finite pass frontier");
        }
    }

    private BasePosition expectedTarget() {
        return observationTargets.get(nextObservationIndex);
    }

    private String expectedFingerprint() {
        return observationFingerprints.get(nextObservationIndex);
    }

    private String expectedSeedFingerprint() {
        return requiredSeedFingerprints.get(nextObservationIndex);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FarmPassCheckpoint)) {
            return false;
        }
        FarmPassCheckpoint that = (FarmPassCheckpoint) other;
        return passRevision == that.passRevision && nextObservationIndex == that.nextObservationIndex
            && verifiedMutations == that.verifiedMutations
            && plot.equals(that.plot)
            && observationTargets.equals(that.observationTargets)
            && observationFingerprints.equals(that.observationFingerprints)
            && requiredSeedFingerprints.equals(that.requiredSeedFingerprints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            plot,
            passRevision,
            observationTargets,
            observationFingerprints,
            requiredSeedFingerprints,
            nextObservationIndex,
            verifiedMutations);
    }
}
