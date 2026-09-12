package io.github.kaseyawolf2.horizonwright.forge.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.util.MouseHelper;

/** Copies the complete mouse sample, including LWJGL3ify's fractional camera deltas. */
final class MouseMovementState {

    private final MouseHelper source;
    private final MouseHelper target;
    private final Method getFloatX;
    private final Method getFloatY;
    private final Method setFloatX;
    private final Method setFloatY;

    MouseMovementState(MouseHelper source, MouseHelper target) {
        this.source = source;
        this.target = target;
        getFloatX = optionalMethod(source, "lwjgl3ify$getFloatDX");
        getFloatY = optionalMethod(source, "lwjgl3ify$getFloatDY");
        setFloatX = optionalMethod(target, "lwjgl3ify$setFloatDX", Float.TYPE);
        setFloatY = optionalMethod(target, "lwjgl3ify$setFloatDY", Float.TYPE);
    }

    void copy() {
        target.deltaX = source.deltaX;
        target.deltaY = source.deltaY;
        setFloats(read(getFloatX, source.deltaX), read(getFloatY, source.deltaY));
    }

    void clear() {
        target.deltaX = target.deltaY = 0;
        setFloats(0F, 0F);
    }

    private float read(Method method, int fallback) {
        return method == null ? fallback : ((Number) invoke(method, source)).floatValue();
    }

    private void setFloats(float x, float y) {
        if (setFloatX != null) invoke(setFloatX, target, x);
        if (setFloatY != null) invoke(setFloatY, target, y);
    }

    private static Method optionalMethod(MouseHelper helper, String name, Class<?>... arguments) {
        try {
            return helper.getClass()
                .getMethod(name, arguments);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not synchronize LWJGL3ify mouse movement", failure);
        }
    }
}
