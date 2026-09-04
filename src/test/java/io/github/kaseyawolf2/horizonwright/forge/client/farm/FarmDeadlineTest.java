package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.forge.client.farm.LiveVanillaFarmBackend.FarmDeadline;

public class FarmDeadlineTest {

    @Test
    public void approachTravelDoesNotConsumeTheInteractionBudget() {
        FarmDeadline deadline = new FarmDeadline(0L);

        assertFalse(deadline.approachExpired(TimeUnit.SECONDS.toNanos(31L)));
        deadline.beginAction(TimeUnit.MINUTES.toNanos(4L));
        assertFalse(deadline.actionExpired(TimeUnit.MINUTES.toNanos(4L) + TimeUnit.SECONDS.toNanos(29L)));
        assertTrue(deadline.actionExpired(TimeUnit.MINUTES.toNanos(4L) + TimeUnit.SECONDS.toNanos(30L)));
    }

    @Test
    public void actionBudgetCanOnlyBeginOnce() {
        FarmDeadline deadline = new FarmDeadline(0L);
        deadline.beginAction(TimeUnit.SECONDS.toNanos(10L));
        deadline.beginAction(TimeUnit.SECONDS.toNanos(20L));

        assertTrue(deadline.actionExpired(TimeUnit.SECONDS.toNanos(40L)));
    }
}
