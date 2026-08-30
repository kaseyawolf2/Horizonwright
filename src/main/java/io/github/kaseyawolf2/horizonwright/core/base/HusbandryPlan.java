package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HusbandryPlan {

    private final NamedArea pen;
    private final LivestockSpecies species;
    private final long policyRevision;
    private final int minimumAdults;
    private final int maximumAdults;
    private final long observationRevision;
    private final String observationFingerprint;
    private final int observedAdults;
    private final int projectedAdults;
    private final List<HusbandryAction> actions;
    private final String holdReason;
    private final HusbandryTransitionToken transitionToken;

    HusbandryPlan(HusbandryPolicy policy, HusbandryObservation observation, int observedAdults, int projectedAdults,
        List<HusbandryAction> actions, String holdReason) {
        NamedArea pen = policy == null ? null : policy.getPen();
        LivestockSpecies species = policy == null ? null : policy.getSpecies();
        long observationRevision = observation == null ? -1L : observation.getRevision();
        String observationFingerprint = observation == null ? null : observation.getObservationFingerprint();
        if (pen == null || species == null
            || observationRevision < 0L
            || observationFingerprint == null
            || observationFingerprint.trim()
                .isEmpty()
            || observedAdults < 0
            || projectedAdults < 0
            || actions == null
            || actions.contains(null)
            || actions.size() > 1
            || holdReason != null && holdReason.trim()
                .isEmpty()
            || !actions.isEmpty() && holdReason != null) {
            throw new IllegalArgumentException("invalid husbandry plan");
        }
        this.pen = pen;
        this.species = species;
        this.policyRevision = policy.getRevision();
        this.minimumAdults = policy.getMinimumAdults();
        this.maximumAdults = policy.getMaximumAdults();
        this.observationRevision = observationRevision;
        this.observationFingerprint = observationFingerprint.trim();
        this.observedAdults = observedAdults;
        this.projectedAdults = projectedAdults;
        this.actions = Collections.unmodifiableList(new ArrayList<HusbandryAction>(actions));
        this.holdReason = holdReason == null ? null : holdReason.trim();
        HusbandryAction action = actions.isEmpty() ? null : actions.get(0);
        if (action != null && action.getDropTarget() != null
            && !pen.contains(
                action.getDropTarget()
                    .getPosition())) {
            throw new IllegalArgumentException("drop action target must be inside the named pen");
        }
        this.transitionToken = action == null ? null : new HusbandryTransitionToken(policy, observation, action);
    }

    public String getPenId() {
        return pen.getId();
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

    public int getObservedAdults() {
        return observedAdults;
    }

    public int getProjectedAdults() {
        return projectedAdults;
    }

    public List<HusbandryAction> getActions() {
        return actions;
    }

    public boolean isHeld() {
        return holdReason != null;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public boolean requiresPostconditionVerification() {
        return !actions.isEmpty();
    }

    /** Every action consumes this snapshot; callers must capture a new observation before planning again. */
    public boolean requiresFreshObservationAfterAction() {
        return !actions.isEmpty();
    }

    public HusbandryTransitionToken getTransitionToken() {
        return transitionToken;
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
            && (!requiresPostconditionVerification()
                || currentObservation.isCompletePenScan() && currentObservation.isEntirePenLoaded());
    }
}
