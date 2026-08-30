package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime.RuntimeSnapshot;
import io.github.kaseyawolf2.horizonwright.Tags;

public final class GuiHorizonwrightDashboard extends GuiScreen {

    private static final int CLOSE_BUTTON = 1;
    private static final int EMERGENCY_STOP_BUTTON = 2;

    private final HorizonwrightRuntime runtime;
    private int left;
    private int top;
    private int panelWidth;

    public GuiHorizonwrightDashboard(HorizonwrightRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        panelWidth = Math.min(420, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(12, (height - 224) / 2);

        buttonList.add(new GuiButton(EMERGENCY_STOP_BUTTON, left + 12, top + 184, 130, 20, "Emergency stop"));
        buttonList.add(new GuiButton(CLOSE_BUTTON, left + panelWidth - 82, top + 184, 70, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CLOSE_BUTTON) {
            mc.displayGuiScreen(null);
        } else if (button.id == EMERGENCY_STOP_BUTTON) {
            runtime.emergencyStop("bootstrap dashboard button");
            button.enabled = false;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        RuntimeSnapshot snapshot = runtime.snapshot();
        boolean locked = snapshot.getActionBroker()
            .isSafetyLocked();

        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + 216, 0xE010141B);
        drawCenteredString(fontRendererObj, "Horizonwright " + Tags.VERSION, width / 2, top + 14, 0xFFF0C674);
        drawCenteredString(fontRendererObj, "Milestone 0A - Bootstrap", width / 2, top + 29, 0xFF8FAAD0);

        drawString(fontRendererObj, "Task orchestrator", left + 16, top + 58, 0xFFAAAAAA);
        drawString(fontRendererObj, "Not started", left + 152, top + 58, 0xFFE0E0E0);
        drawString(fontRendererObj, "Navigation", left + 16, top + 78, 0xFFAAAAAA);
        drawString(fontRendererObj, snapshot.getNavigationDiagnostic(), left + 152, top + 78, 0xFFE0E0E0);
        drawString(fontRendererObj, "Action epoch", left + 16, top + 98, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            Long.toString(
                snapshot.getActionBroker()
                    .getEpoch()),
            left + 152,
            top + 98,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Action leases", left + 16, top + 118, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            Integer.toString(
                snapshot.getActionBroker()
                    .getActiveOwners()
                    .size()),
            left + 152,
            top + 118,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Safety", left + 16, top + 138, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            locked ? EnumChatFormatting.RED + "EMERGENCY STOP LATCHED"
                : EnumChatFormatting.GREEN + "Ready (bootstrap only)",
            left + 152,
            top + 138,
            0xFFFFFFFF);
        drawString(fontRendererObj, "Unattended operation remains disabled.", left + 16, top + 160, 0xFFCC7777);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
