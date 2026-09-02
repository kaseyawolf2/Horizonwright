package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;

/** Operator-facing task history and safe deletion screen. */
public final class GuiTaskManager extends GuiScreen {

    private static final int BACK_BUTTON = 1;
    private static final int DELETE_BUTTON = 2;
    private static final int PREVIOUS_BUTTON = 3;
    private static final int NEXT_BUTTON = 4;
    private static final int CLEAR_COMPLETED_BUTTON = 5;
    private static final int TASK_BUTTON_BASE = 100;
    private static final int TASKS_PER_PAGE = 5;

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtimeProvider;
    private final List<GuiButton> taskButtons = new ArrayList<>();
    private List<String> visibleTaskIds = Collections.emptyList();
    private GuiButton deleteButton;
    private GuiButton previousButton;
    private GuiButton nextButton;
    private GuiButton clearCompletedButton;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private String selectedTaskId;
    private String confirmationTaskId;
    private boolean confirmClearCompleted;
    private String message = "Select a task to inspect it.";

    public GuiTaskManager(GuiScreen parent, CurrentRuntimeProvider runtimeProvider) {
        if (parent == null || runtimeProvider == null) {
            throw new IllegalArgumentException("parent and runtimeProvider must not be null");
        }
        this.parent = parent;
        this.runtimeProvider = runtimeProvider;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        panelWidth = Math.min(470, width - 24);
        panelHeight = Math.min(340, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        taskButtons.clear();
        for (int index = 0; index < TASKS_PER_PAGE; index++) {
            GuiButton button = new GuiButton(
                TASK_BUTTON_BASE + index,
                left + 16,
                top + 42 + index * 24,
                panelWidth - 32,
                20,
                "");
            taskButtons.add(button);
            buttonList.add(button);
        }
        previousButton = new GuiButton(PREVIOUS_BUTTON, left + 16, top + 166, 72, 20, "Previous");
        nextButton = new GuiButton(NEXT_BUTTON, left + 94, top + 166, 72, 20, "Next");
        deleteButton = new GuiButton(
            DELETE_BUTTON,
            left + panelWidth - 168,
            top + panelHeight - 28,
            90,
            20,
            "Delete task");
        buttonList.add(previousButton);
        buttonList.add(nextButton);
        clearCompletedButton = new GuiButton(
            CLEAR_COMPLETED_BUTTON,
            left + 16,
            top + panelHeight - 28,
            116,
            20,
            "Clear completed");
        buttonList.add(clearCompletedButton);
        buttonList.add(deleteButton);
        buttonList.add(new GuiButton(BACK_BUTTON, left + panelWidth - 72, top + panelHeight - 28, 56, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK_BUTTON) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == PREVIOUS_BUTTON) {
            page = Math.max(0, page - 1);
            clearConfirmation();
            return;
        }
        if (button.id == NEXT_BUTTON) {
            page++;
            clearConfirmation();
            return;
        }
        if (button.id == CLEAR_COMPLETED_BUTTON) {
            clearCompletedTasks();
            return;
        }
        if (button.id >= TASK_BUTTON_BASE && button.id < TASK_BUTTON_BASE + TASKS_PER_PAGE) {
            int visibleIndex = button.id - TASK_BUTTON_BASE;
            if (visibleIndex < visibleTaskIds.size()) {
                selectedTaskId = visibleTaskIds.get(visibleIndex);
                clearConfirmation();
                message = "Selected " + selectedTaskId + ".";
            }
            return;
        }
        if (button.id == DELETE_BUTTON && selectedTaskId != null) {
            if (!selectedTaskId.equals(confirmationTaskId)) {
                confirmationTaskId = selectedTaskId;
                message = "Press Confirm delete to permanently remove " + selectedTaskId + ".";
                return;
            }
            CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
            if (!resolution.isAvailable()) {
                message = "Session unavailable: " + resolution.getDiagnostic();
                return;
            }
            try {
                resolution.getRuntime()
                    .removeTask(selectedTaskId);
                message = "Deleted task " + selectedTaskId + ". Schedules are unchanged.";
                selectedTaskId = null;
                clearConfirmation();
            } catch (RuntimeException failure) {
                message = "Task was not deleted: " + safeMessage(failure);
                clearConfirmation();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Horizonwright tasks", width / 2, top + 14, 0xFFF0C674);

        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        List<TaskSnapshot> tasks = resolution.isAvailable() ? resolution.getRuntime()
            .controllerSnapshot()
            .getTasks() : Collections.<TaskSnapshot>emptyList();
        configureTaskButtons(tasks);
        TaskSnapshot selected = find(tasks, selectedTaskId);
        if (selectedTaskId != null && selected == null) {
            selectedTaskId = null;
            clearConfirmation();
        }

        drawString(fontRendererObj, "Task details", left + 16, top + 194, 0xFFAAAAAA);
        String details = selected == null ? message : taskDetails(selected);
        fontRendererObj.drawSplitString(details, left + 16, top + 208, panelWidth - 32, 0xFFE0E0E0);
        drawString(
            fontRendererObj,
            message,
            left + 16,
            top + panelHeight - 42,
            message.startsWith("Task was not") || message.startsWith("Session unavailable") ? 0xFFFF7777 : 0xFF8FAAD0);

        deleteButton.enabled = selected != null && canDelete(selected);
        deleteButton.displayString = selected != null && selected.getSpec()
            .getId()
            .equals(confirmationTaskId) ? "Confirm delete" : "Delete task";
        int completedCount = completedCount(tasks);
        clearCompletedButton.enabled = completedCount > 0;
        clearCompletedButton.displayString = confirmClearCompleted ? "Confirm clear (" + completedCount + ")"
            : "Clear completed";
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void clearCompletedTasks() {
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) {
            message = "Session unavailable: " + resolution.getDiagnostic();
            return;
        }
        List<TaskSnapshot> tasks = resolution.getRuntime()
            .controllerSnapshot()
            .getTasks();
        int count = completedCount(tasks);
        if (count == 0) {
            confirmClearCompleted = false;
            message = "There are no completed tasks to clear.";
            return;
        }
        if (!confirmClearCompleted) {
            confirmClearCompleted = true;
            confirmationTaskId = null;
            message = "Press Confirm clear to remove " + count + " completed task(s).";
            return;
        }
        int removed = 0;
        try {
            for (TaskSnapshot task : new ArrayList<>(tasks)) {
                if (task.getState() == TaskState.COMPLETED) {
                    resolution.getRuntime()
                        .removeTask(
                            task.getSpec()
                                .getId());
                    removed++;
                }
            }
            selectedTaskId = null;
            confirmClearCompleted = false;
            message = "Cleared " + removed + " completed task(s).";
        } catch (RuntimeException failure) {
            confirmClearCompleted = false;
            message = "Completed tasks were only partially cleared: " + safeMessage(failure);
        }
    }

    private static int completedCount(List<TaskSnapshot> tasks) {
        int count = 0;
        for (TaskSnapshot task : tasks) if (task.getState() == TaskState.COMPLETED) count++;
        return count;
    }

    private void configureTaskButtons(List<TaskSnapshot> tasks) {
        int pageCount = Math.max(1, (tasks.size() + TASKS_PER_PAGE - 1) / TASKS_PER_PAGE);
        page = Math.min(page, pageCount - 1);
        int first = page * TASKS_PER_PAGE;
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < taskButtons.size(); index++) {
            GuiButton button = taskButtons.get(index);
            int taskIndex = first + index;
            if (taskIndex < tasks.size()) {
                TaskSnapshot task = tasks.get(taskIndex);
                ids.add(
                    task.getSpec()
                        .getId());
                button.displayString = truncate(
                    task.getSpec()
                        .getId() + " - "
                        + task.getState()
                        + " - "
                        + task.getDetail(),
                    68);
                button.visible = true;
                button.enabled = true;
            } else {
                button.visible = false;
                button.enabled = false;
            }
        }
        visibleTaskIds = Collections.unmodifiableList(ids);
        previousButton.enabled = page > 0;
        nextButton.enabled = page + 1 < pageCount;
    }

    private static boolean canDelete(TaskSnapshot task) {
        return task.getState() != TaskState.RUNNING && task.getState() != TaskState.SUSPENDING;
    }

    private static TaskSnapshot find(List<TaskSnapshot> tasks, String taskId) {
        if (taskId == null) return null;
        for (TaskSnapshot task : tasks) {
            if (task.getSpec()
                .getId()
                .equals(taskId)) return task;
        }
        return null;
    }

    static String taskDetails(TaskSnapshot task) {
        StringBuilder result = new StringBuilder();
        result.append(
            task.getSpec()
                .getId())
            .append(" [")
            .append(task.getState())
            .append("]\n")
            .append(task.getDetail());
        BlockedReason reason = task.getBlockedReason()
            .orElse(null);
        if (reason != null) {
            result.append("\nBlocked: ")
                .append(reason.getDetail());
            if (!reason.getMissingRequirement()
                .isEmpty()) {
                result.append("\nNeeds: ")
                    .append(reason.getMissingRequirement());
            }
            if (!reason.getRequiredUserAction()
                .isEmpty()) {
                result.append("\nNext: ")
                    .append(reason.getRequiredUserAction());
            }
        }
        return result.toString();
    }

    private void clearConfirmation() {
        confirmationTaskId = null;
        confirmClearCompleted = false;
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 3) + "...";
    }

    private static String safeMessage(RuntimeException failure) {
        String detail = failure.getMessage();
        return detail == null || detail.trim()
            .isEmpty() ? failure.getClass()
                .getSimpleName() : detail;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
