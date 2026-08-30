package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BaritoneNavigationCleanupRetryTest {

    @Test
    public void cleanupFailureBudgetIsBoundedAndRemainsExhausted() {
        BaritoneNavigationBackend.CleanupRetryBudget budget = new BaritoneNavigationBackend.CleanupRetryBudget(
            BaritoneNavigationBackend.MAX_CLEANUP_ATTEMPTS);

        assertFalse(budget.recordFailureAndIsExhausted());
        assertFalse(budget.recordFailureAndIsExhausted());
        assertTrue(budget.recordFailureAndIsExhausted());
        assertTrue(budget.recordFailureAndIsExhausted());
        assertEquals(BaritoneNavigationBackend.MAX_CLEANUP_ATTEMPTS, budget.getFailureCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cleanupFailureBudgetRejectsAnUnboundedZeroAttemptConfiguration() {
        new BaritoneNavigationBackend.CleanupRetryBudget(0);
    }
}
