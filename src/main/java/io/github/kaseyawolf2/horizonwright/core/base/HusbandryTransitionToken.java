package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Objects;

/** Immutable one-action token for persisted replay protection at the execution boundary. */
public final class HusbandryTransitionToken {

    private final NamedArea pen;
    private final LivestockSpecies species;
    private final long policyRevision;
    private final int minimumAdults;
    private final int maximumAdults;
    private final long observationRevision;
    private final String observationFingerprint;
    private final HusbandryActionKind actionKind;
    private final String targetIdentity;
    private final String targetFingerprint;
    private final BasePosition targetPosition;

    HusbandryTransitionToken(HusbandryPolicy policy, HusbandryObservation observation, HusbandryAction action) {
        if (policy == null || observation == null || action == null) {
            throw new IllegalArgumentException("policy, observation, and action are required");
        }
        pen = policy.getPen();
        species = policy.getSpecies();
        policyRevision = policy.getRevision();
        minimumAdults = policy.getMinimumAdults();
        maximumAdults = policy.getMaximumAdults();
        observationRevision = observation.getRevision();
        observationFingerprint = observation.getObservationFingerprint();
        actionKind = action.getKind();
        HusbandryDropObservation drop = action.getDropTarget();
        targetIdentity = drop == null ? action.getAnimalIdentity() : drop.getIdentity();
        targetFingerprint = drop == null ? null : drop.getItemFingerprint();
        targetPosition = drop == null ? null : drop.getPosition();
    }

    public boolean isCurrentFor(HusbandryPolicy currentPolicy, HusbandryObservation currentObservation) {
        return currentPolicy != null && currentObservation != null
            && pen.equals(currentPolicy.getPen())
            && species == currentPolicy.getSpecies()
            && policyRevision == currentPolicy.getRevision()
            && minimumAdults == currentPolicy.getMinimumAdults()
            && maximumAdults == currentPolicy.getMaximumAdults()
            && pen.equals(currentObservation.getPen())
            && observationRevision == currentObservation.getRevision()
            && observationFingerprint.equals(currentObservation.getObservationFingerprint())
            && currentObservation.isCompletePenScan()
            && currentObservation.isEntirePenLoaded();
    }

    public NamedArea getPen() {
        return pen;
    }

    public LivestockSpecies getSpecies() {
        return species;
    }

    public long getPolicyRevision() {
        return policyRevision;
    }

    public int getMinimumAdults() {
        return minimumAdults;
    }

    public int getMaximumAdults() {
        return maximumAdults;
    }

    public long getObservationRevision() {
        return observationRevision;
    }

    public String getObservationFingerprint() {
        return observationFingerprint;
    }

    public HusbandryActionKind getActionKind() {
        return actionKind;
    }

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public String getTargetFingerprint() {
        return targetFingerprint;
    }

    public BasePosition getTargetPosition() {
        return targetPosition;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HusbandryTransitionToken)) {
            return false;
        }
        HusbandryTransitionToken that = (HusbandryTransitionToken) other;
        return policyRevision == that.policyRevision && minimumAdults == that.minimumAdults
            && maximumAdults == that.maximumAdults
            && observationRevision == that.observationRevision
            && pen.equals(that.pen)
            && species == that.species
            && observationFingerprint.equals(that.observationFingerprint)
            && actionKind == that.actionKind
            && Objects.equals(targetIdentity, that.targetIdentity)
            && Objects.equals(targetFingerprint, that.targetFingerprint)
            && Objects.equals(targetPosition, that.targetPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            pen,
            species,
            policyRevision,
            minimumAdults,
            maximumAdults,
            observationRevision,
            observationFingerprint,
            actionKind,
            targetIdentity,
            targetFingerprint,
            targetPosition);
    }
}
