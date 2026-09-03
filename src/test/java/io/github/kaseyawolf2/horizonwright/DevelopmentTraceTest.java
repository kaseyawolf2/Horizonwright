package io.github.kaseyawolf2.horizonwright;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class DevelopmentTraceTest {

    @After
    public void restoreDefault() {
        DevelopmentTrace.setEnabled(true);
    }

    @Test
    public void developmentTracingCanBeChangedAtRuntime() {
        DevelopmentTrace.setEnabled(false);
        assertFalse(DevelopmentTrace.isEnabled());
        DevelopmentTrace.setEnabled(true);
        assertTrue(DevelopmentTrace.isEnabled());
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedFieldPairsAreRejectedWhileEnabled() {
        DevelopmentTrace.setEnabled(true);
        DevelopmentTrace.event("test", "bad", "unpaired");
    }
}
