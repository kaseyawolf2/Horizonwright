package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A closed-pen cohort, not a repeating population threshold. State is persisted before dispatch. */
public final class HusbandryBreedingCycle {

    private final Set<String> baseline = new LinkedHashSet<>();
    private final Set<String> cohort = new LinkedHashSet<>();
    private final Set<String> attempted = new LinkedHashSet<>();
    private final Set<String> fed = new LinkedHashSet<>();
    private final Set<String> newborns = new LinkedHashSet<>();
    private String phase = "NEW";
    private int initialAdults;
    private int reservedCulls;
    private long waitingMillis;
    private long lastWait = -1L;
    private String diagnostic = "";
    private boolean waiting;

    public HusbandryBreedingCycle(Map<String, String> values) {
        if (!values.containsKey("cycle.phase")) return;
        phase = values.get("cycle.phase");
        if (!phase.equals("NEW") && !phase.equals("FEED") && !phase.equals("BIRTHS") && !phase.equals("CULL"))
            throw new IllegalArgumentException("invalid breeding cycle phase");
        read(values, "baseline", baseline);
        read(values, "cohort", cohort);
        read(values, "attempted", attempted);
        read(values, "fed", fed);
        read(values, "newborns", newborns);
        initialAdults = Integer.parseInt(values.get("cycle.adults"));
        reservedCulls = Integer.parseInt(values.get("cycle.culls"));
        waitingMillis = Long.parseLong(values.get("cycle.wait"));
        if (initialAdults < 0 || reservedCulls < 0
            || reservedCulls > newborns.size()
            || waitingMillis < 0
            || !baseline.containsAll(cohort)
            || !cohort.containsAll(attempted)
            || !attempted.containsAll(fed)
            || !Collections.disjoint(baseline, newborns))
            throw new IllegalArgumentException("invalid breeding cycle checkpoint");
    }

    public HusbandryPlan plan(HusbandryPolicy policy, HusbandryObservation observation, boolean allowCulling,
        long nowMillis) {
        waiting = false;
        if (!policy.getPen()
            .equals(observation.getPen()) || !observation.isCompletePenScan() || !observation.isEntirePenLoaded())
            return held(policy, observation, "Breeding cycle needs a completely loaded, scanned pen");
        List<AnimalObservation> animals = new ArrayList<>();
        int adults = 0;
        int eligibleAdults = 0;
        for (AnimalObservation animal : observation.getAnimals()) {
            if (animal.getSpecies() != policy.getSpecies() || !policy.getPen()
                .contains(animal.getPosition())) continue;
            animals.add(animal);
            if (animal.isAdult()) adults++;
            if (animal.isEligibleTarget()) eligibleAdults++;
        }
        if (phase.equals("NEW")) {
            initialAdults = adults;
            List<String> ready = new ArrayList<>();
            for (AnimalObservation animal : animals) {
                baseline.add(animal.getIdentity());
                if (animal.isEligibleFeedTarget()) ready.add(animal.getIdentity());
            }
            Collections.sort(ready);
            // An unpaired adult cannot create a birth and should not consume feed.
            if (ready.size() % 2 != 0) ready.remove(ready.size() - 1);
            cohort.addAll(ready);
            phase = "FEED";
        }
        int previousBirths = newborns.size();
        for (AnimalObservation animal : animals) {
            if (!animal.isAdult() && !baseline.contains(animal.getIdentity())) newborns.add(animal.getIdentity());
        }
        if (newborns.size() > previousBirths) {
            waitingMillis = 0L;
            lastWait = nowMillis;
        }
        if (phase.equals("FEED")) {
            for (String identity : cohort) {
                if (attempted.contains(identity)) continue;
                for (AnimalObservation animal : animals) {
                    if (identity.equals(animal.getIdentity()) && animal.isEligibleFeedTarget()) {
                        diagnostic = "Feeding breeding cohort: " + fed.size() + "/" + cohort.size();
                        return new HusbandryPlan(
                            policy,
                            observation,
                            adults,
                            adults,
                            Collections
                                .singletonList(new HusbandryAction(HusbandryActionKind.FEED_ADULT, identity, null)),
                            null);
                    }
                }
                // It disappeared, was protected, or another actor fed it. It grants no replacement credit.
                attempted.add(identity);
            }
            phase = "BIRTHS";
        }
        int expectedBirths = fed.size() / 2;
        int credits = Math.min(expectedBirths, newborns.size());
        if (phase.equals("BIRTHS")) {
            if (credits < expectedBirths) {
                if (lastWait >= 0L) waitingMillis += Math.max(0L, nowMillis - lastWait);
                lastWait = nowMillis;
                diagnostic = "Waiting for newborns: " + credits + "/" + expectedBirths;
                if (waitingMillis >= 120_000L) {
                    if (credits == 0)
                        return held(policy, observation, diagnostic + "; no newborns confirmed after two minutes");
                } else {
                    waiting = true;
                    return new HusbandryPlan(policy, observation, adults, adults, Collections.emptyList(), null);
                }
            }
            phase = "CULL";
            lastWait = -1L;
        }
        int floor = Math.max(policy.getMinimumAdults(), initialAdults - credits);
        if (allowCulling && reservedCulls < credits
            && adults > floor
            && eligibleAdults > 2
            && animals.size() > baseline.size()) {
            // Ask the existing freshly revalidated knife/cull backend for exactly ONE adult.
            // Using currentAdults-1 avoids demanding that protected animals satisfy all remaining credits.
            HusbandryPolicy oneReplacement = new HusbandryPolicy(
                policy.getPen(),
                policy.getSpecies(),
                policy.getRevision(),
                policy.getMinimumAdults(),
                adults - 1);
            HusbandryPlan cull = new HusbandryPlanner().plan(oneReplacement, observation, true);
            diagnostic = "Replacement culls " + reservedCulls + "/" + credits + "; new babies " + newborns.size();
            return cull;
        }
        diagnostic = "Breeding cycle finished: " + fed.size()
            + " adults fed, "
            + credits
            + " newborn(s), "
            + reservedCulls
            + " replacement cull(s) dispatched"
            + (!allowCulling ? "; culling disabled" : "; adult breeding reserve preserved");
        // Collection must not re-enter the old feed-below-minimum policy.
        for (HusbandryDropObservation drop : observation.getDrops()) {
            if (policy.getPen()
                .contains(drop.getPosition()))
                return new HusbandryPlan(
                    policy,
                    observation,
                    adults,
                    adults,
                    Collections.singletonList(new HusbandryAction(HusbandryActionKind.COLLECT_DROPS, null, drop)),
                    null);
        }
        return new HusbandryPlan(policy, observation, adults, adults, Collections.emptyList(), null);
    }

    public void dispatched(HusbandryAction action) {
        if (action.getKind() == HusbandryActionKind.FEED_ADULT) attempted.add(action.getAnimalIdentity());
        if (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT) reservedCulls++;
    }

    public void confirmed(HusbandryActionKind kind, String identity) {
        if (kind == HusbandryActionKind.FEED_ADULT) fed.add(identity);
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void pauseClock() {
        lastWait = -1L;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public void save(Map<String, String> values) {
        values.put("cycle.phase", phase);
        write(values, "baseline", baseline);
        write(values, "cohort", cohort);
        write(values, "attempted", attempted);
        write(values, "fed", fed);
        write(values, "newborns", newborns);
        values.put("cycle.adults", Integer.toString(initialAdults));
        values.put("cycle.culls", Integer.toString(reservedCulls));
        values.put("cycle.wait", Long.toString(waitingMillis));
    }

    private static void write(Map<String, String> values, String key, Set<String> identities) {
        // Separate entries avoid imposing an unbounded string on the persistence codec.
        int index = 0;
        for (String identity : identities) values.put("cycle." + key + "." + index++, identity);
    }

    private static void read(Map<String, String> values, String key, Set<String> identities) {
        for (int index = 0; values.containsKey("cycle." + key + "." + index); index++)
            identities.add(values.get("cycle." + key + "." + index));
    }

    private static HusbandryPlan held(HusbandryPolicy policy, HusbandryObservation observation, String detail) {
        return new HusbandryPlan(policy, observation, 0, 0, Collections.emptyList(), detail);
    }
}
