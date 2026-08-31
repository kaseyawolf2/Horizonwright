package io.github.kaseyawolf2.horizonwright.core.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ContainerTransactionFingerprintTest {

    private static final ItemFingerprint ITEM = new ItemFingerprint("gregtech:ore", 4, "nbt", 16);

    @Test
    public void digestIsStableAndCoversEpochParametersAndEverySnapshotField() {
        ContainerTransaction original = transaction(41L, 0, snapshot(10L, ITEM, null), snapshot(11L, null, ITEM));
        ContainerTransaction same = transaction(41L, 0, snapshot(10L, ITEM, null), snapshot(11L, null, ITEM));
        ContainerTransaction changedEpoch = transaction(42L, 0, snapshot(10L, ITEM, null), snapshot(11L, null, ITEM));
        ContainerTransaction changedSlot = transaction(41L, 1, snapshot(10L, ITEM, null), snapshot(11L, null, ITEM));

        assertEquals(
            ContainerTransactionFingerprint.fingerprint(original),
            ContainerTransactionFingerprint.fingerprint(same));
        assertNotEquals(
            ContainerTransactionFingerprint.fingerprint(original),
            ContainerTransactionFingerprint.fingerprint(changedEpoch));
        assertNotEquals(
            ContainerTransactionFingerprint.fingerprint(original),
            ContainerTransactionFingerprint.fingerprint(changedSlot));
    }

    private static ContainerTransaction transaction(long epoch, int slot, ContainerSnapshot before,
        ContainerSnapshot after) {
        return new ContainerTransaction(
            "unload",
            epoch,
            Collections.singletonList(new VerifiedContainerClick("move", slot, 0, 1, before, after)));
    }

    private static ContainerSnapshot snapshot(long revision, ItemFingerprint... items) {
        return new ContainerSnapshot(7, "chest", "layout", revision, Arrays.asList(items), null);
    }
}
