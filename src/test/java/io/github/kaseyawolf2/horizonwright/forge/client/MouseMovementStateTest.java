package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.*;

import net.minecraft.util.MouseHelper;

import org.junit.Test;

public class MouseMovementStateTest {

    @Test
    public void preservesFractionalCameraMovementEvenWhenIntegerDeltasAreZero() {
        FloatMouse source = new FloatMouse();
        FloatMouse target = new FloatMouse();
        source.x = 0.375F;
        source.y = -0.625F;
        MouseMovementState state = new MouseMovementState(source, target);

        state.copy();

        assertEquals(0, target.deltaX);
        assertEquals(0, target.deltaY);
        assertEquals(0.375F, target.lwjgl3ify$getFloatDX(), 0F);
        assertEquals(-0.625F, target.lwjgl3ify$getFloatDY(), 0F);
        state.clear();
        assertEquals(0F, target.lwjgl3ify$getFloatDX(), 0F);
        assertEquals(0F, target.lwjgl3ify$getFloatDY(), 0F);
        assertEquals(0.375F, source.x, 0F);
    }

    @Test
    public void supportsVanillaAndIntegerOnlyReplacementHelpers() {
        MouseHelper source = new MouseHelper();
        source.deltaX = 12;
        source.deltaY = -4;
        FloatMouse floatTarget = new FloatMouse();
        MouseMovementState state = new MouseMovementState(source, floatTarget);
        state.copy();
        assertEquals(12F, floatTarget.x, 0F);
        assertEquals(-4F, floatTarget.y, 0F);

        MouseHelper vanillaTarget = new MouseHelper();
        MouseMovementState vanilla = new MouseMovementState(source, vanillaTarget);
        vanilla.copy();
        assertEquals(12, vanillaTarget.deltaX);
        assertEquals(-4, vanillaTarget.deltaY);
        vanilla.clear();
        assertEquals(0, vanillaTarget.deltaX);
        assertEquals(0, vanillaTarget.deltaY);
    }

    /** Mirrors the public accessors injected into MouseHelper by LWJGL3ify. */
    public static final class FloatMouse extends MouseHelper {

        private float x;
        private float y;

        public float lwjgl3ify$getFloatDX() {
            return x;
        }

        public float lwjgl3ify$getFloatDY() {
            return y;
        }

        public void lwjgl3ify$setFloatDX(float value) {
            x = value;
        }

        public void lwjgl3ify$setFloatDY(float value) {
            y = value;
        }
    }
}
