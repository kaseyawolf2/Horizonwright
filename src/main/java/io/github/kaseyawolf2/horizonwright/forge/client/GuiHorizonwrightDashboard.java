package io.github.kaseyawolf2.horizonwright.forge.client;

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

public final class GuiHorizonwrightDashboard extends GuiScreen {

    private static final int CLOSE_BUTTON = 1;
    private static final int AUTOMATION_STOP_BUTTON = 2;
    private static final int PAUSE_BUTTON = 3;
    private static final int DRY_RUN_BUTTON = 4;

    private final HorizonwrightRuntime runtime;
    private int left;
    private int top;
    private int panelWidth;
    private GuiButton taskControlButton;
    private GuiButton dryRunButton;
    private GuiButton automationStopButton;
    private String operatorMessage = "";

    public GuiHorizonwrightDashboard(HorizonwrightRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        panelWidth = Math.min(420, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 274) / 2);

        taskControlButton = new GuiButton(PAUSE_BUTTON, left + 12, top + 232, 92, 20, "Pause active");
        dryRunButton = new GuiButton(DRY_RUN_BUTTON, left + 110, top + 232, 92, 20, "Dry-run: off");
        automationStopButton = new GuiButton(AUTOMATION_STOP_BUTTON, left + 208, top + 232, 118, 20, "Stop automation");
        buttonList.add(taskControlButton);
        buttonList.add(dryRunButton);
        buttonList.add(automationStopButton);
        buttonList.add(new GuiButton(CLOSE_BUTTON, left + panelWidth - 82, top + 232, 70, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CLOSE_BUTTON) {
            mc.displayGuiScreen(null);
        } else if (button.id == AUTOMATION_STOP_BUTTON) {
            if (runtime.snapshot()
                .getActionBroker()
                .isAutomationLocked()) {
                try {
                    runtime.resetAutomationStop();
                    operatorMessage = "Automation re-armed; resume blocked tasks explicitly.";
                } catch (RuntimeException failure) {
                    operatorMessage = "Reset not ready: " + failure.getMessage();
                }
            } else {
                runtime.stopAutomation("dashboard button");
                operatorMessage = "Automation stopped; player control returns after packet drain.";
            }
        } else if (button.id == PAUSE_BUTTON) {
            try {
                ControllerSnapshot controller = runtime.controllerSnapshot();
                if (controller.getActiveTaskId()
                    .isPresent()) {
                    operatorMessage = runtime.pauseActiveTask()
                        .isPresent() ? "Active task is pausing at a safe point." : "No active task to pause.";
                } else {
                    OptionalTaskResume resume = onlyResumeCandidate(controller);
                    operatorMessage = resume.task == null ? resume.diagnostic
                        : "Resumed " + runtime.resumeTask(
                            resume.task.getSpec()
                                .getId())
                            .getSpec()
                            .getId() + ".";
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
        RuntimeSnapshot snapshot = runtime.snapshot();
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

        taskControlButton.enabled = !locked && (activeTask != null || resumeCandidates.size() == 1);
        taskControlButton.displayString = activeTask != null ? "Pause active"
            : resumeCandidates.size() == 1 ? "Resume task"
                : resumeCandidates.isEmpty() ? "No task to resume" : "Choose via /hw";
        dryRunButton.enabled = !locked;
        dryRunButton.displayString = snapshot.isDryRun() ? "Dry-run: ON" : "Dry-run: off";
        automationStopButton.enabled = !deathLocked;
        automationStopButton.displayString = automationStopped ? "Reset automation" : "Stop automation";

        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + 264, 0xE010141B);
        drawCenteredString(fontRendererObj, "Horizonwright " + Tags.VERSION, width / 2, top + 14, 0xFFF0C674);
        drawCenteredString(fontRendererObj, "Milestone 1 - Task control", width / 2, top + 29, 0xFF8FAAD0);

        drawString(fontRendererObj, "Active task", left + 16, top + 52, 0xFFAAAAAA);
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
            top + 52,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Queue", left + 16, top + 70, 0xFFAAAAAA);
        drawString(fontRendererObj, queueSummary(controller), left + 112, top + 70, 0xFFE0E0E0);
        drawString(fontRendererObj, "Blocked", left + 16, top + 88, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            blockedTask == null ? "none" : truncate(blockedSummary(blockedTask), 46),
            left + 112,
            top + 88,
            blockedTask == null ? 0xFFE0E0E0 : 0xFFFFAA66);
        drawString(fontRendererObj, "Navigation", left + 16, top + 106, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            truncate(snapshot.getNavigationDiagnostic(), 46),
            left + 112,
            top + 106,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Action epoch", left + 16, top + 124, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            Long.toString(
                snapshot.getActionBroker()
                    .getEpoch()),
            left + 112,
            top + 124,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Action leases", left + 16, top + 142, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            Integer.toString(
                snapshot.getActionBroker()
                    .getActiveOwners()
                    .size()),
            left + 112,
            top + 142,
            0xFFE0E0E0);
        drawString(fontRendererObj, "Mode", left + 16, top + 160, 0xFFAAAAAA);
        drawString(
            fontRendererObj,
            deathLocked ? EnumChatFormatting.RED + "DEATH SAFETY LOCKED"
                : automationStopped ? EnumChatFormatting.YELLOW + "AUTOMATION STOPPED (player free)"
                    : snapshot.isDryRun() ? EnumChatFormatting.YELLOW + "DRY-RUN (leases disabled)"
                        : EnumChatFormatting.GREEN + "Live, operator-supervised",
            left + 112,
            top + 160,
            0xFFFFFFFF);
        NavigationProgress progress = snapshot.getNavigationProgress();
        drawString(
            fontRendererObj,
            progress == null ? "Navigation request: none"
                : truncate(progress.getRequestId() + ": " + progress.getState() + " - " + progress.getDetail(), 58),
            left + 16,
            top + 180,
            0xFFB8C8DE);
        drawString(
            fontRendererObj,
            truncate(operatorMessage, 58),
            left + 16,
            top + 198,
            operatorMessage.toLowerCase()
                .contains("failed") ? 0xFFFF7777 : 0xFFB8C8DE);
        drawString(fontRendererObj, "Unattended operation remains disabled.", left + 16, top + 216, 0xFFCC7777);

        super.drawScreen(mouseX, mouseY, partialTicks);
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

    private static OptionalTaskResume onlyResumeCandidate(ControllerSnapshot controller) {
        TaskResumeCandidates candidates = TaskResumeCandidates.from(controller.getTasks());
        if (candidates.size() == 1) {
            return new OptionalTaskResume(
                candidates.onlyCandidate()
                    .get(),
                "");
        }
        return new OptionalTaskResume(
            null,
            candidates.isEmpty() ? "No suspended task to resume."
                : "Several tasks can resume; use /hw resume to choose one.");
    }

    private static final class OptionalTaskResume {

        private final TaskSnapshot task;
        private final String diagnostic;

        private OptionalTaskResume(TaskSnapshot task, String diagnostic) {
            this.task = task;
            this.diagnostic = diagnostic;
        }
    }
}
