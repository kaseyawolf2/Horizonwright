package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Deterministic one-action policy; every action requires a new named-pen observation before another plan. */
public final class HusbandryPlanner {

    /** Revalidate the selected animal, not whether it is still first in a freshly sorted target list. */
    public boolean canCullTarget(HusbandryPolicy policy, HusbandryObservation observation, String identity) {
        if (!policy.getPen()
            .equals(observation.getPen()) || !observation.isCompletePenScan() || !observation.isEntirePenLoaded())
            return false;
        int adults = 0;
        int eligible = 0;
        boolean targetEligible = false;
        for (AnimalObservation animal : observation.getAnimals()) {
            if (animal.getSpecies() != policy.getSpecies() || !policy.getPen()
                .contains(animal.getPosition())) continue;
            if (animal.isAdult()) adults++;
            if (animal.isEligibleTarget()) eligible++;
            if (animal.getIdentity()
                .equals(identity)) targetEligible = animal.isEligibleTarget() && !animal.isBreedingEngaged();
        }
        return targetEligible && adults > policy.getMaximumAdults()
            && adults > Math.max(2, policy.getMinimumAdults())
            && eligible > 2;
    }

    public HusbandryPlan plan(HusbandryPolicy policy, HusbandryObservation observation) {
        return plan(policy, observation, true);
    }

    public HusbandryPlan plan(HusbandryPolicy policy, HusbandryObservation observation, boolean allowCulling) {
        if (policy == null || observation == null) {
            throw new IllegalArgumentException("policy and observation are required");
        }
        if (!policy.getPen()
            .equals(observation.getPen())) {
            throw new IllegalStateException("husbandry observation belongs to a different named pen");
        }
        if (!observation.isCompletePenScan() || !observation.isEntirePenLoaded()) {
            return held(policy, observation, 0, "the named pen is not completely scanned and loaded");
        }
        List<AnimalObservation> adults = new ArrayList<AnimalObservation>();
        List<AnimalObservation> eligible = new ArrayList<AnimalObservation>();
        List<AnimalObservation> cullable = new ArrayList<AnimalObservation>();
        for (AnimalObservation animal : observation.getAnimals()) {
            if (!policy.getPen()
                .contains(animal.getPosition()) || animal.getSpecies() != policy.getSpecies()) {
                continue;
            }
            if (animal.isAdult()) {
                adults.add(animal);
            }
            if (animal.isEligibleTarget()) {
                eligible.add(animal);
                if (!animal.isBreedingEngaged()) cullable.add(animal);
            }
        }
        Collections.sort(eligible, Comparator.comparing(AnimalObservation::getIdentity));
        Collections.sort(cullable, Comparator.comparing(AnimalObservation::getIdentity));

        if (adults.size() < policy.getMinimumAdults()) {
            List<AnimalObservation> ready = new ArrayList<AnimalObservation>();
            int engaged = 0;
            for (AnimalObservation animal : eligible) {
                if (animal.isEligibleFeedTarget()) {
                    ready.add(animal);
                }
                if (animal.isBreedingEngaged()) {
                    engaged++;
                }
            }
            if (engaged >= 2) {
                return idle(policy, observation, adults.size());
            }
            int requiredReady = engaged == 0 ? 2 : 1;
            if (ready.size() < requiredReady) {
                return held(
                    policy,
                    observation,
                    adults.size(),
                    "a safe breeding pair cannot be established from the current observation");
            }
            return action(
                policy,
                observation,
                adults.size(),
                adults.size(),
                new HusbandryAction(
                    HusbandryActionKind.FEED_ADULT,
                    ready.get(0)
                        .getIdentity(),
                    null));
        }

        int excess = adults.size() - policy.getMaximumAdults();
        if (excess <= 0 || !allowCulling) {
            List<HusbandryDropObservation> drops = new ArrayList<HusbandryDropObservation>();
            for (HusbandryDropObservation drop : observation.getDrops()) {
                if (policy.getPen()
                    .contains(drop.getPosition())) {
                    drops.add(drop);
                }
            }
            Collections.sort(drops, Comparator.comparing(HusbandryDropObservation::getIdentity));
            if (!drops.isEmpty()) {
                return action(
                    policy,
                    observation,
                    adults.size(),
                    adults.size(),
                    new HusbandryAction(HusbandryActionKind.COLLECT_DROPS, null, drops.get(0)));
            }
            return idle(policy, observation, adults.size());
        }
        int maximumSafeCullByPopulation = adults.size() - Math.max(2, policy.getMinimumAdults());
        int maximumSafeCullByBreedingPair = eligible.size() - 2;
        int maximumSafeCull = Math
            .max(0, Math.min(Math.min(maximumSafeCullByPopulation, maximumSafeCullByBreedingPair), cullable.size()));
        if (excess > maximumSafeCull) {
            return held(policy, observation, adults.size(), "protected population prevents a safe bounded cull");
        }
        AnimalObservation target = cullable.get(cullable.size() - 1);
        return action(
            policy,
            observation,
            adults.size(),
            adults.size() - 1,
            new HusbandryAction(HusbandryActionKind.CULL_EXCESS_ADULT, target.getIdentity(), null));
    }

    private static HusbandryPlan action(HusbandryPolicy policy, HusbandryObservation observation, int observedAdults,
        int projectedAdults, HusbandryAction action) {
        return new HusbandryPlan(
            policy,
            observation,
            observedAdults,
            projectedAdults,
            Collections.singletonList(action),
            null);
    }

    private static HusbandryPlan idle(HusbandryPolicy policy, HusbandryObservation observation, int adults) {
        return new HusbandryPlan(policy, observation, adults, adults, Collections.<HusbandryAction>emptyList(), null);
    }

    private static HusbandryPlan held(HusbandryPolicy policy, HusbandryObservation observation, int adults,
        String reason) {
        return new HusbandryPlan(policy, observation, adults, adults, Collections.<HusbandryAction>emptyList(), reason);
    }
}
