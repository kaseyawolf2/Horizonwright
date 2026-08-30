package io.github.kaseyawolf2.horizonwright.core.base;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class HusbandryAndSleepPlannerTest {

    private final HusbandryPlanner husbandry = new HusbandryPlanner();
    private final NamedArea pen = new NamedArea(
        "cow-pen",
        "Cow Pen",
        new BasePosition(0, 0, 60, 0),
        new BasePosition(0, 10, 70, 10));
    private final HusbandryPolicy cows = new HusbandryPolicy(pen, LivestockSpecies.COW, 7L, 2, 4);

    @Test
    public void feedingProducesPolicyAndObservationBoundTransitionToken() {
        HusbandryPolicy breedingPolicy = new HusbandryPolicy(pen, LivestockSpecies.COW, 12L, 4, 6);
        HusbandryObservation initial = observation(
            1L,
            "animals:1",
            Arrays.asList(
                animal("a", LivestockSpecies.COW, true, false, false, false, true, false),
                animal("b", LivestockSpecies.COW, true, false, false, false, true, false)),
            noDrops(),
            true,
            true);

        HusbandryPlan plan = husbandry.plan(breedingPolicy, initial);
        assertSingleAnimalAction(plan, HusbandryActionKind.FEED_ADULT, "a");
        HusbandryTransitionToken token = plan.getTransitionToken();
        assertNotNull(token);
        assertTrue(plan.isCurrentFor(breedingPolicy, initial));
        assertTrue(token.isCurrentFor(breedingPolicy, initial));

        HusbandryPolicy revised = new HusbandryPolicy(pen, LivestockSpecies.COW, 13L, 4, 6);
        HusbandryPolicy rebound = new HusbandryPolicy(pen, LivestockSpecies.COW, 12L, 3, 6);
        assertFalse(plan.isCurrentFor(revised, initial));
        assertFalse(plan.isCurrentFor(rebound, initial));
        assertFalse(token.isCurrentFor(revised, initial));
        assertFalse(
            token.isCurrentFor(
                breedingPolicy,
                observation(2L, "animals:2", initial.getAnimals(), noDrops(), true, true)));
    }

    @Test
    public void incompleteOrUnloadedPenScanCanNeverAuthorizeMutation() {
        List<AnimalObservation> excess = fiveEligibleCows();
        HusbandryPlan incomplete = husbandry.plan(cows, observation(2L, "incomplete", excess, noDrops(), false, true));
        HusbandryPlan unloaded = husbandry.plan(cows, observation(3L, "unloaded", excess, noDrops(), true, false));

        assertHeldWithoutToken(incomplete);
        assertHeldWithoutToken(unloaded);
        assertTrue(
            incomplete.getHoldReason()
                .contains("completely scanned"));
    }

    @Test
    public void cullingUsesOneBoundedActionAndPreservesProtectedPopulation() {
        List<AnimalObservation> animals = new ArrayList<AnimalObservation>(fiveEligibleCows());
        animals.add(animal("named", LivestockSpecies.COW, true, true, false, false, false, false));
        animals.add(animal("protected", LivestockSpecies.COW, true, false, false, true, false, false));

        HusbandryPlan first = husbandry.plan(cows, observation(10L, "cull:1", animals, noDrops(), true, true));
        assertSingleAnimalAction(first, HusbandryActionKind.CULL_EXCESS_ADULT, "e");
        assertEquals(7, first.getObservedAdults());
        assertEquals(6, first.getProjectedAdults());
        assertEquals(7L, first.getPolicyRevision());

        HusbandryPolicy impossible = new HusbandryPolicy(pen, LivestockSpecies.COW, 8L, 2, 2);
        HusbandryPlan held = husbandry.plan(impossible, observation(11L, "protected", animals, noDrops(), true, true));
        assertHeldWithoutToken(held);
    }

    @Test
    public void dropCollectionTargetsOneTypedDropStrictlyInsideNamedPen() {
        HusbandryDropObservation outside = new HusbandryDropObservation(
            "outside-drop",
            "minecraft:beef",
            new BasePosition(0, 30, 64, 30));
        HusbandryDropObservation inside = new HusbandryDropObservation(
            "inside-drop",
            "minecraft:leather",
            new BasePosition(0, 5, 64, 5));
        List<AnimalObservation> stable = Arrays.asList(
            animal("a", LivestockSpecies.COW, true, false, false, false, false, false),
            animal("b", LivestockSpecies.COW, true, false, false, false, false, false));

        HusbandryPlan plan = husbandry
            .plan(cows, observation(20L, "drops", stable, Arrays.asList(outside, inside), true, true));
        assertEquals(
            1,
            plan.getActions()
                .size());
        HusbandryAction action = plan.getActions()
            .get(0);
        assertEquals(HusbandryActionKind.COLLECT_DROPS, action.getKind());
        assertNull(action.getAnimalIdentity());
        assertEquals(inside, action.getDropTarget());
        assertTrue(
            pen.contains(
                action.getDropTarget()
                    .getPosition()));
        assertEquals(
            "minecraft:leather",
            plan.getTransitionToken()
                .getTargetFingerprint());

        HusbandryPlan outsideOnly = husbandry
            .plan(cows, observation(21L, "outside-only", stable, Collections.singletonList(outside), true, true));
        assertTrue(
            outsideOnly.getActions()
                .isEmpty());
        assertNull(outsideOnly.getTransitionToken());
    }

    @Test
    public void crossPenAndOversizedSnapshotsAreRejected() {
        HusbandryObservation valid = observation(30L, "valid", fiveEligibleCows(), noDrops(), true, true);
        NamedArea otherPen = new NamedArea("other", "Other", pen.getMinimum(), pen.getMaximum());
        HusbandryObservation wrongPen = new HusbandryObservation(
            otherPen,
            30L,
            "valid",
            valid.getAnimals(),
            noDrops(),
            true,
            true);
        assertRejected(() -> husbandry.plan(cows, wrongPen));

        List<HusbandryDropObservation> oversized = new ArrayList<HusbandryDropObservation>();
        for (int index = 0; index <= HusbandryObservation.MAX_DROP_CANDIDATES; index++) {
            oversized
                .add(new HusbandryDropObservation("drop-" + index, "minecraft:beef", new BasePosition(0, 5, 64, 5)));
        }
        assertArgumentRejected(() -> observation(31L, "huge", noAnimals(), oversized, true, true));
    }

    @Test
    public void sleepDecisionIsARevisionBoundCurrentnessToken() {
        SleepPlanner sleep = new SleepPlanner();
        BasePosition bed = new BasePosition(0, 1, 64, 1);
        SleepObservation safe = bedObservation(40L, "sleep:40", 13000L, true, false, bed, true, true);

        SleepDecision decision = sleep.plan(safe);
        assertEquals(SleepActionKind.USE_REGISTERED_BED, decision.getAction());
        assertTrue(decision.requiresInteraction());
        assertTrue(decision.isCurrentFor(sleep.getSleepWindow(), safe));
        assertFalse(
            decision.isCurrentFor(
                sleep.getSleepWindow(),
                bedObservation(41L, "sleep:41", 13000L, true, false, bed, true, true)));
        assertFalse(
            decision.isCurrentFor(
                sleep.getSleepWindow(),
                bedObservation(40L, "sleep:40", 13001L, true, false, bed, true, true)));
        assertFalse(
            decision.isCurrentFor(
                sleep.getSleepWindow(),
                bedObservation(40L, "sleep:40", 13000L, true, true, bed, true, true)));
        assertFalse(decision.isCurrentFor(sleep.getSleepWindow(), portableObservation(40L, "sleep:40", 13000L)));
    }

    @Test
    public void sleepFailsClosedAndWraparoundWindowRetainsExactBoundaries() {
        SleepPlanner sleep = new SleepPlanner();
        BasePosition bed = new BasePosition(0, 1, 64, 1);
        assertSleep(
            SleepActionKind.SKIP_DAYTIME,
            sleep.plan(bedObservation(1L, "day", 6000L, true, false, bed, true, true)));
        assertSleep(
            SleepActionKind.HOLD_INVALID_DIMENSION,
            sleep.plan(bedObservation(2L, "invalid", 13000L, false, false, bed, true, true)));
        assertSleep(
            SleepActionKind.HOLD_DANGER,
            sleep.plan(bedObservation(3L, "danger", 13000L, true, true, bed, true, true)));
        assertSleep(
            SleepActionKind.HOLD_PROVIDER_UNAVAILABLE,
            sleep.plan(bedObservation(4L, "missing", 13000L, true, false, bed, false, true)));

        SleepPlanner wrapping = new SleepPlanner(new SleepWindow(24000L, 22000L, 2000L));
        assertSleep(SleepActionKind.SKIP_DAYTIME, wrapping.plan(portableObservation(10L, "before", 21999L)));
        assertSleep(SleepActionKind.USE_PORTABLE_PROVIDER, wrapping.plan(portableObservation(11L, "start", 22000L)));
        assertSleep(SleepActionKind.USE_PORTABLE_PROVIDER, wrapping.plan(portableObservation(12L, "inside", 1999L)));
        assertSleep(SleepActionKind.SKIP_DAYTIME, wrapping.plan(portableObservation(13L, "end", 2000L)));
    }

    private HusbandryObservation observation(long revision, String fingerprint, List<AnimalObservation> animals,
        List<HusbandryDropObservation> drops, boolean complete, boolean loaded) {
        return new HusbandryObservation(pen, revision, fingerprint, animals, drops, complete, loaded);
    }

    private AnimalObservation animal(String id, LivestockSpecies species, boolean adult, boolean named, boolean tamed,
        boolean protectedStock, boolean ready, boolean engaged) {
        return new AnimalObservation(
            id,
            species,
            new BasePosition(0, 5, 64, 5),
            adult,
            named,
            tamed,
            protectedStock,
            ready,
            engaged);
    }

    private List<AnimalObservation> fiveEligibleCows() {
        return Arrays.asList(
            animal("a", LivestockSpecies.COW, true, false, false, false, false, false),
            animal("b", LivestockSpecies.COW, true, false, false, false, false, false),
            animal("c", LivestockSpecies.COW, true, false, false, false, false, false),
            animal("d", LivestockSpecies.COW, true, false, false, false, false, false),
            animal("e", LivestockSpecies.COW, true, false, false, false, false, false));
    }

    private static List<AnimalObservation> noAnimals() {
        return Collections.emptyList();
    }

    private static List<HusbandryDropObservation> noDrops() {
        return Collections.emptyList();
    }

    private static SleepObservation bedObservation(long revision, String fingerprint, long worldTime,
        boolean validDimension, boolean danger, BasePosition bed, boolean available, boolean reachable) {
        return new SleepObservation(
            revision,
            fingerprint,
            0,
            worldTime,
            validDimension,
            danger,
            SleepProviderKind.REGISTERED_BED,
            bed,
            available,
            reachable);
    }

    private static SleepObservation portableObservation(long revision, String fingerprint, long worldTime) {
        return new SleepObservation(
            revision,
            fingerprint,
            7,
            worldTime,
            true,
            false,
            SleepProviderKind.ADVENTURE_BACKPACK,
            null,
            true,
            true);
    }

    private static void assertSingleAnimalAction(HusbandryPlan plan, HusbandryActionKind kind, String identity) {
        assertFalse(plan.isHeld());
        assertEquals(
            1,
            plan.getActions()
                .size());
        assertEquals(
            kind,
            plan.getActions()
                .get(0)
                .getKind());
        assertEquals(
            identity,
            plan.getActions()
                .get(0)
                .getAnimalIdentity());
    }

    private static void assertHeldWithoutToken(HusbandryPlan plan) {
        assertTrue(plan.isHeld());
        assertTrue(
            plan.getActions()
                .isEmpty());
        assertNull(plan.getTransitionToken());
    }

    private static void assertSleep(SleepActionKind action, SleepDecision decision) {
        assertEquals(action, decision.getAction());
    }

    private static void assertRejected(Runnable operation) {
        try {
            operation.run();
            fail("expected authority violation");
        } catch (IllegalStateException expected) {
            assertFalse(
                expected.getMessage()
                    .isEmpty());
        }
    }

    private static void assertArgumentRejected(Runnable operation) {
        try {
            operation.run();
            fail("expected invalid bounded observation to be rejected");
        } catch (IllegalArgumentException expected) {
            assertFalse(
                expected.getMessage()
                    .isEmpty());
        }
    }
}
