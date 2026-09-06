package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TreePlantingPatternTest {

    @Test
    public void onlyMissingSingleSaplingsAreQueued() {
        assertTrue(TreePlantingPattern.needsFill(1, 1, 0));
        assertFalse(TreePlantingPattern.needsFill(1, 0, 1));
    }

    @Test
    public void incompleteTwoByTwoIsFilledWithoutReplacingExistingSaplings() {
        for (int empty = 1; empty <= 4; empty++) assertTrue(TreePlantingPattern.needsFill(4, empty, 4 - empty));
        assertFalse(TreePlantingPattern.needsFill(4, 0, 4));
        assertFalse(TreePlantingPattern.needsFill(4, 2, 1));
    }
}
