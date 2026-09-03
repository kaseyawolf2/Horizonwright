package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleRule;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleState;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleTrigger;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.SleepTask;

/** Operator-facing recurring-job list and type-safe settings editor. */
public final class GuiScheduleManager extends GuiScreen {

    private static final int BACK_BUTTON = 1;
    private static final int PREVIOUS_BUTTON = 2;
    private static final int NEXT_BUTTON = 3;
    private static final int STATE_BUTTON = 4;
    private static final int SAVE_BUTTON = 5;
    private static final int DELETE_BUTTON = 6;
    private static final int SCHEDULE_BUTTON_BASE = 100;
    private static final int SCHEDULES_PER_PAGE = 5;

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtimeProvider;
    private final ProfileAssetEditorProvider editorProvider;
    private final List<GuiButton> scheduleButtons = new ArrayList<>();
    private List<ScheduleSnapshot> schedules = Collections.emptyList();
    private GuiTextField targetField;
    private GuiTextField intervalField;
    private GuiTextField reserveField;
    private GuiButton previousButton;
    private GuiButton nextButton;
    private GuiButton stateButton;
    private GuiButton saveButton;
    private GuiButton deleteButton;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private String selectedScheduleId;
    private String pendingDeleteScheduleId;
    private String message = "Select a scheduled job to inspect or edit it.";

    public GuiScheduleManager(GuiScreen parent, CurrentRuntimeProvider runtimeProvider,
        ProfileAssetEditorProvider editorProvider) {
        if (parent == null || runtimeProvider == null || editorProvider == null) {
            throw new IllegalArgumentException("parent, runtimeProvider, and editorProvider are required");
        }
        this.parent = parent;
        this.runtimeProvider = runtimeProvider;
        this.editorProvider = editorProvider;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        panelWidth = Math.min(500, width - 24);
        panelHeight = Math.min(350, height - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - panelHeight) / 2);

        scheduleButtons.clear();
        for (int index = 0; index < SCHEDULES_PER_PAGE; index++) {
            GuiButton button = new GuiHorizonwrightButton(
                SCHEDULE_BUTTON_BASE + index,
                left + 16,
                top + 42 + index * 24,
                panelWidth - 32,
                20,
                "");
            scheduleButtons.add(button);
            buttonList.add(button);
        }

        previousButton = new GuiHorizonwrightButton(PREVIOUS_BUTTON, left + 16, top + 166, 72, 20, "Previous");
        nextButton = new GuiHorizonwrightButton(NEXT_BUTTON, left + 94, top + 166, 72, 20, "Next");
        buttonList.add(previousButton);
        buttonList.add(nextButton);

        targetField = field(left + 92, top + 224, 132);
        intervalField = field(left + 314, top + 224, 54);
        reserveField = field(left + 430, top + 224, 48);

        int actionY = top + panelHeight - 28;
        stateButton = new GuiHorizonwrightButton(STATE_BUTTON, left + 16, actionY, 86, 20, "Pause");
        saveButton = new GuiHorizonwrightButton(SAVE_BUTTON, left + 108, actionY, 108, 20, "Save settings");
        deleteButton = new GuiHorizonwrightButton(DELETE_BUTTON, left + 222, actionY, 118, 20, "Delete schedule");
        buttonList.add(stateButton);
        buttonList.add(saveButton);
        buttonList.add(deleteButton);
        buttonList.add(new GuiHorizonwrightButton(BACK_BUTTON, left + panelWidth - 76, actionY, 60, 20, "Back"));
        refreshSchedules();
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
        if (button.id == PREVIOUS_BUTTON) {
            page = Math.max(0, page - 1);
            clearDeleteConfirmation();
            return;
        }
        if (button.id == NEXT_BUTTON) {
            page++;
            clearDeleteConfirmation();
            return;
        }
        if (button.id >= SCHEDULE_BUTTON_BASE && button.id < SCHEDULE_BUTTON_BASE + SCHEDULES_PER_PAGE) {
            selectVisible(button.id - SCHEDULE_BUTTON_BASE);
            return;
        }
        ScheduleSnapshot selected = selectedSchedule();
        if (selected == null) return;
        try {
            HorizonwrightRuntime runtime = requireRuntime();
            if (button.id == STATE_BUTTON) {
                if (selected.getState() == ScheduleState.ACTIVE) {
                    runtime.pauseSchedule(selectedScheduleId);
                    message = "Paused '" + selectedScheduleId + "'. No new occurrences will start.";
                } else if (selected.getState() == ScheduleState.PAUSED) {
                    runtime.resumeSchedule(selectedScheduleId);
                    message = "Resumed '" + selectedScheduleId + "'.";
                }
                clearDeleteConfirmation();
            } else if (button.id == SAVE_BUTTON) {
                saveSelected(runtime, selected);
                clearDeleteConfirmation();
            } else if (button.id == DELETE_BUTTON) {
                deleteSelected(runtime);
            }
            refreshSchedules();
        } catch (RuntimeException failure) {
            clearDeleteConfirmation();
            message = "Nothing changed: " + safeMessage(failure);
        }
    }

    private void selectVisible(int visibleIndex) {
        int scheduleIndex = page * SCHEDULES_PER_PAGE + visibleIndex;
        if (scheduleIndex < 0 || scheduleIndex >= schedules.size()) return;
        ScheduleSnapshot selected = schedules.get(scheduleIndex);
        selectedScheduleId = selected.getRule()
            .getId();
        clearDeleteConfirmation();
        populateEditor(selected);
        message = description(selected);
    }

    private void saveSelected(HorizonwrightRuntime runtime, ScheduleSnapshot selected) {
        String target = ProfileAssetInput.stableId(targetField.getText(), targetLabel(selected));
        if (FarmTask.TYPE.equals(
            selected.getRule()
                .getTask()
                .getType())) {
            requireSavedArea(target);
            int minutes = ProfileAssetInput.positiveInteger(intervalField.getText(), "interval minutes");
            int reserve = ProfileAssetInput.nonNegativeInteger(reserveField.getText(), "seed reserve");
            runtime.updateFarmSchedule(selectedScheduleId, target, reserve, Math.multiplyExact(minutes, 60_000L));
            message = "Saved '" + selectedScheduleId
                + "': farm '"
                + target
                + "' every "
                + minutes
                + " connected minute(s), seed reserve "
                + reserve
                + ".";
        } else if (SleepTask.TYPE.equals(
            selected.getRule()
                .getTask()
                .getType())) {
                    requireSavedLocation(target);
                    runtime.updateNightSleepSchedule(selectedScheduleId, target);
                    message = "Saved '" + selectedScheduleId + "': sleep at '" + target + "' each night.";
                } else {
                    throw new IllegalStateException("this schedule type is currently view-only");
                }
    }

    private void deleteSelected(HorizonwrightRuntime runtime) {
        if (!selectedScheduleId.equals(pendingDeleteScheduleId)) {
            pendingDeleteScheduleId = selectedScheduleId;
            message = "Delete '" + selectedScheduleId
                + "'? Existing task history remains. Click Confirm delete to remove future runs.";
            return;
        }
        String removed = selectedScheduleId;
        runtime.removeSchedule(removed);
        selectedScheduleId = null;
        pendingDeleteScheduleId = null;
        clearEditor();
        message = "Deleted scheduled job '" + removed + "'. Existing task history was preserved.";
    }

    @Override
    public void updateScreen() {
        targetField.updateCursorCounter();
        intervalField.updateCursorCounter();
        reserveField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        targetField.textboxKeyTyped(character, keyCode);
        intervalField.textboxKeyTyped(character, keyCode);
        reserveField.textboxKeyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        targetField.mouseClicked(mouseX, mouseY, mouseButton);
        intervalField.mouseClicked(mouseX, mouseY, mouseButton);
        reserveField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshSchedules();
        configureButtons();
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Scheduled jobs", width / 2, top + 14, 0xFFF0C674);

        ScheduleSnapshot selected = selectedSchedule();
        drawString(
            fontRendererObj,
            selected == null ? "No scheduled job selected." : truncate(description(selected), 78),
            left + 16,
            top + 194,
            0xFFB8C8DE);
        drawString(
            fontRendererObj,
            truncate(message, 78),
            left + 16,
            top + 208,
            message.startsWith("Nothing") || message.startsWith("Session unavailable") ? 0xFFFF7777 : 0xFF8FAAD0);
        drawString(
            fontRendererObj,
            selected == null ? "Target" : targetLabel(selected),
            left + 16,
            top + 230,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Minutes", left + 256, top + 230, 0xFFE0E0E0);
        drawString(fontRendererObj, "Seed reserve", left + 372, top + 230, 0xFFE0E0E0);
        targetField.drawTextBox();
        intervalField.drawTextBox();
        reserveField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void refreshSchedules() {
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) {
            schedules = Collections.emptyList();
            message = "Session unavailable: " + resolution.getDiagnostic();
            return;
        }
        schedules = resolution.getRuntime()
            .controllerSnapshot()
            .getScheduler()
            .getSchedules();
        int pages = Math.max(1, (schedules.size() + SCHEDULES_PER_PAGE - 1) / SCHEDULES_PER_PAGE);
        page = Math.min(page, pages - 1);
        if (selectedScheduleId != null && selectedSchedule() == null) {
            selectedScheduleId = null;
            clearDeleteConfirmation();
            clearEditor();
        }
    }

    private void configureButtons() {
        int pages = Math.max(1, (schedules.size() + SCHEDULES_PER_PAGE - 1) / SCHEDULES_PER_PAGE);
        int first = page * SCHEDULES_PER_PAGE;
        for (int index = 0; index < scheduleButtons.size(); index++) {
            GuiButton button = scheduleButtons.get(index);
            int scheduleIndex = first + index;
            if (scheduleIndex < schedules.size()) {
                ScheduleSnapshot schedule = schedules.get(scheduleIndex);
                button.displayString = schedule.getRule()
                    .getId() + " - "
                    + schedule.getState()
                    + " - "
                    + schedule.getRule()
                        .getTask()
                        .getDisplayName();
                button.visible = true;
                button.enabled = true;
            } else {
                button.visible = false;
                button.enabled = false;
            }
        }
        previousButton.enabled = page > 0;
        nextButton.enabled = page + 1 < pages;

        ScheduleSnapshot selected = selectedSchedule();
        boolean editable = selected != null && isEditable(selected);
        targetField.setEnabled(editable);
        intervalField.setEnabled(editable && isFarm(selected));
        reserveField.setEnabled(editable && isFarm(selected));
        saveButton.enabled = editable;
        stateButton.enabled = selected != null && selected.getState() != ScheduleState.CANCELLED;
        stateButton.displayString = selected != null && selected.getState() == ScheduleState.PAUSED ? "Resume"
            : "Pause";
        deleteButton.enabled = selected != null;
        deleteButton.displayString = selectedScheduleId != null && selectedScheduleId.equals(pendingDeleteScheduleId)
            ? "Confirm delete"
            : "Delete schedule";
    }

    private void populateEditor(ScheduleSnapshot selected) {
        if (isFarm(selected)) {
            targetField.setText(
                FarmTask.plotId(
                    selected.getRule()
                        .getTask()));
            intervalField.setText(
                Long.toString(
                    selected.getRule()
                        .getIntervalMillis() / 60_000L));
            reserveField.setText(
                Integer.toString(
                    FarmTask.minimumSeedReserve(
                        selected.getRule()
                            .getTask())));
        } else if (isSleep(selected)) {
            targetField.setText(
                SleepTask.bedLocationId(
                    selected.getRule()
                        .getTask()));
            intervalField.setText("nightly");
            reserveField.setText("n/a");
        } else {
            targetField.setText("view only");
            intervalField.setText("n/a");
            reserveField.setText("n/a");
        }
    }

    private void clearEditor() {
        targetField.setText("");
        intervalField.setText("");
        reserveField.setText("");
    }

    private ScheduleSnapshot selectedSchedule() {
        if (selectedScheduleId == null) return null;
        for (ScheduleSnapshot schedule : schedules) {
            if (schedule.getRule()
                .getId()
                .equals(selectedScheduleId)) return schedule;
        }
        return null;
    }

    private HorizonwrightRuntime requireRuntime() {
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) throw new IllegalStateException(resolution.getDiagnostic());
        return resolution.getRuntime();
    }

    private ProfileAssetEditor requireEditor() {
        return editorProvider.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"));
    }

    private void requireSavedArea(String areaId) {
        boolean found = requireEditor().load()
            .getNamedAreas()
            .stream()
            .anyMatch(
                area -> area.getId()
                    .equals(areaId));
        if (!found) throw new IllegalArgumentException("unknown saved farm area: " + areaId);
    }

    private void requireSavedLocation(String locationId) {
        boolean found = requireEditor().load()
            .getNamedLocations()
            .stream()
            .anyMatch(
                location -> location.getId()
                    .equals(locationId));
        if (!found) throw new IllegalArgumentException("unknown saved bed location: " + locationId);
    }

    private static boolean isEditable(ScheduleSnapshot schedule) {
        return isFarm(schedule) || isSleep(schedule);
    }

    private static boolean isFarm(ScheduleSnapshot schedule) {
        return schedule != null && FarmTask.TYPE.equals(
            schedule.getRule()
                .getTask()
                .getType());
    }

    private static boolean isSleep(ScheduleSnapshot schedule) {
        return schedule != null && SleepTask.TYPE.equals(
            schedule.getRule()
                .getTask()
                .getType());
    }

    private static String targetLabel(ScheduleSnapshot schedule) {
        return isFarm(schedule) ? "Farm area" : isSleep(schedule) ? "Bed" : "Target";
    }

    private static String description(ScheduleSnapshot schedule) {
        ScheduleRule rule = schedule.getRule();
        String timing = rule.getTrigger() == ScheduleTrigger.CONNECTED_INTERVAL
            ? "every " + rule.getIntervalMillis() / 60_000L + " connected minute(s)"
            : rule.getTrigger() == ScheduleTrigger.WORLD_TIME_WINDOW ? "once each Minecraft night" : "when idle";
        return rule.getId() + " | " + schedule.getState() + " | " + timing + " | runs: " + schedule.getTotalRuns();
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
    }

    private GuiTextField field(int x, int y, int width) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(48);
        return field;
    }

    private void clearDeleteConfirmation() {
        pendingDeleteScheduleId = null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
