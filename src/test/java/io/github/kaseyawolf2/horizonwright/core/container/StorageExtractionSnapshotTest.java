package io.github.kaseyawolf2.horizonwright.core.container;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class StorageExtractionSnapshotTest {

    private static final ItemFingerprint ORE = item("ore", 64);
    private static final ItemFingerprint TOOL = item("tool", 1);

    @Test
    public void extractionMayEmptyAndRepackStorageButMustPreservePlayerEvidence() {
        VerifiedContainerClick click = click();
        assertTrue(click.matchesAfter(snapshot(1, null, null, null, TOOL)));
        assertTrue(click.matchesAfter(snapshot(1, null, item("ore", 46), null, TOOL)));
        assertFalse(click.matchesAfter(snapshot(1, null, null, ORE, TOOL)));
        assertFalse(click.matchesAfter(snapshot(1, null, null, null, null)));
        assertFalse(click.matchesAfter(snapshot(1, item("ore", 65), null, null, TOOL)));
        assertFalse(click.matchesAfter(snapshot(1, item("other", 1), null, null, TOOL)));
        assertFalse(click.matchesAfter(snapshot(1, new ItemFingerprint("ore", 0, "changed-nbt", 1), null, null, TOOL)));
    }

    @Test
    public void identityCursorAndRevisionRemainRequired() {
        VerifiedContainerClick click = click();
        ContainerSnapshot empty = snapshot(1, null, null, null, TOOL);
        assertFalse(click.matchesAfter(new ContainerSnapshot(8, "chest", "storage+player", 1, empty.getSlots(), null)));
        assertFalse(click.matchesAfter(new ContainerSnapshot(7, "chest", "storage+player", 1, empty.getSlots(), ORE)));
        assertFalse(click.matchesAfter(snapshot(2, null, null, null, TOOL)));
    }

    @Test
    public void extractionBeforeDispatchAndExtraFreedCapacityAreAllowed() {
        ContainerSnapshot before = snapshot(0, item("ore", 60), TOOL, item("ore", 10), TOOL);
        ContainerSnapshot after = snapshot(1, ORE, TOOL, item("ore", 6), TOOL);
        VerifiedContainerClick click = new VerifiedContainerClick("partial", 2, 0, 1, before, after)
            .allowingStorageExtraction(2);
        assertTrue(click.matchesBefore(snapshot(0, null, TOOL, item("ore", 10), TOOL)));
        assertFalse(click.matchesBefore(snapshot(0, null, TOOL, item("ore", 9), TOOL)));
        assertTrue(click.matchesAfter(snapshot(1, item("ore", 10), TOOL, null, TOOL)));
        assertTrue(click.matchesAfter(snapshot(1, null, TOOL, null, TOOL)));
        assertFalse(click.matchesAfter(snapshot(1, null, TOOL, item("ore", 7), TOOL)));
    }

    @Test
    public void ordinaryTransactionsRemainExactAndExtractionCannotChainStaleClicks() {
        VerifiedContainerClick aware = click();
        VerifiedContainerClick exact = new VerifiedContainerClick(
            "exact",
            2,
            0,
            1,
            aware.getExpectedBefore(),
            aware.getExpectedAfter());
        assertFalse(exact.matchesAfter(snapshot(1, null, null, null, TOOL)));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ContainerTransaction("bad", 1, Arrays.asList(aware, exact)));
        ContainerTransaction transaction = new ContainerTransaction("unload", 1, Collections.singletonList(aware));
        assertTrue(
            transaction.nextClick(aware.getExpectedBefore(), 1)
                .isPresent());
        assertTrue(transaction.confirm(aware.getClickId(), true, snapshot(1, null, null, null, TOOL), 1));
        assertEquals(ContainerTransactionState.COMPLETED, transaction.getState());
        assertFalse(
            transaction.nextClick(aware.getExpectedBefore(), 1)
                .isPresent());
    }

    @Test
    public void verificationPolicyIsIncludedInPersistedFingerprint() {
        VerifiedContainerClick aware = click();
        VerifiedContainerClick exact = new VerifiedContainerClick(
            aware.getClickId(),
            2,
            0,
            1,
            aware.getExpectedBefore(),
            aware.getExpectedAfter());
        assertNotEquals(
            ContainerTransactionFingerprint
                .fingerprint(new ContainerTransaction("unload", 1, Collections.singletonList(aware))),
            ContainerTransactionFingerprint
                .fingerprint(new ContainerTransaction("unload", 1, Collections.singletonList(exact))));
    }

    @Test
    public void pickupsInOtherPlayerSlotsAreAllowedBeforeAndAfterTransfer() {
        VerifiedContainerClick click = new VerifiedContainerClick(
            "unload",
            2,
            0,
            1,
            snapshot(0, null, null, ORE, null, item("sand", 12)),
            snapshot(1, ORE, null, null, null, item("sand", 12))).allowingStorageExtraction(2);
        ItemFingerprint ink = item("minecraft:dye", 1);
        assertTrue(click.matchesBefore(snapshot(0, null, null, ORE, ink, item("sand", 14))));
        assertTrue(click.matchesAfter(snapshot(1, null, null, null, ink, item("sand", 14))));
        assertFalse(click.matchesBefore(snapshot(0, null, null, item("ore", 63), ink, item("sand", 14))));
        assertFalse(click.matchesAfter(snapshot(1, null, null, ORE, ink, item("sand", 14))));
        assertFalse(click.matchesAfter(snapshot(1, null, null, null, ink, item("sand", 11))));
        assertFalse(click.matchesAfter(snapshot(1, null, null, null, ink, item("gravel", 12))));
        assertFalse(
            click.matchesAfter(snapshot(1, null, null, null, ink, new ItemFingerprint("sand", 0, "changed", 12))));
    }

    private static VerifiedContainerClick click() {
        return new VerifiedContainerClick(
            "ore",
            2,
            0,
            1,
            snapshot(0, null, null, ORE, TOOL),
            snapshot(1, ORE, null, null, TOOL)).allowingStorageExtraction(2);
    }

    private static ContainerSnapshot snapshot(long revision, ItemFingerprint... slots) {
        return new ContainerSnapshot(7, "chest", "storage+player", revision, Arrays.asList(slots), null);
    }

    private static ItemFingerprint item(String id, int count) {
        return new ItemFingerprint(id, 0, "none", count);
    }
}
