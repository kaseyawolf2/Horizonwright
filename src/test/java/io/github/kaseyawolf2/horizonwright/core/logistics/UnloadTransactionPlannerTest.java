package io.github.kaseyawolf2.horizonwright.core.logistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

public class UnloadTransactionPlannerTest {

    private static final ItemFingerprint ORE = item("gregtech:gt.blockores", 32, "ore", 16);
    private static final ItemFingerprint DIRT = item("minecraft:dirt", 0, "none", 32);
    private static final ItemFingerprint PICK = item("TConstruct:pickaxe", 0, "pick", 1);

    @Test
    public void exactQuickMovePredictionsBecomeAOneAtATimeVerifiedTransaction() {
        NamedLoadout empty = new NamedLoadout("empty", "Empty", Collections.<LoadoutReservation>emptyList());
        UnloadPlan plan = UnloadPlanner.plan(empty, Arrays.asList(ORE, DIRT));
        ContainerSnapshot before = snapshot(10L, ORE, DIRT, null, null);
        ContainerSnapshot afterOre = snapshot(11L, null, DIRT, ORE, null);
        ContainerSnapshot afterDirt = snapshot(12L, null, null, ORE, DIRT);
        UnloadClickPrediction ore = prediction(0, "ore", 0, before, afterOre);
        UnloadClickPrediction dirt = prediction(1, "dirt", 1, afterOre, afterDirt);

        ContainerTransaction transaction = UnloadTransactionPlanner
            .create("unload-1", 41L, plan, Arrays.asList(ORE, DIRT), Arrays.asList(ore, dirt));

        assertEquals(
            "ore",
            transaction.nextClick(before, 41L)
                .get()
                .getClickId());
        assertTrue(transaction.confirm("ore", true, afterOre, 41L));
        assertEquals(
            "dirt",
            transaction.nextClick(afterOre, 41L)
                .get()
                .getClickId());
        assertTrue(transaction.confirm("dirt", true, afterDirt, 41L));
        assertEquals(ContainerTransactionState.COMPLETED, transaction.getState());
    }

    @Test
    public void reservedSlotPredictionIsRejectedEvenWhenTheAdapterCallsItQuickMove() {
        NamedLoadout loadout = new NamedLoadout(
            "work",
            "Work",
            Collections
                .singletonList(new LoadoutReservation("pick", LoadoutRole.TOOL, "TConstruct:pickaxe", 0, null, 1)));
        UnloadPlan plan = UnloadPlanner.plan(loadout, Arrays.asList(PICK, ORE));
        ContainerSnapshot before = snapshot(10L, PICK, ORE, null, null);
        ContainerSnapshot after = snapshot(11L, null, ORE, PICK, null);
        UnloadClickPrediction reserved = prediction(0, "pick", 0, before, after);

        assertThrows(
            IllegalArgumentException.class,
            () -> UnloadTransactionPlanner
                .create("unsafe", 41L, plan, Arrays.asList(PICK, ORE), Collections.singletonList(reserved)));
    }

    @Test
    public void predictionMustReduceTheExactApprovedSourceAndConserveItems() {
        NamedLoadout empty = new NamedLoadout("empty", "Empty", Collections.<LoadoutReservation>emptyList());
        UnloadPlan plan = UnloadPlanner.plan(empty, Collections.singletonList(ORE));
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot unchanged = snapshot(11L, ORE, null);
        UnloadClickPrediction bad = prediction(0, "unchanged", 0, before, unchanged);

        assertThrows(
            IllegalArgumentException.class,
            () -> UnloadTransactionPlanner
                .create("bad", 41L, plan, Collections.singletonList(ORE), Collections.singletonList(bad)));

        ContainerSnapshot deleted = snapshot(11L, null, null);
        UnloadClickPrediction disappearing = prediction(0, "deleted", 0, before, deleted);
        assertThrows(
            IllegalArgumentException.class,
            () -> UnloadTransactionPlanner
                .create("deleted", 41L, plan, Collections.singletonList(ORE), Collections.singletonList(disappearing)));
    }

    private static UnloadClickPrediction prediction(int playerSlot, String id, int windowSlot, ContainerSnapshot before,
        ContainerSnapshot after) {
        return new UnloadClickPrediction(playerSlot, new VerifiedContainerClick(id, windowSlot, 0, 1, before, after));
    }

    private static ContainerSnapshot snapshot(long revision, ItemFingerprint... slots) {
        return new ContainerSnapshot(7, "minecraft:chest", "player-2+chest-2", revision, Arrays.asList(slots), null);
    }

    private static ItemFingerprint item(String id, int metadata, String hash, int count) {
        return new ItemFingerprint(id, metadata, hash, count);
    }
}
