package io.github.kaseyawolf2.horizonwright.core.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ContainerClickCorrelationTest {

    private static final ItemFingerprint ORE = new ItemFingerprint("gregtech:ore", 4, "ore-data", 16);

    @Test
    public void requiresExactWriteAcceptedConfirmationAndSynchronizedSnapshot() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        ContainerClickCorrelation correlation = correlation(before, after);

        assertTrue(
            correlation.prepare(before, 41L, 100L, 50L)
                .isPresent());
        assertEquals(
            ContainerClickCorrelation.WriteObservation.MATCHED,
            correlation.observeWrite(7, 0, 0, 1, (short) 23, 101L));
        assertEquals(
            ContainerClickCorrelation.ConfirmationObservation.NOT_APPLICABLE,
            correlation.observeConfirmation(7, (short) 22, true, 102L));
        assertEquals(
            ContainerClickCorrelation.ConfirmationObservation.ACCEPTED,
            correlation.observeConfirmation(7, (short) 23, true, 103L));
        assertFalse(correlation.observeSynchronizedSnapshot(before, 41L, 104L));
        assertTrue(correlation.observeSynchronizedSnapshot(after, 41L, 105L));
        assertEquals(ContainerClickCorrelation.State.COMPLETED, correlation.getState());
    }

    @Test
    public void rejectionIsTerminalAndCannotExposeTheClickAgain() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        ContainerClickCorrelation correlation = correlation(before, after);

        correlation.prepare(before, 41L, 100L, 50L);
        correlation.observeWrite(7, 0, 0, 1, (short) 23, 101L);
        assertEquals(
            ContainerClickCorrelation.ConfirmationObservation.REJECTED,
            correlation.observeConfirmation(7, (short) 23, false, 102L));

        assertEquals(ContainerClickCorrelation.State.ABORTED, correlation.getState());
        assertFalse(
            correlation.prepare(before, 41L, 103L, 50L)
                .isPresent());
    }

    @Test
    public void timeoutIsTerminalAndExplicitlyForbidsResend() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        ContainerClickCorrelation correlation = correlation(before, after);

        correlation.prepare(before, 41L, 100L, 50L);
        correlation.observeWrite(7, 0, 0, 1, (short) 23, 101L);
        assertTrue(correlation.expire(150L));

        assertEquals(ContainerClickCorrelation.State.ABORTED, correlation.getState());
        assertTrue(
            correlation.getTransaction()
                .getAbortReason()
                .contains("not be resent"));
        assertFalse(
            correlation.prepare(before, 41L, 151L, 50L)
                .isPresent());
    }

    @Test
    public void staleConfirmationsAreIgnoredButPreparedWriteMismatchAborts() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        ContainerClickCorrelation stale = correlation(before, after);
        stale.prepare(before, 41L, 100L, 50L);
        stale.observeWrite(7, 0, 0, 1, (short) 23, 101L);

        assertEquals(
            ContainerClickCorrelation.ConfirmationObservation.NOT_APPLICABLE,
            stale.observeConfirmation(9, (short) 23, false, 102L));
        assertEquals(ContainerClickCorrelation.State.AWAITING_CONFIRMATION, stale.getState());

        ContainerClickCorrelation mismatch = correlation(before, after);
        mismatch.prepare(before, 41L, 100L, 50L);
        assertEquals(
            ContainerClickCorrelation.WriteObservation.ACTIVE_MISMATCH,
            mismatch.observeWrite(7, 1, 0, 1, (short) 24, 101L));
        assertEquals(ContainerClickCorrelation.State.ABORTED, mismatch.getState());
    }

    @Test
    public void additionalUnpreparedClickPassesAsUnrelatedButAbortsAutomation() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        ContainerClickCorrelation correlation = correlation(before, after);
        correlation.prepare(before, 41L, 100L, 50L);
        correlation.observeWrite(7, 0, 0, 1, (short) 23, 101L);

        assertEquals(
            ContainerClickCorrelation.WriteObservation.NOT_APPLICABLE,
            correlation.observeWrite(7, 1, 0, 0, (short) 24, 102L));
        assertEquals(ContainerClickCorrelation.State.ABORTED, correlation.getState());
    }

    private static ContainerClickCorrelation correlation(ContainerSnapshot before, ContainerSnapshot after) {
        VerifiedContainerClick click = new VerifiedContainerClick("quick-move", 0, 0, 1, before, after);
        return new ContainerClickCorrelation(new ContainerTransaction("unload", 41L, Collections.singletonList(click)));
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
