package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/** Keeps a joined automation client running without warping the desktop mouse. */
final class BackgroundClientControl {

    private BackgroundMouseHelper helper;
    private GameSettings settings;
    private boolean previousPauseOnLostFocus;

    void update(Minecraft mc, boolean attached) {
        boolean enabled = attached && mc.theWorld != null && mc.thePlayer != null;
        if (!enabled) {
            if (helper != null && mc.mouseHelper == helper) mc.mouseHelper = helper.original();
            helper = null;
            if (settings != null) settings.pauseOnLostFocus = previousPauseOnLostFocus;
            settings = null;
            return;
        }
        if (settings != mc.gameSettings) {
            if (settings != null) settings.pauseOnLostFocus = previousPauseOnLostFocus;
            settings = mc.gameSettings;
            previousPauseOnLostFocus = settings.pauseOnLostFocus;
        }
        settings.pauseOnLostFocus = false;
        if (mc.mouseHelper != helper || helper == null) {
            helper = new BackgroundMouseHelper(
                mc.mouseHelper,
                Display::isActive,
                () -> { if (Mouse.isCreated() && Mouse.isGrabbed()) Mouse.setGrabbed(false); });
            mc.mouseHelper = helper;
        }
        if (!Display.isActive()) {
            helper.ungrabMouseCursor();
            // No GUI is opened and no automation key state is reset. A deliberate
            // click into the game can restore normal foreground mouse capture.
            mc.inGameHasFocus = false;
        }
    }
}
