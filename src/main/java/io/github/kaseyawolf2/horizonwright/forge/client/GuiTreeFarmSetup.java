package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeTask;

/** Guided one-pass and recurring ordinary-tree controls for one saved area. */
public final class GuiTreeFarmSetup extends GuiScreen {

    private static final int QUEUE_BUTTON = 1;
    private static final int SCHEDULE_BUTTON = 2;
    private static final int BACK_BUTTON = 3;
    private static final int CLOSE_BUTTON = 4;

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtimeProvider;
    private final NamedArea area;
    private GuiTextField reserve;
    private GuiTextField interval;
    private int left;
    private int top;
    private int panelWidth;
    private String status = "Only supported vanilla trees wholly inside this area's 3D bounds are eligible.";

    public GuiTreeFarmSetup(GuiScreen parent, CurrentRuntimeProvider runtimeProvider, NamedArea area) {
        if (parent == null || runtimeProvider == null || area == null) {
            throw new IllegalArgumentException("parent, runtimeProvider, and area are required");
        }
        this.parent = parent;
        this.runtimeProvider = runtimeProvider;
        this.area = area;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        panelWidth = Math.min(500, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 248) / 2);
        reserve = field(left + 154, top + 91, 70, "2");
        interval = field(left + 388, top + 91, 80, "30");
        loadDefaults();
        buttonList.add(new GuiHorizonwrightButton(QUEUE_BUTTON, left + 18, top + 128, 220, 22, "Queue one tree pass"));
        buttonList.add(
            new GuiHorizonwrightButton(
                SCHEDULE_BUTTON,
                left + 246,
                top + 128,
                panelWidth - 264,
                22,
                "Schedule tree passes"));
        buttonList.add(new GuiHorizonwrightButton(CLOSE_BUTTON, left + 18, top + 202, 72, 20, "Close"));
        buttonList.add(new GuiHorizonwrightButton(BACK_BUTTON, left + panelWidth - 90, top + 202, 72, 20, "Back"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK_BUTTON) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == CLOSE_BUTTON) {
            mc.displayGuiScreen(null);
            return;
        }
        try {
            if (button.id == QUEUE_BUTTON) queue();
            else if (button.id == SCHEDULE_BUTTON) schedule();
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    private void queue() {
        HorizonwrightRuntime runtime = requireRuntime();
        if (mc.theWorld == null) throw new IllegalStateException("join the bound world first");
        int minimum = ProfileAssetInput.nonNegativeInteger(reserve.getText(), "minimum sapling reserve");
        String taskId = "trees-" + area.getId() + "-" + MinecraftRuntimeAccess.totalWorldTime(mc.theWorld);
        TaskSnapshot submitted = runtime.submitTreePass(TreeTask.finitePass(taskId, area.getId(), minimum));
        status = "Queued '" + submitted.getSpec()
            .getId() + "' with sapling reserve " + minimum + ".";
    }

    private void schedule() {
        HorizonwrightRuntime runtime = requireRuntime();
        int minimum = ProfileAssetInput.nonNegativeInteger(reserve.getText(), "minimum sapling reserve");
        int minutes = ProfileAssetInput.positiveInteger(interval.getText(), "tree interval minutes");
        long intervalMillis = Math.multiplyExact((long) minutes, 60_000L);
        String id = "trees-" + area.getId();
        ScheduleSnapshot existing = findSchedule(runtime, id);
        if (existing == null) runtime.scheduleTreePass(id, area.getId(), minimum, intervalMillis);
        else if (existing.getState() == ScheduleState.CANCELLED) {
            runtime.removeSchedule(id);
            runtime.scheduleTreePass(id, area.getId(), minimum, intervalMillis);
        } else {
            runtime.updateTreeSchedule(id, area.getId(), minimum, intervalMillis);
            if (existing.getState() == ScheduleState.PAUSED) runtime.resumeSchedule(id);
        }
        status = "Saved '" + id + "' every " + minutes + " connected minute(s).";
    }

    private void loadDefaults() {
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) return;
        ScheduleSnapshot existing = findSchedule(resolution.getRuntime(), "trees-" + area.getId());
        if (existing == null || !TreeTask.isForArea(
            existing.getRule()
                .getTask(),
            area.getId())) return;
        reserve.setText(
            Integer.toString(
                TreeTask.minimumSaplingReserve(
                    existing.getRule()
                        .getTask())));
        long millis = existing.getRule()
            .getIntervalMillis();
        if (millis >= 60_000L && millis % 60_000L == 0L) interval.setText(Long.toString(millis / 60_000L));
    }

    private static ScheduleSnapshot findSchedule(HorizonwrightRuntime runtime, String id) {
        for (ScheduleSnapshot schedule : runtime.controllerSnapshot()
            .getScheduler()
            .getSchedules()) {
            if (id.equals(
                schedule.getRule()
                    .getId()))
                return schedule;
        }
        return null;
    }

    private HorizonwrightRuntime requireRuntime() {
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) throw new IllegalStateException(resolution.getDiagnostic());
        return resolution.getRuntime();
    }

    @Override
    public void updateScreen() {
        reserve.updateCursorCounter();
        interval.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        reserve.textboxKeyTyped(character, keyCode);
        interval.textboxKeyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        reserve.mouseClicked(mouseX, mouseY, mouseButton);
        interval.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + 232, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Ordinary tree farm", width / 2, top + 14, 0xFFF0C674);
        drawCenteredString(
            fontRendererObj,
            area.getDisplayName() + " (" + area.getId() + ")",
            width / 2,
            top + 31,
            0xFF8FAAD0);
        fontRendererObj.drawSplitString(
            status,
            left + 18,
            top + 52,
            panelWidth - 36,
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        drawString(fontRendererObj, "Keep saplings", left + 18, top + 97, 0xFFE0E0E0);
        drawString(fontRendererObj, "Every minutes", left + 270, top + 97, 0xFFE0E0E0);
        fontRendererObj.drawSplitString(
            "The area must include every log from ground to canopy. Trees crossing its boundary and dark-oak 2x2 trees are skipped.",
            left + 18,
            top + 166,
            panelWidth - 36,
            0xFF98A8BD);
        reserve.drawTextBox();
        interval.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private GuiTextField field(int x, int y, int width, String value) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(12);
        field.setText(value);
        return field;
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
