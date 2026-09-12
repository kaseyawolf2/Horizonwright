package io.github.kaseyawolf2.horizonwright.forge.client.sleep;

import static org.junit.Assert.*;

import org.junit.Test;

public class SleepCompletionPolicyTest {

    @Test
    public void excavationCannotResumeWhileSleepingOrBeforeMorning() {
        assertFalse(SleepCompletionPolicy.mayResumeWork(false, true));
        assertFalse(SleepCompletionPolicy.mayResumeWork(true, true));
        assertFalse(SleepCompletionPolicy.mayResumeWork(false, false));
        assertTrue(SleepCompletionPolicy.mayResumeWork(true, false));
    }
}
