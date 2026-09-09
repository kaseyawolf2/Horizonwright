package io.github.kaseyawolf2.horizonwright.core.inventory;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.inventory.GeneralizedInventoryPlanner.Move;
import io.github.kaseyawolf2.horizonwright.core.inventory.GeneralizedInventoryPlanner.Plan;
import io.github.kaseyawolf2.horizonwright.core.inventory.InventoryEndpoint.Kind;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;

public class GeneralizedInventoryPlannerTest {

    private final GeneralizedInventoryPlanner planner = new GeneralizedInventoryPlanner();

    @Test
    public void fullPlayerStowsCargoBeforeRetrievingTaskToolFromBag() {
        InventoryEndpoint player = endpoint("player", Kind.PLAYER, slot(0, item("ore", "a", 32)));
        InventoryEndpoint bag = endpoint("bag", Kind.PORTABLE, slot(0, item("pick", "tool", 1)), slot(1, null));

        Plan plan = planner.plan(Arrays.asList(player, bag), required("pick", 1), ignored -> false, ignored -> true);

        assertTrue(plan.isReadyForTask());
        assertEquals(
            2,
            plan.getMoves()
                .size());
        assertMove(
            plan.getMoves()
                .get(0),
            "player",
            0,
            "bag",
            1,
            32);
        assertMove(
            plan.getMoves()
                .get(1),
            "bag",
            0,
            "player",
            0,
            1);
        assertEquals(
            GeneralizedInventoryPlanner.Purpose.RETRIEVE,
            plan.getMoves()
                .get(1)
                .getPurpose());
        assertNull(
            plan.getMoves()
                .get(1)
                .getExpectedDestination());
        assertEquals(
            "ore",
            player.getSlots()
                .get(0)
                .getItem()
                .getItemId());
        assertNull(
            bag.getSlots()
                .get(1)
                .getItem());
    }

    @Test
    public void preservesCarrierFoodArmorAndRequiredMaterialQuantityWhileStowingSpareTools() {
        InventoryEndpoint player = endpoint(
            "player",
            Kind.PLAYER,
            slot(0, item("bag", "b", 1)),
            slot(1, item("food", "f", 12)),
            slot(2, item("armor", "a", 1)),
            slot(3, item("repair", "r", 20)),
            slot(4, item("axe", "t", 1)));
        InventoryEndpoint bag = new InventoryEndpoint(
            "bag",
            Kind.PORTABLE,
            true,
            true,
            true,
            new InventoryLocation("player", 0),
            Arrays.asList(slot(0, null), slot(1, null), slot(2, null), slot(3, null), slot(4, null)));

        Plan plan = planner.plan(
            Arrays.asList(player, bag),
            required("repair", 8),
            value -> value.getItemId()
                .equals("food")
                || value.getItemId()
                    .equals("armor"),
            ignored -> true);

        assertTrue(plan.isReadyForTask());
        assertEquals(
            2,
            plan.getMoves()
                .size());
        assertMove(
            plan.getMoves()
                .get(0),
            "player",
            3,
            "bag",
            0,
            12);
        assertMove(
            plan.getMoves()
                .get(1),
            "player",
            4,
            "bag",
            1,
            1);
    }

    @Test
    public void reservesOnlyOneNeededToolAndStowsDuplicate() {
        InventoryEndpoint player = endpoint(
            "player",
            Kind.PLAYER,
            slot(0, item("pick", "first", 1)),
            slot(1, item("pick", "second", 1)));
        Plan plan = planner.plan(
            Arrays.asList(player, endpoint("bag", Kind.PORTABLE, slot(0, null))),
            required("pick", 1),
            ignored -> false,
            ignored -> true);

        assertTrue(plan.isReadyForTask());
        assertEquals(
            1,
            plan.getMoves()
                .size());
        assertMove(
            plan.getMoves()
                .get(0),
            "player",
            1,
            "bag",
            0,
            1);
    }

    @Test
    public void fillsRestrictedBagBeforeGeneralBagRegardlessOfEndpointOrder() {
        InventoryEndpoint player = endpoint(
            "player",
            Kind.PLAYER,
            slot(0, item("ore", "o", 64)),
            slot(1, item("wood", "w", 32)));
        InventoryEndpoint general = endpoint("a-general", Kind.PORTABLE, slot(0, null));
        InventoryEndpoint mining = endpoint(
            "z-mining",
            Kind.PORTABLE,
            new InventorySlot(0, null, 64, id("ore"), 100, true));

        Plan plan = planner
            .plan(Arrays.asList(general, player, mining), Collections.emptyList(), ignored -> false, ignored -> true);

        assertEquals(
            2,
            plan.getMoves()
                .size());
        assertMove(
            plan.getMoves()
                .get(0),
            "player",
            0,
            "z-mining",
            0,
            64);
        assertMove(
            plan.getMoves()
                .get(1),
            "player",
            1,
            "a-general",
            0,
            32);
    }

    @Test
    public void mergesOnlyExactNbtIdentityAndThenUsesEmptySlots() {
        InventoryEndpoint player = endpoint("player", Kind.PLAYER, slot(0, item("ore", "wanted", 20)));
        InventoryEndpoint bag = endpoint(
            "bag",
            Kind.PORTABLE,
            slot(0, item("ore", "other", 1)),
            slot(1, null),
            slot(2, item("ore", "wanted", 60)));

        Plan plan = planner
            .plan(Arrays.asList(player, bag), Collections.emptyList(), ignored -> false, ignored -> true);

        assertEquals(
            2,
            plan.getMoves()
                .size());
        assertMove(
            plan.getMoves()
                .get(0),
            "player",
            0,
            "bag",
            2,
            4);
        assertMove(
            plan.getMoves()
                .get(1),
            "player",
            0,
            "bag",
            1,
            16);
        assertEquals(
            16,
            plan.getMoves()
                .get(1)
                .getExpectedSource()
                .getCount());
    }

    @Test
    public void unavailableAndUnreadableEndpointsDoNotProvideItemsOrCapacity() {
        InventoryEndpoint player = endpoint("player", Kind.PLAYER, slot(0, item("ore", "o", 32)), slot(1, null));
        InventoryEndpoint offlineNetwork = new InventoryEndpoint(
            "network",
            Kind.NETWORK,
            false,
            true,
            true,
            null,
            Arrays.asList(slot(0, item("pick", "p", 1)), slot(1, null)));
        InventoryEndpoint unreadableBag = new InventoryEndpoint(
            "bag",
            Kind.PORTABLE,
            true,
            false,
            true,
            null,
            Arrays.asList(slot(0, item("pick", "p", 1)), slot(1, null)));
        List<InventoryEndpoint> endpoints = Arrays.asList(player, offlineNetwork, unreadableBag);

        Plan plan = planner.plan(endpoints, required("pick", 1), ignored -> false, ignored -> true);

        assertFalse(plan.isReadyForTask());
        assertTrue(
            plan.getMoves()
                .isEmpty());
        assertEquals(
            1,
            plan.getMissingRequirements()
                .get(0)
                .getMissingCount());
        assertEquals(0, planner.availableCount(endpoints, id("pick")));
        assertEquals(0, planner.availableCapacity(endpoints, item("ore", "o", 1), false));
    }

    @Test
    public void insertDeniedAndFilterRejectingSlotsDoNotProvideCapacity() {
        InventoryEndpoint player = endpoint("player", Kind.PLAYER, slot(0, item("ore", "o", 32)));
        InventoryEndpoint readOnly = new InventoryEndpoint(
            "readonly",
            Kind.NETWORK,
            true,
            true,
            false,
            null,
            Collections.singletonList(slot(0, null)));
        InventoryEndpoint forest = endpoint(
            "forest",
            Kind.PORTABLE,
            new InventorySlot(0, null, 64, id("wood"), 50, true));
        List<InventoryEndpoint> endpoints = Arrays.asList(player, readOnly, forest);

        Plan plan = planner.plan(endpoints, Collections.emptyList(), ignored -> false, ignored -> true);

        assertTrue(
            plan.getMoves()
                .isEmpty());
        assertEquals(0, planner.availableCapacity(endpoints, item("ore", "o", 1), false));
    }

    @Test
    public void readableButReadOnlyNetworkDoesNotOfferRetrievableItems() {
        InventoryEndpoint player = endpoint("player", Kind.PLAYER, slot(0, null));
        InventoryEndpoint network = new InventoryEndpoint(
            "network",
            Kind.NETWORK,
            true,
            true,
            false,
            null,
            Collections.singletonList(slot(0, item("pick", "p", 1))));
        List<InventoryEndpoint> endpoints = Arrays.asList(player, network);

        Plan plan = planner.plan(endpoints, required("pick", 1), ignored -> false, ignored -> true);

        assertFalse(plan.isReadyForTask());
        assertTrue(
            plan.getMoves()
                .isEmpty());
        assertEquals(0, planner.availableCount(endpoints, id("pick")));
    }

    @Test
    public void extractionPermissionAndFullPlayerAreReportedAsUnmetTaskNeeds() {
        InventoryEndpoint player = endpoint("player", Kind.PLAYER, slot(0, item("food", "f", 64)));
        InventoryEndpoint bag = endpoint("bag", Kind.PORTABLE, slot(0, item("pick", "p", 1)));
        InventoryEndpoint locked = endpoint(
            "locked",
            Kind.PORTABLE,
            new InventorySlot(0, item("axe", "a", 1), 1, ignored -> true, 0, false));
        List<LoadoutReservation> requirements = new ArrayList<>(required("pick", 1));
        requirements.addAll(required("axe", 1));

        Plan plan = planner.plan(Arrays.asList(player, bag, locked), requirements, ignored -> true, ignored -> true);

        assertFalse(plan.isReadyForTask());
        assertEquals(
            2,
            plan.getMissingRequirements()
                .size());
        assertTrue(
            plan.getMoves()
                .isEmpty());
        assertEquals(0, planner.availableCount(Arrays.asList(player, bag, locked), id("axe")));
    }

    @Test
    public void itemSpecificLimitsKeepUnstackableToolsInSeparatePlayerSlots() {
        InventoryEndpoint player = endpoint(
            "player",
            Kind.PLAYER,
            new InventorySlot(
                0,
                null,
                value -> value.getItemId()
                    .equals("pick") ? 1 : 64,
                ignored -> true,
                0,
                true),
            new InventorySlot(
                1,
                null,
                value -> value.getItemId()
                    .equals("pick") ? 1 : 64,
                ignored -> true,
                0,
                true));
        InventoryEndpoint bag = endpoint(
            "bag",
            Kind.PORTABLE,
            slot(0, item("pick", "same", 1)),
            slot(1, item("pick", "same", 1)));

        Plan plan = planner.plan(Arrays.asList(player, bag), required("pick", 2), ignored -> false, ignored -> false);

        assertTrue(plan.isReadyForTask());
        assertEquals(
            2,
            plan.getMoves()
                .size());
        assertMove(
            plan.getMoves()
                .get(0),
            "bag",
            0,
            "player",
            0,
            1);
        assertMove(
            plan.getMoves()
                .get(1),
            "bag",
            1,
            "player",
            1,
            1);
    }

    @Test
    public void onlyConnectedNetworkCanRetrieveAndAlwaysTargetsPlayer() {
        InventoryEndpoint network = endpoint("terminal", Kind.NETWORK, slot(0, item("pick", "p", 1)));
        Plan plan = planner.plan(
            Arrays.asList(network, endpoint("player", Kind.PLAYER, slot(0, null))),
            required("pick", 1),
            ignored -> false,
            ignored -> false);

        assertTrue(plan.isReadyForTask());
        assertEquals(
            1,
            plan.getMoves()
                .size());
        assertMove(
            plan.getMoves()
                .get(0),
            "terminal",
            0,
            "player",
            0,
            1);
    }

    @Test
    public void unavailableBagCarrierCannotBeStowedInAnotherBag() {
        InventoryEndpoint player = endpoint("player", Kind.PLAYER, slot(0, item("bag", "b", 1)));
        InventoryEndpoint closed = new InventoryEndpoint(
            "closed",
            Kind.PORTABLE,
            false,
            false,
            false,
            new InventoryLocation("player", 0),
            Collections.emptyList());
        Plan plan = planner.plan(
            Arrays.asList(player, closed, endpoint("other", Kind.PORTABLE, slot(0, null))),
            Collections.emptyList(),
            ignored -> false,
            ignored -> true);

        assertTrue(
            plan.getMoves()
                .isEmpty());
    }

    @Test
    public void destinationOrderDoesNotDependOnAdapterDiscoveryOrder() {
        InventoryEndpoint player = endpoint("player", Kind.PLAYER, slot(0, item("ore", "o", 1)));
        InventoryEndpoint a = endpoint("a", Kind.PORTABLE, slot(4, null), slot(2, null));
        InventoryEndpoint b = endpoint("b", Kind.PORTABLE, slot(0, null));
        Plan first = planner
            .plan(Arrays.asList(player, a, b), Collections.emptyList(), ignored -> false, ignored -> true);
        Plan second = planner
            .plan(Arrays.asList(b, a, player), Collections.emptyList(), ignored -> false, ignored -> true);

        assertMove(
            first.getMoves()
                .get(0),
            "player",
            0,
            "a",
            2,
            1);
        assertEquals(
            first.getMoves()
                .get(0)
                .getDestination(),
            second.getMoves()
                .get(0)
                .getDestination());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateEndpointIdsCannotAliasInventoryLocations() {
        planner.plan(
            Arrays.asList(endpoint("bag", Kind.PORTABLE, slot(0, null)), endpoint("bag", Kind.PORTABLE, slot(1, null))),
            Collections.emptyList(),
            ignored -> false,
            ignored -> true);
    }

    private static List<LoadoutReservation> required(String id, int count) {
        return Collections
            .singletonList(new LoadoutReservation("required-" + id, LoadoutRole.TOOL, id, -1, null, count));
    }

    private static Predicate<ItemFingerprint> id(String id) {
        return value -> id.equals(value.getItemId());
    }

    private static ItemFingerprint item(String id, String nbt, int count) {
        return new ItemFingerprint(id, 0, nbt, count);
    }

    private static InventorySlot slot(int index, ItemFingerprint item) {
        return new InventorySlot(index, item, 64, ignored -> true, 0, true);
    }

    private static InventoryEndpoint endpoint(String id, Kind kind, InventorySlot... slots) {
        return new InventoryEndpoint(id, kind, true, true, true, null, Arrays.asList(slots));
    }

    private static void assertMove(Move move, String source, int sourceSlot, String destination, int destinationSlot,
        int count) {
        assertEquals(new InventoryLocation(source, sourceSlot), move.getSource());
        assertEquals(new InventoryLocation(destination, destinationSlot), move.getDestination());
        assertEquals(count, move.getCount());
    }
}
