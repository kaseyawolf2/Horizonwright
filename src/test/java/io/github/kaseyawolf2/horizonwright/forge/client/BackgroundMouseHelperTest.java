package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.*;

import net.minecraft.util.MouseHelper;

import org.junit.Test;

public class BackgroundMouseHelperTest {

    @Test
    public void automatedGuiReleaseNeverCallsCursorWarpEvenInForeground() {
        FakeMouse original = new FakeMouse();
        int[] released = { 0 };
        BackgroundMouseHelper helper = new BackgroundMouseHelper(original, () -> true, () -> released[0]++);
        helper.ungrabMouseCursor();
        assertEquals(1, released[0]);
        assertEquals(0, original.warps);
        assertSame(original, helper.original());
    }

    @Test
    public void backgroundOpenCloseCycleCannotGrabWarpOrReadDesktopMovement() {
        FakeMouse original = new FakeMouse();
        BackgroundMouseHelper helper = new BackgroundMouseHelper(original, () -> false, () -> {});
        for (int cycle = 0; cycle < 5; cycle++) {
            helper.ungrabMouseCursor();
            helper.grabMouseCursor();
            helper.mouseXYChange();
        }
        assertEquals(0, original.grabs);
        assertEquals(0, original.warps);
        assertEquals(0, original.reads);
        assertEquals(0, helper.deltaX);
        assertEquals(0, helper.deltaY);
    }

    @Test
    public void foregroundManualMouseControlStillWorksAndBackgroundClearsDeltas() {
        FakeMouse original = new FakeMouse();
        boolean[] focused = { true };
        BackgroundMouseHelper helper = new BackgroundMouseHelper(original, () -> focused[0], () -> {});
        helper.grabMouseCursor();
        helper.mouseXYChange();
        assertEquals(1, original.grabs);
        assertEquals(12, helper.deltaX);
        assertEquals(-4, helper.deltaY);
        focused[0] = false;
        helper.mouseXYChange();
        assertEquals(0, helper.deltaX);
        assertEquals(0, helper.deltaY);
        assertEquals(1, original.reads);
    }

    private static final class FakeMouse extends MouseHelper {

        private int grabs, warps, reads;

        public void grabMouseCursor() {
            grabs++;
        }

        public void ungrabMouseCursor() {
            warps++;
        }

        public void mouseXYChange() {
            reads++;
            deltaX = 12;
            deltaY = -4;
        }
    }
}
