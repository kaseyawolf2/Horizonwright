package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;

final class GuiMiningMode extends GuiReadableScreen {

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtime;
    private final ProfileAssetEditorProvider editors;
    private final NamedArea area;

    GuiMiningMode(GuiScreen parent, CurrentRuntimeProvider runtime, ProfileAssetEditorProvider editors,
        NamedArea area) {
        this.parent = parent;
        this.runtime = runtime;
        this.editors = editors;
        this.area = area;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(
            new GuiHorizonwrightButton(1, width / 2 - 170, height / 2 - 35, 340, 22, "Excavation: clear the volume"));
        buttonList.add(
            new GuiHorizonwrightButton(
                2,
                width / 2 - 170,
                height / 2,
                340,
                22,
                "Quarry: ramps, lighting and fluid settings"));
        buttonList.add(new GuiHorizonwrightButton(0, width / 2 - 60, height / 2 + 50, 120, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0) mc.displayGuiScreen(parent);
        if (b.id == 1) mc.displayGuiScreen(new GuiExcavationSetup(this, runtime, editors, area));
        if (b.id == 2) mc.displayGuiScreen(new GuiManagedQuarrySetup(this, runtime, editors, area));
    }

    @Override
    protected void drawContents(int x, int y, float ticks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            "Mining mode — " + (area.isCircular() ? "circle" : "rectangle"),
            width / 2,
            height / 2 - 70,
            0xFFF0C674);
        super.drawContents(x, y, ticks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
