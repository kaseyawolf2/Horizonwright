package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.function.BooleanSupplier;

import net.minecraft.util.MouseHelper;

/** Releases the desktop cursor without vanilla's window-center warp. */
final class BackgroundMouseHelper extends MouseHelper {

    private final MouseHelper delegate;
    private final BooleanSupplier focused;
    private final Runnable release;
    private final MouseMovementState movement;

    BackgroundMouseHelper(MouseHelper delegate, BooleanSupplier focused, Runnable release) {
        this.delegate = delegate;
        this.focused = focused;
        this.release = release;
        this.movement = new MouseMovementState(delegate, this);
    }

    MouseHelper original() {
        return delegate;
    }

    @Override
    public void grabMouseCursor() {
        if (focused.getAsBoolean()) delegate.grabMouseCursor();
        else release.run();
        movement.clear();
    }

    @Override
    public void ungrabMouseCursor() {
        // Never call vanilla here: it calls Mouse.setCursorPosition before releasing.
        release.run();
        movement.clear();
    }

    @Override
    public void mouseXYChange() {
        if (focused.getAsBoolean()) {
            delegate.mouseXYChange();
            movement.copy();
        } else {
            movement.clear();
        }
    }
}
