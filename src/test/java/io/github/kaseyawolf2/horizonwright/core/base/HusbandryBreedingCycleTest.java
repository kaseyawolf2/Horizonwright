package io.github.kaseyawolf2.horizonwright.core.base;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class HusbandryBreedingCycleTest {

    private static final NamedArea PEN = new NamedArea(
        "pen",
        "Pen",
        new BasePosition(0, 0, 60, 0),
        new BasePosition(0, 10, 70, 10));
    private static final HusbandryPolicy POLICY = new HusbandryPolicy(PEN, LivestockSpecies.COW, 1L, 2, 8);

    @Test
    public void breedsAllPairsThenReplacesExactlyObservedBabiesAndKeepsPair() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(8);
        feedAll(cycle, animals, 8);
        assertTrue(
            cycle.plan(POLICY, observe(animals), true, 1000L)
                .getActions()
                .isEmpty());
        assertTrue(cycle.isWaiting());
        addBabies(animals, 4);
        for (int kill = 0; kill < 4; kill++) {
            HusbandryPlan plan = cycle.plan(POLICY, observe(animals), true, 2000L);
            assertEquals(
                HusbandryActionKind.CULL_EXCESS_ADULT,
                plan.getActions()
                    .get(0)
                    .getKind());
            HusbandryAction action = plan.getActions()
                .get(0);
            cycle.dispatched(action);
            animals.removeIf(
                animal -> animal.getIdentity()
                    .equals(action.getAnimalIdentity()));
            cycle = restored(cycle);
        }
        assertTrue(
            cycle.plan(POLICY, observe(animals), true, 3000L)
                .getActions()
                .isEmpty());
        assertEquals(8, animals.size());
        assertEquals(
            4L,
            animals.stream()
                .filter(AnimalObservation::isAdult)
                .count());
    }

    @Test
    public void existingBabiesDoNotGrantReplacementCredit() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(4);
        addBabies(animals, 2);
        feedAll(cycle, animals, 4);
        cycle.plan(POLICY, observe(animals), true, 1000L);
        assertTrue(cycle.isWaiting());
        HusbandryPlan timeout = cycle.plan(POLICY, observe(animals), true, 121001L);
        assertTrue(timeout.isHeld());
        assertTrue(
            timeout.getActions()
                .isEmpty());
    }

    @Test
    public void partialBirthsAllowOnlyActualReplacementsAfterWaiting() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(8);
        feedAll(cycle, animals, 8);
        addBabies(animals, 2);
        cycle.plan(POLICY, observe(animals), true, 1000L);
        assertTrue(cycle.isWaiting());
        for (int index = 0; index < 2; index++) {
            HusbandryAction action = cycle.plan(POLICY, observe(animals), true, 121001L)
                .getActions()
                .get(0);
            assertEquals(HusbandryActionKind.CULL_EXCESS_ADULT, action.getKind());
            cycle.dispatched(action);
            animals.removeIf(
                animal -> animal.getIdentity()
                    .equals(action.getAnimalIdentity()));
        }
        assertTrue(
            cycle.plan(POLICY, observe(animals), true, 122001L)
                .getActions()
                .isEmpty());
        assertEquals(8, animals.size());
    }

    @Test
    public void unrelatedHerdLossPreventsShrinkingBelowStartingPopulation() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(4);
        feedAll(cycle, animals, 4);
        addBabies(animals, 2);
        HusbandryAction first = cycle.plan(POLICY, observe(animals), true, 1000L)
            .getActions()
            .get(0);
        cycle.dispatched(first);
        animals.removeIf(
            animal -> animal.getIdentity()
                .equals(first.getAnimalIdentity()));
        animals.removeIf(
            animal -> animal.getIdentity()
                .equals("baby-0"));
        assertTrue(
            cycle.plan(POLICY, observe(animals), true, 2000L)
                .getActions()
                .isEmpty());
    }

    @Test
    public void configuredMinimumAdultReserveStopsReplacementEarly() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(8);
        feedAll(cycle, animals, 8);
        addBabies(animals, 4);
        HusbandryPolicy largerReserve = new HusbandryPolicy(PEN, LivestockSpecies.COW, 1L, 6, 8);
        for (int index = 0; index < 2; index++) {
            HusbandryAction action = cycle.plan(largerReserve, observe(animals), true, 1000L)
                .getActions()
                .get(0);
            cycle.dispatched(action);
            animals.removeIf(
                animal -> animal.getIdentity()
                    .equals(action.getAnimalIdentity()));
        }
        assertTrue(
            cycle.plan(largerReserve, observe(animals), true, 2000L)
                .getActions()
                .isEmpty());
        assertEquals(
            6L,
            animals.stream()
                .filter(AnimalObservation::isAdult)
                .count());
    }

    @Test
    public void reserveOverridesOneForOneReplacement() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(2);
        feedAll(cycle, animals, 2);
        addBabies(animals, 1);
        assertTrue(
            cycle.plan(POLICY, observe(animals), true, 1000L)
                .getActions()
                .isEmpty());
        assertFalse(cycle.isWaiting());
        assertEquals(3, animals.size());
    }

    @Test
    public void disablingCullingStillBreedsButNeverAttacks() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(4);
        feedAll(cycle, animals, 4);
        addBabies(animals, 2);
        assertTrue(
            cycle.plan(POLICY, observe(animals), false, 1000L)
                .getActions()
                .isEmpty());
        assertTrue(
            cycle.getDiagnostic()
                .contains("culling disabled"));
    }

    @Test
    public void interruptedDispatchCannotReuseReplacementCredit() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(4);
        feedAll(cycle, animals, 4);
        addBabies(animals, 2);
        HusbandryAction first = cycle.plan(POLICY, observe(animals), true, 1000L)
            .getActions()
            .get(0);
        cycle.dispatched(first);
        cycle = restored(cycle);
        // Unknown outcome: do not spend this credit a second time, even if target still appears alive.
        HusbandryAction second = cycle.plan(POLICY, observe(animals), true, 1100L)
            .getActions()
            .get(0);
        cycle.dispatched(second);
        cycle = restored(cycle);
        assertTrue(
            cycle.plan(POLICY, observe(animals), true, 1200L)
                .getActions()
                .isEmpty());
    }

    @Test
    public void partialFeedDoesNotManufactureBirthCredits() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(2);
        HusbandryAction feed = cycle.plan(POLICY, observe(animals), true, 0L)
            .getActions()
            .get(0);
        cycle.dispatched(feed);
        cycle = restored(cycle); // No confirmed feed.
        HusbandryAction second = cycle.plan(POLICY, observe(animals), true, 100L)
            .getActions()
            .get(0);
        cycle.dispatched(second);
        cycle.confirmed(second.getKind(), second.getAnimalIdentity());
        addBabies(animals, 1);
        assertTrue(
            cycle.plan(POLICY, observe(animals), true, 1000L)
                .getActions()
                .isEmpty());
    }

    @Test
    public void babiesProtectedAndEngagedAdultsAreNeverCullTargets() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(4);
        feedAll(cycle, animals, 4);
        addBabies(animals, 2);
        animals.set(2, animal("adult-2", true, false, true, false));
        animals.set(3, animal("adult-3", true, false, false, true));
        HusbandryPlan plan = cycle.plan(POLICY, observe(animals), true, 1000L);
        assertEquals(
            "adult-1",
            plan.getActions()
                .get(0)
                .getAnimalIdentity());
    }

    @Test
    public void unloadedPenDoesNotStartCycleOrGrantCredits() {
        HusbandryBreedingCycle cycle = fresh();
        HusbandryObservation unloaded = new HusbandryObservation(
            PEN,
            1L,
            "unloaded",
            adults(4),
            Collections.emptyList(),
            true,
            false);
        assertTrue(
            cycle.plan(POLICY, unloaded, true, 0L)
                .isHeld());
        Map<String, String> state = new LinkedHashMap<>();
        cycle.save(state);
        assertEquals("NEW", state.get("cycle.phase"));
    }

    @Test
    public void oddAdultIsNotFedWithoutAPartner() {
        HusbandryBreedingCycle cycle = fresh();
        List<AnimalObservation> animals = adults(5);
        feedAll(cycle, animals, 4);
        cycle.plan(POLICY, observe(animals), true, 1000L);
        assertTrue(cycle.isWaiting());
    }

    private static void feedAll(HusbandryBreedingCycle cycle, List<AnimalObservation> animals, int count) {
        for (int index = 0; index < count; index++) {
            HusbandryPlan plan = cycle.plan(POLICY, observe(animals), false, index * 100L);
            HusbandryAction action = plan.getActions()
                .get(0);
            assertEquals(HusbandryActionKind.FEED_ADULT, action.getKind());
            cycle.dispatched(action);
            cycle.confirmed(action.getKind(), action.getAnimalIdentity());
            // Client snapshot intentionally still says ready: persisted identity prevents a duplicate feed.
        }
    }

    private static HusbandryBreedingCycle fresh() {
        return new HusbandryBreedingCycle(Collections.emptyMap());
    }

    private static HusbandryBreedingCycle restored(HusbandryBreedingCycle cycle) {
        Map<String, String> values = new LinkedHashMap<>();
        cycle.save(values);
        return new HusbandryBreedingCycle(values);
    }

    private static List<AnimalObservation> adults(int count) {
        List<AnimalObservation> animals = new ArrayList<>();
        for (int index = 0; index < count; index++) animals.add(animal("adult-" + index, true, true, false, false));
        return animals;
    }

    private static void addBabies(List<AnimalObservation> animals, int count) {
        for (int index = 0; index < count; index++) animals.add(animal("baby-" + index, false, false, false, false));
    }

    private static AnimalObservation animal(String id, boolean adult, boolean ready, boolean protectedStock,
        boolean engaged) {
        return new AnimalObservation(
            id,
            LivestockSpecies.COW,
            new BasePosition(0, 2, 64, 2),
            adult,
            false,
            false,
            protectedStock,
            ready,
            engaged);
    }

    private static HusbandryObservation observe(List<AnimalObservation> animals) {
        return new HusbandryObservation(PEN, 1L, "test", animals, Collections.emptyList(), true, true);
    }
}
