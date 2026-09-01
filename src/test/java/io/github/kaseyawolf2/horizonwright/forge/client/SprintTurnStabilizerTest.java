package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SprintTurnStabilizerTest {

    @Test
    public void bridgesOnlyATransientLossAfterBaritoneEstablishedSprint() {
        SprintTurnStabilizer stabilizer = new SprintTurnStabilizer();

        assertFalse(stabilizer.shouldRestore(true, true, true, false));
        assertFalse(stabilizer.shouldRestore(true, true, true, true));
        assertTrue(stabilizer.shouldRestore(true, true, true, false));
        assertTrue(stabilizer.shouldRestore(true, true, true, false));
        assertTrue(stabilizer.shouldRestore(true, true, true, false));
        assertTrue(stabilizer.shouldRestore(true, true, true, false));
        assertFalse(stabilizer.shouldRestore(true, true, true, false));
    }

    @Test
    public void navigationEndOrIneligibilityClearsTheGraceWindow() {
        SprintTurnStabilizer stabilizer = new SprintTurnStabilizer();
        stabilizer.shouldRestore(true, true, true, true);

        assertFalse(stabilizer.shouldRestore(false, true, true, false));
        assertFalse(stabilizer.shouldRestore(true, true, true, false));
        stabilizer.shouldRestore(true, true, true, true);
        assertFalse(stabilizer.shouldRestore(true, false, true, false));
        assertFalse(stabilizer.shouldRestore(true, true, true, false));
    }
}
