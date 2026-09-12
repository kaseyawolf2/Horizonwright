package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.ExcavationHudLayout;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.ExcavationStatisticsOverlay;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.HudPosition;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationStatistics;

/** Uses Minecraft's actual HUD coordinate space, rather than the dashboard's scaled viewport. */
public final class GuiHudEditor extends GuiScreen {

    private final GuiScreen parent;
    private HudPosition position = ExcavationStatisticsOverlay.position();
    private final String preview;
    private ExcavationHudLayout layout;
    private boolean dragging;
    private int grabX, grabY;
    private String message = "Drag the blue panel. Arrow keys nudge; Shift = 10 pixels.";

    public GuiHudEditor(GuiScreen parent, CurrentRuntimeProvider provider) {
        this.parent = parent;
        String sample = "Overall Total\n\nElapsed: 3m 18s : Time remaining: 12m 4s\nBroken: 416 : Remaining*: 2500\nBlocks/min: 126.1 : 30s avg: 140.0\n\nCurrent Layer (Y 71)\n\nElapsed: 53s : Time remaining: 1m 3s\nBroken: 116 : Remaining*: 138\nBlocks/min: 131.3 : 30s avg: 140.0\n\n* Remaining positions may include air.\nLast layer time: 1m 12s";
        if (provider.getCurrentRuntime()
            .isPresent()) {
            ControllerSnapshot snapshot = provider.getCurrentRuntime()
                .get()
                .controllerSnapshot();
            TaskSnapshot active = snapshot.getActiveTaskId()
                .flatMap(snapshot::findTask)
                .orElse(null);
            if (active != null && "excavation".equals(
                active.getSpec()
                    .getType()))
                sample = ExcavationStatistics.describe(active.getCheckpoint());
        }
        preview = sample;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        dragging = false;
        layout = new ExcavationHudLayout(fontRendererObj, preview, width, height);
        int w = Math.min(100, (width - 24) / 3);
        int left = (width - 3 * w - 8) / 2;
        buttonList.add(new GuiHorizonwrightButton(1, left, height - 26, w, 20, "Save"));
        buttonList.add(new GuiHorizonwrightButton(2, left + w + 4, height - 26, w, 20, "Cancel"));
        buttonList.add(new GuiHorizonwrightButton(3, left + 2 * w + 8, height - 26, w, 20, "Reset position"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Leave the game and other mods' HUDs visible as placement references.
        int x = position.x(width, layout.width), y = position.y(height, layout.height);
        layout.draw(fontRendererObj, x, y, true);
        drawCenteredString(fontRendererObj, "Horizonwright HUD position", width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, message, width / 2, 22, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        for (Object value : buttonList) {
            GuiButton control = (GuiButton) value;
            if (control.mousePressed(mc, x, y)) {
                super.mouseClicked(x, y, button);
                return;
            }
        }
        if (button == 0) {
            int panelX = position.x(width, layout.width), panelY = position.y(height, layout.height);
            if (x >= panelX && x < panelX + layout.width && y >= panelY && y < panelY + layout.height) {
                dragging = true;
                grabX = x - panelX;
                grabY = y - panelY;
            }
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (dragging && button == 0)
            position = HudPosition.at(x - grabX, y - grabY, width, height, layout.width, layout.height);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        if (button == 0) dragging = false;
        super.mouseMovedOrUp(x, y, button);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        int step = isShiftKeyDown() ? 10 : 1;
        int dx = key == Keyboard.KEY_LEFT ? -step : key == Keyboard.KEY_RIGHT ? step : 0;
        int dy = key == Keyboard.KEY_UP ? -step : key == Keyboard.KEY_DOWN ? step : 0;
        if (dx != 0 || dy != 0) position = HudPosition.at(
            position.x(width, layout.width) + dx,
            position.y(height, layout.height) + dy,
            width,
            height,
            layout.width,
            layout.height);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) mc.displayGuiScreen(parent);
        else if (button.id == 3) position = HudPosition.DEFAULT;
        else if (button.id == 1) {
            try {
                ExcavationStatisticsOverlay.savePosition(position);
                mc.displayGuiScreen(parent);
            } catch (java.io.IOException failure) {
                message = "Could not save HUD position. Try again.";
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
