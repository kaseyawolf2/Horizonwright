package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime.RuntimeSnapshot;
import io.github.kaseyawolf2.horizonwright.Tags;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskResumeCandidates;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;

public final class GuiHorizonwrightDashboard extends GuiScreen {

    private static final int CLOSE_BUTTON = 1;
    private static final int AUTOMATION_STOP_BUTTON = 2;
    private static final int PAUSE_BUTTON = 3;
    private static final int DRY_RUN_BUTTON = 4;
    private static final int BARITONE_TAB_BUTTON = 5;
    private static final int PROFILE_ASSETS_TAB_BUTTON = 6;
    private static final int TASKS_BUTTON = 7;
    private static final int OVERVIEW_TAB_BUTTON = 8;
    private static final int SCHEDULES_BUTTON = 9;
    private static final int RESUME_CHOICE_BUTTON_BASE = 100;
    private static final int RESUME_PREVIOUS_BUTTON = 200;
    private static final int RESUME_NEXT_BUTTON = 201;
    private static final int RESUME_CANCEL_BUTTON = 202;
    private static final int RESUME_CHOICES_PER_PAGE = 5;

    private final CurrentRuntimeProvider runtimeProvider;
    private final ProfileAssetEditorProvider profileEditorProvider;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private GuiButton taskControlButton;
    private GuiButton dryRunButton;
    private GuiButton automationStopButton;
    private final List<GuiButton> resumeChoiceButtons = new ArrayList<>();
    private GuiButton resumePreviousButton;
    private GuiButton resumeNextButton;
    private GuiButton resumeCancelButton;
    private List<String> visibleResumeTaskIds = Collections.emptyList();
    private boolean resumeSelectorOpen;
    private int resumeSelectorPage;
    private String operatorMessage = "";

    public GuiHorizonwrightDashboard(CurrentRuntimeProvider runtimeProvider) {
        this(runtimeProvider, () -> java.util.Optional.empty());
    }

    public GuiHorizonwrightDashboard(CurrentRuntimeProvider runtimeProvider,
        ProfileAssetEditorProvider profileEditorProvider) {
        if (runtimeProvider == null) {
            throw new IllegalArgumentException("runtimeProvider must not be null");
        }
        if (profileEditorProvider == null) {
            throw new IllegalArgumentException("profileEditorProvider must not be null");
        }
        this.runtimeProvider = runtimeProvider;
        this.profileEditorProvider = profileEditorProvider;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        panelWidth = Math.min(420, width - 24);
        panelHeight = Math.min(320, height - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - panelHeight) / 2);

        int actionY = top + panelHeight - 28;
        taskControlButton = new GuiHorizonwrightButton(PAUSE_BUTTON, left + 12, actionY, 92, 20, "Pause active");
        dryRunButton = new GuiHorizonwrightButton(DRY_RUN_BUTTON, left + 110, actionY, 92, 20, "Dry-run: off");
        automationStopButton = new GuiHorizonwrightButton(
            AUTOMATION_STOP_BUTTON,
            left + 208,
            actionY,
            118,
            20,
            "Stop automation");
        taskControlButton.enabled = false;
        dryRunButton.enabled = false;
        automationStopButton.enabled = false;
        buttonList.add(taskControlButton);
        buttonList.add(dryRunButton);
        buttonList.add(automationStopButton);
        buttonList.add(new GuiHorizonwrightButton(CLOSE_BUTTON, left + panelWidth - 82, actionY, 70, 20, "Close"));

        int navigationWidth = panelWidth - 24;
        int tabGap = 4;
        int tabWidth = (navigationWidth - tabGap * 4) / 5;
        int navigationY = top + 42;
        GuiButton overviewTab = new GuiHorizonwrightButton(
            OVERVIEW_TAB_BUTTON,
            left + 12,
            navigationY,
            tabWidth,
            20,
            "Overview");
        overviewTab.enabled = false;
        buttonList.add(overviewTab);
        buttonList.add(
            new GuiHorizonwrightButton(
                TASKS_BUTTON,
                left + 12 + tabWidth + tabGap,
                navigationY,
                tabWidth,
                20,
                "Tasks"));
        buttonList.add(
            new GuiHorizonwrightButton(
                SCHEDULES_BUTTON,
                left + 12 + (tabWidth + tabGap) * 2,
                navigationY,
                tabWidth,
                20,
                "Schedules"));
        buttonList.add(
            new GuiHorizonwrightButton(
                PROFILE_ASSETS_TAB_BUTTON,
                left + 12 + (tabWidth + tabGap) * 3,
                navigationY,
                tabWidth,
                20,
                "Profile"));
        buttonList.add(
            new GuiHorizonwrightButton(
                BARITONE_TAB_BUTTON,
                left + 12 + (tabWidth + tabGap) * 4,
                navigationY,
                navigationWidth - (tabWidth + tabGap) * 4,
                20,
                "Baritone"));

        resumeChoiceButtons.clear();
        for (int index = 0; index < RESUME_CHOICES_PER_PAGE; index++) {
            GuiButton choice = new GuiHorizonwrightButton(
                RESUME_CHOICE_BUTTON_BASE + index,
                left + 42,
                top + 88 + index * 24,
                panelWidth - 84,
                20,
                "");
            choice.visible = false;
            resumeChoiceButtons.add(choice);
            buttonList.add(choice);
        }
        resumePreviousButton = new GuiHorizonwrightButton(
            RESUME_PREVIOUS_BUTTON,
            left + 42,
            top + 214,
            72,
            20,
            "Previous");
        resumeNextButton = new GuiHorizonwrightButton(RESUME_NEXT_BUTTON, left + 120, top + 214, 72, 20, "Next");
        resumeCancelButton = new GuiHorizonwrightButton(
            RESUME_CANCEL_BUTTON,
            left + panelWidth - 114,
            top + 214,
            72,
            20,
            "Cancel");
        buttonList.add(resumePreviousButton);
        buttonList.add(resumeNextButton);
        buttonList.add(resumeCancelButton);
        hideResumeSelectorControls();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CLOSE_BUTTON) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == BARITONE_TAB_BUTTON) {
            mc.displayGuiScreen(new GuiBaritoneSettings(this));
            return;
        }
        if (button.id == PROFILE_ASSETS_TAB_BUTTON) {
            mc.displayGuiScreen(new GuiProfileAssets(this, runtimeProvider, profileEditorProvider));
            return;
        }
        if (button.id == TASKS_BUTTON) {
            mc.displayGuiScreen(new GuiTaskManager(this, runtimeProvider));
            return;
        }
        if (button.id == SCHEDULES_BUTTON) {
            mc.displayGuiScreen(new GuiScheduleManager(this, runtimeProvider, profileEditorProvider));
            return;
        }
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) {
            disableActionControls();
            operatorMessage = "Session unavailable: " + resolution.getDiagnostic();
            return;
        }
        HorizonwrightRuntime runtime = resolution.getRuntime();
        if (button.id == RESUME_CANCEL_BUTTON) {
            closeResumeSelector();
            operatorMessage = "Resume selection cancelled.";
        } else if (button.id == RESUME_PREVIOUS_BUTTON) {
            resumeSelectorPage = Math.max(0, resumeSelectorPage - 1);
        } else if (button.id == RESUME_NEXT_BUTTON) {
            resumeSelectorPage++;
        } else if (button.id >= RESUME_CHOICE_BUTTON_BASE
            && button.id < RESUME_CHOICE_BUTTON_BASE + RESUME_CHOICES_PER_PAGE) {
                resumeVisibleChoice(runtime, button.id - RESUME_CHOICE_BUTTON_BASE);
            } else if (button.id == AUTOMATION_STOP_BUTTON) {
                try {
                    if (runtime.snapshot()
                        .getActionBroker()
                        .isAutomationLocked()) {
                        runtime.resetAutomationStop();
                        operatorMessage = "Automation re-armed; resume blocked tasks explicitly.";
                    } else {
                        runtime.stopAutomation("dashboard button");
                        operatorMessage = "Automation stopped; player control returns after packet drain.";
                    }
                } catch (RuntimeException failure) {
                    operatorMessage = "Automation control failed safely: " + safeMessage(failure);
                }
            } else if (button.id == PAUSE_BUTTON) {
                try {
                    ControllerSnapshot controller = runtime.controllerSnapshot();
                    if (controller.getActiveTaskId()
                        .isPresent()) {
                        operatorMessage = runtime.pauseActiveTask()
                            .isPresent() ? "Active task is pausing at a safe point." : "No active task to pause.";
                    } else {
                        TaskResumeCandidates candidates = TaskResumeCandidates.from(controller.getTasks());
                        if (candidates.size() == 1) {
                            TaskSnapshot candidate = candidates.onlyCandidate()
                                .get();
                            operatorMessage = "Resumed " + runtime.resumeTask(
                                candidate.getSpec()
                                    .getId())
                                .getSpec()
                                .getId() + ".";
                        } else if (candidates.isEmpty()) {
                            operatorMessage = "No suspended task to resume.";
                        } else {
                            resumeSelectorOpen = true;
                            resumeSelectorPage = 0;
                            operatorMessage = "Choose the movement or work task to resume.";
                        }
                    }
                } catch (RuntimeException failure) {
                    operatorMessage = "Task control failed safely: " + failure.getMessage();
                }
            } else if (button.id == DRY_RUN_BUTTON) {
                try {
                    runtime.setDryRun(!runtime.isDryRun());
                    operatorMessage = runtime.isDryRun() ? "Dry-run enabled; gameplay leases are disabled."
                        : "Dry-run disabled; resume blocked work explicitly.";
                } catch (RuntimeException failure) {
                    operatorMessage = "Dry-run change failed: " + failure.getMessage();
                }
            }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) {
            drawUnavailableScreen(mouseX, mouseY, partialTicks, resolution.getDiagnostic());
            return;
        }
        HorizonwrightRuntime runtime = resolution.getRuntime();
        RuntimeSnapshot snapshot;
        try {
            snapshot = runtime.snapshot();
        } catch (RuntimeException failure) {
            drawUnavailableScreen(
                mouseX,
                mouseY,
                partialTicks,
                "ACTIVE: Runtime snapshot failed safely: " + safeMessage(failure));
            return;
        }
        ControllerSnapshot controller = snapshot.getController();
        boolean locked = snapshot.getActionBroker()
            .isSafetyLocked();
        boolean automationStopped = snapshot.getActionBroker()
            .isAutomationLocked();
        boolean deathLocked = snapshot.getActionBroker()
            .isDeathSafetyLocked();
        TaskSnapshot activeTask = controller.getActiveTaskId()
            .isPresent()
                ? controller.findTask(
                    controller.getActiveTaskId()
                        .get())
                    .orElse(null)
                : null;
        TaskSnapshot blockedTask = firstBlocked(controller);
        TaskResumeCandidates resumeCandidates = TaskResumeCandidates.from(controller.getTasks());

        if (resumeSelectorOpen && (locked || activeTask != null || resumeCandidates.isEmpty())) {
            closeResumeSelector();
            operatorMessage = locked ? "Resume selection closed by the safety lock."
                : activeTask != null ? "Resume selection closed because a task became active."
                    : "Resume selection closed; no suspended tasks remain.";
        }

        taskControlButton.enabled = !locked && (activeTask != null || !resumeCandidates.isEmpty());
        taskControlButton.displayString = activeTask != null ? "Pause active"
            : resumeCandidates.size() == 1 ? "Resume task"
                : resumeCandidates.isEmpty() ? "No task to resume" : "Choose task";
        dryRunButton.enabled = !locked;
        dryRunButton.displayString = snapshot.isDryRun() ? "Dry-run: ON" : "Dry-run: off";
        automationStopButton.enabled = !deathLocked;
        automationStopButton.displayString = automationStopped ? "Reset automation" : "Stop automation";
        if (resumeSelectorOpen) {
            configureResumeSelector(resumeCandidates);
        } else {
            hideResumeSelectorControls();
        }

        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xE010141B);
        drawCenteredString(fontRendererObj, "Horizonwright " + Tags.VERSION, width / 2, top + 14, 0xFFF0C674);
        drawCenteredString(fontRendererObj, "Operations dashboard", width / 2, top + 28, 0xFF8FAAD0);

        drawString(fontRendererObj, "Active task", left + 16, top + 76, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            activeTask == null ? "none"
                : truncate(
                    activeTask.getSpec()
                        .getId() + " / "
                        + activeTask.getState()
                        + " / "
                        + activeTask.getDetail(),
                    42),
            left + 112,
            top + 76,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Queue", left + 16, top + 94, 0xFFAAAAAA);
        drawString(fontRendererObj, queueSummary(controller), left + 112, top + 94, 0xFFE0E0E0);
        drawString(fontRendererObj, "Blocked", left + 16, top + 112, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            blockedTask == null ? "none" : truncate(blockedSummary(blockedTask), 46),
            left + 112,
            top + 112,
            blockedTask == null ? 0xFFE0E0E0 : 0xFFFFAA66);
        drawString(fontRendererObj, "Navigation", left + 16, top + 130, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            truncate(snapshot.getNavigationDiagnostic(), 46),
            left + 112,
            top + 130,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Automation", left + 16, top + 148, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            deathLocked ? "Blocked by death state"
                : automationStopped ? "Stopped - player control is free"
                    : snapshot.isDryRun() ? "Dry-run - no gameplay actions" : "Ready",
            left + 112,
            top + 148,
            deathLocked ? 0xFFFF7777 : automationStopped || snapshot.isDryRun() ? 0xFFFFAA66 : 0xFF77DD77);
        drawString(fontRendererObj, "Actions active", left + 16, top + 166, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            Integer.toString(
                snapshot.getActionBroker()
                    .getActiveOwners()
                    .size()),
            left + 112,
            top + 166,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Control", left + 16, top + 184, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            deathLocked ? EnumChatFormatting.RED + "DEATH SAFETY LOCKED"
                : automationStopped ? EnumChatFormatting.YELLOW + "AUTOMATION STOPPED (player free)"
                    : snapshot.isDryRun() ? EnumChatFormatting.YELLOW + "DRY-RUN (leases disabled)"
                        : EnumChatFormatting.GREEN + "Live, operator-supervised",
            left + 112,
            top + 184,
            0xFFFFFFFF);
        NavigationProgress progress = snapshot.getNavigationProgress();
        drawString(
            fontRendererObj,
            progress == null ? "Navigation request: none"
                : truncate(progress.getRequestId() + ": " + progress.getState() + " - " + progress.getDetail(), 58),
            left + 16,
            top + 206,
            0xFFB8C8DE);
        drawString(
            fontRendererObj,
            truncate(operatorMessage, 58),
            left + 16,
            top + 224,
            operatorMessage.toLowerCase()
                .contains("failed") ? 0xFFFF7777 : 0xFFB8C8DE);
        drawString(fontRendererObj, "Unattended operation remains disabled.", left + 16, top + 244, 0xFFCC7777);

        if (resumeSelectorOpen) {
            drawResumeSelectorPanel(resumeCandidates.size());
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        if (!resumeSelectorOpen && blockedTask != null
            && mouseX >= left + 108
            && mouseX <= left + panelWidth - 12
            && mouseY >= top + 106
            && mouseY <= top + 124) {
            drawHoveringText(
                fontRendererObj.listFormattedStringToWidth(GuiTaskManager.taskDetails(blockedTask), panelWidth - 64),
                mouseX,
                mouseY,
                fontRendererObj);
        }
    }

    private void drawUnavailableScreen(int mouseX, int mouseY, float partialTicks, String diagnostic) {
        closeResumeSelector();
        disableActionControls();
        taskControlButton.displayString = "Session unavailable";
        dryRunButton.displayString = "Dry-run unavailable";
        automationStopButton.displayString = "Stop unavailable";

        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xE010141B);
        drawCenteredString(fontRendererObj, "Horizonwright " + Tags.VERSION, width / 2, top + 14, 0xFFF0C674);
        drawCenteredString(fontRendererObj, "Operations dashboard", width / 2, top + 28, 0xFF8FAAD0);
        drawString(fontRendererObj, "Session", left + 16, top + 82, 0xFFAAAAAA);
        drawString(fontRendererObj, "Unavailable", left + 112, top + 82, 0xFFFFAA66);
        drawString(fontRendererObj, truncate(diagnostic, 58), left + 16, top + 106, 0xFFFFAA66);
        drawString(
            fontRendererObj,
            "Join the bound world or resolve the session diagnostic.",
            left + 16,
            top + 130,
            0xFFB8C8DE);
        drawString(fontRendererObj, truncate(operatorMessage, 58), left + 16, top + 224, 0xFFB8C8DE);
        drawString(
            fontRendererObj,
            "No Horizonwright action controls are available.",
            left + 16,
            top + 244,
            0xFFCC7777);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void configureResumeSelector(TaskResumeCandidates candidates) {
        int pageCount = Math.max(1, (candidates.size() + RESUME_CHOICES_PER_PAGE - 1) / RESUME_CHOICES_PER_PAGE);
        resumeSelectorPage = Math.min(resumeSelectorPage, pageCount - 1);
        int firstCandidate = resumeSelectorPage * RESUME_CHOICES_PER_PAGE;
        List<String> visibleTaskIds = new ArrayList<>();
        for (int index = 0; index < resumeChoiceButtons.size(); index++) {
            GuiButton button = resumeChoiceButtons.get(index);
            int candidateIndex = firstCandidate + index;
            if (candidateIndex < candidates.size()) {
                TaskSnapshot task = candidates.asList()
                    .get(candidateIndex);
                visibleTaskIds.add(
                    task.getSpec()
                        .getId());
                button.displayString = truncate(resumeChoiceLabel(task), 46);
                button.visible = true;
                button.enabled = true;
            } else {
                button.visible = false;
                button.enabled = false;
            }
        }
        visibleResumeTaskIds = Collections.unmodifiableList(visibleTaskIds);
        resumePreviousButton.visible = pageCount > 1;
        resumePreviousButton.enabled = resumeSelectorPage > 0;
        resumeNextButton.visible = pageCount > 1;
        resumeNextButton.enabled = resumeSelectorPage + 1 < pageCount;
        resumeCancelButton.visible = true;
        resumeCancelButton.enabled = true;
        disableActionControls();
    }

    private void drawResumeSelectorPanel(int candidateCount) {
        int pageCount = Math.max(1, (candidateCount + RESUME_CHOICES_PER_PAGE - 1) / RESUME_CHOICES_PER_PAGE);
        drawRect(left + 28, top + 66, left + panelWidth - 28, top + 246, 0xFA10141B);
        drawCenteredString(
            fontRendererObj,
            "Select a task to resume  (page " + (resumeSelectorPage + 1) + "/" + pageCount + ")",
            width / 2,
            top + 74,
            0xFFF0C674);
        drawCenteredString(
            fontRendererObj,
            "Only the selected task will regain automation authority.",
            width / 2,
            top + 238,
            0xFFB8C8DE);
    }

    private void resumeVisibleChoice(HorizonwrightRuntime runtime, int visibleIndex) {
        if (visibleIndex < 0 || visibleIndex >= visibleResumeTaskIds.size()) {
            operatorMessage = "Resume choices changed; select again.";
            return;
        }
        String taskId = visibleResumeTaskIds.get(visibleIndex);
        try {
            TaskResumeCandidates currentCandidates = TaskResumeCandidates.from(
                runtime.controllerSnapshot()
                    .getTasks());
            if (findResumeCandidate(currentCandidates, taskId) == null) {
                operatorMessage = "Task " + taskId + " is no longer resumable; the list was refreshed.";
                return;
            }
            TaskSnapshot resumed = runtime.resumeTask(taskId);
            closeResumeSelector();
            operatorMessage = "Resumed " + resumed.getSpec()
                .getId() + ".";
        } catch (RuntimeException failure) {
            operatorMessage = "Task resume failed safely: " + safeMessage(failure);
        }
    }

    private void closeResumeSelector() {
        resumeSelectorOpen = false;
        resumeSelectorPage = 0;
        hideResumeSelectorControls();
    }

    private void hideResumeSelectorControls() {
        for (GuiButton button : resumeChoiceButtons) {
            button.visible = false;
            button.enabled = false;
        }
        if (resumePreviousButton != null) {
            resumePreviousButton.visible = false;
            resumePreviousButton.enabled = false;
        }
        if (resumeNextButton != null) {
            resumeNextButton.visible = false;
            resumeNextButton.enabled = false;
        }
        if (resumeCancelButton != null) {
            resumeCancelButton.visible = false;
            resumeCancelButton.enabled = false;
        }
        visibleResumeTaskIds = Collections.emptyList();
    }

    static String resumeChoiceLabel(TaskSnapshot task) {
        return task.getSpec()
            .getDisplayName() + "  ["
            + task.getSpec()
                .getId()
            + "] - "
            + task.getState();
    }

    private static TaskSnapshot findResumeCandidate(TaskResumeCandidates candidates, String taskId) {
        for (TaskSnapshot candidate : candidates.asList()) {
            if (candidate.getSpec()
                .getId()
                .equals(taskId)) {
                return candidate;
            }
        }
        return null;
    }

    private void disableActionControls() {
        if (taskControlButton != null) {
            taskControlButton.enabled = false;
        }
        if (dryRunButton != null) {
            dryRunButton.enabled = false;
        }
        if (automationStopButton != null) {
            automationStopButton.enabled = false;
        }
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null || failure.getMessage()
            .trim()
            .isEmpty() ? failure.getClass()
                .getSimpleName() : failure.getMessage();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 3) + "...";
    }

    private static String queueSummary(ControllerSnapshot snapshot) {
        StringBuilder summary = new StringBuilder();
        for (TaskLane lane : TaskLane.values()) {
            if (summary.length() > 0) {
                summary.append("  ");
            }
            summary.append(
                lane.name()
                    .charAt(0))
                .append(':')
                .append(
                    snapshot.getQueue()
                        .getLane(lane)
                        .size());
        }
        return summary.toString();
    }

    private static TaskSnapshot firstBlocked(ControllerSnapshot snapshot) {
        for (TaskSnapshot task : snapshot.getTasks()) {
            if (task.getBlockedReason()
                .isPresent()) {
                return task;
            }
        }
        return null;
    }

    private static String blockedSummary(TaskSnapshot task) {
        BlockedReason reason = task.getBlockedReason()
            .orElse(null);
        return reason == null ? "none"
            : task.getSpec()
                .getId() + ": "
                + reason.getDetail();
    }

}
