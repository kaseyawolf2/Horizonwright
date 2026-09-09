package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.*;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerActionPacing;

public class InventoryPreparationStartTest {

    @Test
    public void dashboardCanRemainOpenWithoutStartingPreparationAndClosingItAllowsProgress() {
        ContainerActionPacing pacing = new ContainerActionPacing(5);
        // Longer than the active transfer timeout: no active session should be needed while waiting.
        for (int tick = 0; tick < 4000; tick++) {
            assertNotNull(waitReason(true, true, true, pacing));
            pacing.tick();
        }
        for (int tick = 1; tick < 5; tick++) {
            assertNotNull(waitReason(false, true, true, pacing));
            pacing.tick();
        }
        assertNull(waitReason(false, true, true, pacing));
    }

    @Test
    public void hiddenForeignContainerAndCarriedCursorItemStillPreventStarting() {
        ContainerActionPacing pacing = new ContainerActionPacing(5);
        assertNotNull(waitReason(false, false, true, pacing));
        for (int tick = 0; tick < 5; tick++) pacing.tick();
        assertNotNull(waitReason(false, true, false, pacing));
        for (int tick = 1; tick < 5; tick++) {
            pacing.tick();
            assertNotNull(waitReason(false, true, true, pacing));
        }
        pacing.tick();
        assertNull(waitReason(false, true, true, pacing));
    }

    @Test
    public void reopeningScreenRestartsDelayAndClearInitialStateCanStart() {
        ContainerActionPacing pacing = new ContainerActionPacing(5);
        assertNull(waitReason(false, true, true, pacing));
        assertNotNull(waitReason(true, true, true, pacing));
        for (int tick = 0; tick < 4; tick++) pacing.tick();
        assertNotNull(waitReason(true, true, true, pacing));
        for (int tick = 1; tick < 5; tick++) {
            pacing.tick();
            assertNotNull(waitReason(false, true, true, pacing));
        }
        pacing.tick();
        assertNull(waitReason(false, true, true, pacing));
    }

    private static String waitReason(boolean screenOpen, boolean playerInventory, boolean cursorEmpty,
        ContainerActionPacing pacing) {
        return LiveExtendedInventoryService.preparationStartWait(screenOpen, playerInventory, cursorEmpty, pacing);
    }
}
