package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.MathHelper;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;

/** Safe editor for one existing named area's display name and inclusive block bounds. */
public final class GuiSavedAreaEditor extends GuiScreen {

    private static final int BACK_BUTTON = 1;
    private static final int SAVE_BUTTON = 2;
    private static final int FIRST_HERE_BUTTON = 3;
    private static final int SECOND_HERE_BUTTON = 4;
    private static final int QUEUE_FARM_BUTTON = 5;
    private static final int SCHEDULE_FARM_BUTTON = 6;
    private static final int CLOSE_BUTTON = 7;
    private static final int HUSBANDRY_BUTTON = 8;

    private final GuiScreen parent;
    private final ProfileAssetEditorProvider editorProvider;
    private final CurrentRuntimeProvider runtimeProvider;
    private final NamedArea original;
    private GuiTextField displayName;
    private GuiTextField dimension;
    private GuiTextField firstX;
    private GuiTextField firstY;
    private GuiTextField firstZ;
    private GuiTextField secondX;
    private GuiTextField secondY;
    private GuiTextField secondZ;
    private GuiTextField seedReserve;
    private GuiTextField intervalMinutes;
    private int left;
    private int top;
    private int panelWidth;
    private String status = "Edit the inclusive bounds, or stand at a corner and capture your feet position.";

    public GuiSavedAreaEditor(GuiScreen parent, ProfileAssetEditorProvider editorProvider,
        CurrentRuntimeProvider runtimeProvider, NamedArea original) {
        if (parent == null || editorProvider == null || runtimeProvider == null || original == null) {
            throw new IllegalArgumentException(
                "parent, editorProvider, runtimeProvider, and original area are required");
        }
        this.parent = parent;
        this.editorProvider = editorProvider;
        this.runtimeProvider = runtimeProvider;
        this.original = original;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        panelWidth = Math.min(500, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 380) / 2);

        displayName = field(left + 116, top + 52, 188, original.getDisplayName());
        dimension = field(
            left + 408,
            top + 52,
            70,
            Integer.toString(
                original.getMinimum()
                    .getDimensionId()));
        firstX = field(
            left + 70,
            top + 104,
            64,
            Integer.toString(
                original.getMinimum()
                    .getX()));
        firstY = field(
            left + 180,
            top + 104,
            48,
            Integer.toString(
                original.getMinimum()
                    .getY()));
        firstZ = field(
            left + 274,
            top + 104,
            64,
            Integer.toString(
                original.getMinimum()
                    .getZ()));
        secondX = field(
            left + 70,
            top + 158,
            64,
            Integer.toString(
                original.getMaximum()
                    .getX()));
        secondY = field(
            left + 180,
            top + 158,
            48,
            Integer.toString(
                original.getMaximum()
                    .getY()));
        secondZ = field(
            left + 274,
            top + 158,
            64,
            Integer.toString(
                original.getMaximum()
                    .getZ()));
        seedReserve = field(left + 112, top + 267, 60, "2");
        intervalMinutes = field(left + 408, top + 267, 70, "30");
        loadFarmScheduleDefaults();

        buttonList
            .add(new GuiHorizonwrightButton(FIRST_HERE_BUTTON, left + 350, top + 103, 128, 20, "Use my feet here"));
        buttonList
            .add(new GuiHorizonwrightButton(SECOND_HERE_BUTTON, left + 350, top + 157, 128, 20, "Use my feet here"));
        buttonList.add(
            new GuiHorizonwrightButton(SAVE_BUTTON, left + 18, top + 232, panelWidth - 112, 22, "Save area changes"));
        buttonList
            .add(new GuiHorizonwrightButton(QUEUE_FARM_BUTTON, left + 18, top + 294, 220, 22, "Queue one farm pass"));
        buttonList.add(
            new GuiHorizonwrightButton(
                SCHEDULE_FARM_BUTTON,
                left + 244,
                top + 294,
                panelWidth - 262,
                22,
                "Schedule farm"));
        buttonList.add(new GuiHorizonwrightButton(CLOSE_BUTTON, left + 18, top + 332, 64, 20, "Close"));
        buttonList
            .add(new GuiHorizonwrightButton(HUSBANDRY_BUTTON, left + 94, top + 332, 168, 20, "Livestock settings"));
        buttonList.add(new GuiHorizonwrightButton(BACK_BUTTON, left + panelWidth - 82, top + 332, 64, 20, "Back"));
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
        if (button.id == HUSBANDRY_BUTTON) {
            mc.displayGuiScreen(new GuiHusbandrySetup(this, runtimeProvider, editorProvider, original));
            return;
        }
        try {
            if (button.id == FIRST_HERE_BUTTON) capture(firstX, firstY, firstZ);
            else if (button.id == SECOND_HERE_BUTTON) capture(secondX, secondY, secondZ);
            else if (button.id == SAVE_BUTTON) save();
            else if (button.id == QUEUE_FARM_BUTTON) queueFarmPass();
            else if (button.id == SCHEDULE_FARM_BUTTON) scheduleFarmPasses();
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    private void capture(GuiTextField x, GuiTextField y, GuiTextField z) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.theWorld.provider == null) {
            throw new IllegalStateException("join the bound world before capturing a corner");
        }
        dimension.setText(Integer.toString(mc.theWorld.provider.dimensionId));
        x.setText(Integer.toString(MathHelper.floor_double(mc.thePlayer.posX)));
        y.setText(Integer.toString(MathHelper.floor_double(mc.thePlayer.posY) - 1));
        z.setText(Integer.toString(MathHelper.floor_double(mc.thePlayer.posZ)));
        status = "Captured feet position. Review both corners, then save.";
    }

    private void save() {
        ProfileAssetEditor editor = editorProvider.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"));
        int parsedDimension = integer(dimension.getText(), "dimension");
        BasePosition first = new BasePosition(
            parsedDimension,
            integer(firstX.getText(), "corner 1 X"),
            integer(firstY.getText(), "corner 1 Y"),
            integer(firstZ.getText(), "corner 1 Z"));
        BasePosition second = new BasePosition(
            parsedDimension,
            integer(secondX.getText(), "corner 2 X"),
            integer(secondY.getText(), "corner 2 Y"),
            integer(secondZ.getText(), "corner 2 Z"));
        String name = displayName.getText() == null ? ""
            : displayName.getText()
                .trim();
        if (name.isEmpty()) throw new IllegalArgumentException("display name must not be blank");
        editor.apply(ProfileAssetUpdate.ofArea(new NamedArea(original.getId(), name, first, second)));
        status = "Saved area '" + original.getId() + "'. Schedules using it remain connected.";
    }

    private void queueFarmPass() {
        if (mc == null || mc.theWorld == null) throw new IllegalStateException("join the bound world first");
        int reserve = ProfileAssetInput.nonNegativeInteger(seedReserve.getText(), "minimum seed reserve");
        String taskId = "farm-" + original.getId() + "-" + MinecraftRuntimeAccess.totalWorldTime(mc.theWorld);
        HorizonwrightRuntime runtime = CurrentRuntimeUiResolver.resolve(runtimeProvider)
            .getRuntime();
        TaskSnapshot submitted = runtime.submitFarm(FarmTask.finitePass(taskId, original.getId(), reserve));
        status = "Queued '" + submitted.getSpec()
            .getId() + "' with seed reserve " + reserve + ".";
    }

    private void scheduleFarmPasses() {
        if (mc == null || mc.theWorld == null) throw new IllegalStateException("join the bound world first");
        int reserve = ProfileAssetInput.nonNegativeInteger(seedReserve.getText(), "minimum seed reserve");
        int minutes = ProfileAssetInput.positiveInteger(intervalMinutes.getText(), "farm interval minutes");
        long intervalMillis = Math.multiplyExact((long) minutes, 60_000L);
        String scheduleId = "farm-" + original.getId();
        HorizonwrightRuntime runtime = CurrentRuntimeUiResolver.resolve(runtimeProvider)
            .getRuntime();
        ScheduleSnapshot existing = findFarmSchedule(runtime, scheduleId);
        if (existing == null) {
            runtime.scheduleFarm(scheduleId, original.getId(), reserve, intervalMillis);
            status = "Scheduled '" + scheduleId + "' every " + minutes + " connected minute(s).";
            return;
        }
        if (existing.getState() == ScheduleState.CANCELLED) {
            runtime.removeSchedule(scheduleId);
            runtime.scheduleFarm(scheduleId, original.getId(), reserve, intervalMillis);
            status = "Recreated '" + scheduleId + "' every " + minutes + " connected minute(s).";
            return;
        }
        runtime.updateFarmSchedule(scheduleId, original.getId(), reserve, intervalMillis);
        if (existing.getState() == ScheduleState.PAUSED) runtime.resumeSchedule(scheduleId);
        status = "Updated '" + scheduleId + "' to every " + minutes + " connected minute(s).";
    }

    private void loadFarmScheduleDefaults() {
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) return;
        ScheduleSnapshot existing = findFarmSchedule(resolution.getRuntime(), "farm-" + original.getId());
        if (existing == null || !FarmTask.isForPlot(
            existing.getRule()
                .getTask(),
            original.getId())) return;
        seedReserve.setText(
            Integer.toString(
                FarmTask.minimumSeedReserve(
                    existing.getRule()
                        .getTask())));
        long interval = existing.getRule()
            .getIntervalMillis();
        if (interval >= 60_000L && interval % 60_000L == 0L) {
            intervalMinutes.setText(Long.toString(interval / 60_000L));
        }
    }

    private static ScheduleSnapshot findFarmSchedule(HorizonwrightRuntime runtime, String scheduleId) {
        for (ScheduleSnapshot schedule : runtime.controllerSnapshot()
            .getScheduler()
            .getSchedules()) {
            if (scheduleId.equals(
                schedule.getRule()
                    .getId()))
                return schedule;
        }
        return null;
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields()) field.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        for (GuiTextField field : fields()) field.textboxKeyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : fields()) field.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + 364, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Edit saved work area", width / 2, top + 14, 0xFFF0C674);
        drawCenteredString(fontRendererObj, "Stable ID: " + original.getId(), width / 2, top + 29, 0xFF8FAAD0);
        label("Display name", left + 18, top + 58);
        label("Dimension", left + 338, top + 58);
        label("Corner 1", left + 18, top + 84);
        coordinateLabels(top + 110);
        label("Corner 2", left + 18, top + 138);
        coordinateLabels(top + 164);
        fontRendererObj.drawSplitString(
            status,
            left + 18,
            top + 194,
            panelWidth - 36,
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        drawCenteredString(fontRendererObj, "Farm actions for this saved area", width / 2, top + 258, 0xFFF0C674);
        label("Seed reserve", left + 18, top + 273);
        label("Every minutes", left + 300, top + 273);
        for (GuiTextField field : fields()) field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void coordinateLabels(int y) {
        label("X", left + 54, y);
        label("Y", left + 164, y);
        label("Z", left + 258, y);
    }

    private void label(String text, int x, int y) {
        drawString(fontRendererObj, text, x, y, 0xFFE0E0E0);
    }

    private GuiTextField field(int x, int y, int width, String value) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(64);
        field.setText(value);
        return field;
    }

    private GuiTextField[] fields() {
        return new GuiTextField[] { displayName, dimension, firstX, firstY, firstZ, secondX, secondY, secondZ,
            seedReserve, intervalMinutes };
    }

    private static int integer(String value, String field) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(field + " must be a whole number", failure);
        }
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
