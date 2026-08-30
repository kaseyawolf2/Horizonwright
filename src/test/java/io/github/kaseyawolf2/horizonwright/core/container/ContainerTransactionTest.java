package io.github.kaseyawolf2.horizonwright.core.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ContainerTransactionTest {

    private static final ItemFingerprint ORE = new ItemFingerprint("gregtech:gt.blockores", 32, "ore", 16);
    private static final ItemFingerprint TOOL = new ItemFingerprint("tconstruct:pickaxe", 0, "tool", 1);

    @Test
    public void exposesExactlyOneClickUntilExactServerConfirmation() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot middle = snapshot(11L, null, ORE);
        ContainerSnapshot after = snapshot(12L, TOOL, ORE);
        ContainerTransaction transaction = transaction(before, middle, after);

        assertEquals(
            "take",
            transaction.nextClick(before, 41L)
                .get()
                .getClickId());
        assertFalse(
            transaction.nextClick(before, 41L)
                .isPresent());
        assertTrue(transaction.confirm("take", true, middle, 41L));
        assertEquals(
            "place",
            transaction.nextClick(middle, 41L)
                .get()
                .getClickId());
        assertTrue(transaction.confirm("place", true, after, 41L));
        assertEquals(ContainerTransactionState.COMPLETED, transaction.getState());
        assertEquals(2, transaction.getCompletedClickCount());
    }

    @Test
    public void staleEpochAbortsBeforeAnyClickCanEscape() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerTransaction transaction = oneClick(before, snapshot(11L, null, ORE));

        assertFalse(
            transaction.nextClick(before, 42L)
                .isPresent());
        assertEquals(ContainerTransactionState.ABORTED, transaction.getState());
        assertTrue(
            transaction.getAbortReason()
                .contains("epoch"));
    }

    @Test
    public void changedContainerAbortsBeforeClick() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerTransaction transaction = oneClick(before, snapshot(11L, null, ORE));

        assertFalse(
            transaction.nextClick(snapshot(10L, TOOL, null), 41L)
                .isPresent());
        assertEquals(ContainerTransactionState.ABORTED, transaction.getState());
    }

    @Test
    public void rejectionUnexpectedAfterSnapshotAndWrongConfirmationAllAbort() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);

        ContainerTransaction rejected = oneClick(before, after);
        rejected.nextClick(before, 41L);
        assertFalse(rejected.confirm("take", false, before, 41L));

        ContainerTransaction changed = oneClick(before, after);
        changed.nextClick(before, 41L);
        assertFalse(changed.confirm("take", true, snapshot(11L, TOOL, null), 41L));

        ContainerTransaction wrongId = oneClick(before, after);
        wrongId.nextClick(before, 41L);
        assertFalse(wrongId.confirm("duplicate", true, after, 41L));

        assertEquals(ContainerTransactionState.ABORTED, rejected.getState());
        assertEquals(ContainerTransactionState.ABORTED, changed.getState());
        assertEquals(ContainerTransactionState.ABORTED, wrongId.getState());
    }

    @Test
    public void requiresAnExactContinuousPlan() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot middle = snapshot(11L, null, ORE);
        ContainerSnapshot different = snapshot(11L, TOOL, null);
        ContainerSnapshot after = snapshot(12L, TOOL, ORE);

        assertThrows(
            IllegalArgumentException.class,
            () -> new ContainerTransaction(
                "broken",
                41L,
                Arrays.asList(click("take", before, middle), click("place", different, after))));
    }

    private static ContainerTransaction transaction(ContainerSnapshot before, ContainerSnapshot middle,
        ContainerSnapshot after) {
        return new ContainerTransaction(
            "unload",
            41L,
            Arrays.asList(click("take", before, middle), click("place", middle, after)));
    }

    private static ContainerTransaction oneClick(ContainerSnapshot before, ContainerSnapshot after) {
        return new ContainerTransaction("unload", 41L, Collections.singletonList(click("take", before, after)));
    }

    private static VerifiedContainerClick click(String id, ContainerSnapshot before, ContainerSnapshot after) {
        return new VerifiedContainerClick(id, 0, 0, 0, before, after);
    }

    private static ContainerSnapshot snapshot(long revision, ItemFingerprint first, ItemFingerprint second) {
        return new ContainerSnapshot(
            7,
            "minecraft:chest",
            "chest-2+player-2",
            revision,
            Arrays.asList(first, second),
            null);
    }
}
