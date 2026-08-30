package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime.RuntimeSnapshot;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskResumeCandidates;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingState;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;

public final class HorizonwrightClientCommand extends CommandBase {

    private final CurrentRuntimeProvider runtimeProvider;
    private final ClientProfileBindingCoordinator profileBindings;

    public HorizonwrightClientCommand(CurrentRuntimeProvider runtimeProvider) {
        this(runtimeProvider, null);
    }

    public HorizonwrightClientCommand(CurrentRuntimeProvider runtimeProvider,
        ClientProfileBindingCoordinator profileBindings) {
        if (runtimeProvider == null) {
            throw new IllegalArgumentException("runtimeProvider must not be null");
        }
        this.runtimeProvider = runtimeProvider;
        this.profileBindings = profileBindings;
    }

    @Override
    public String getCommandName() {
        return "hw";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/hw [panel|profile [status|enroll|recover|reassociate <id>]|status|task [id]|goto <x> <y> <z> [tolerance]|pause [id]|resume [id]|cancel <id>|navcancel|dryrun [on|off]|stop|reset]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        String subcommand = arguments.length == 0 ? "panel" : arguments[0].toLowerCase();
        if ("panel".equals(subcommand)) {
            ClientBootstrap.openDashboard();
            return;
        }
        if ("profile".equals(subcommand)) {
            controlProfile(sender, arguments);
            return;
        }
        if (!isRuntimeCommand(subcommand)) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Unknown Horizonwright command. " + getCommandUsage(sender)));
            return;
        }
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) {
            showUnavailable(sender, resolution);
            return;
        }
        HorizonwrightRuntime runtime = resolution.getRuntime();
        if ("status".equals(subcommand)) {
            showStatus(sender, runtime);
            return;
        }
        if ("task".equals(subcommand)) {
            showTask(sender, arguments, runtime);
            return;
        }
        if ("goto".equals(subcommand)) {
            startNavigation(sender, arguments, runtime);
            return;
        }
        if ("navcancel".equals(subcommand)) {
            try {
                Optional<TaskSnapshot> cancelled = runtime.cancelNavigationTask("manual /hw navcancel command");
                sender.addChatMessage(
                    new ChatComponentText(
                        cancelled.isPresent()
                            ? EnumChatFormatting.YELLOW + "Horizonwright navigation task "
                                + cancelled.get()
                                    .getSpec()
                                    .getId()
                                + " is cancelling safely."
                            : EnumChatFormatting.GRAY + "Horizonwright has no live navigation task."));
            } catch (RuntimeException failure) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "Navigation cancellation failed safely: " + failure.getMessage()));
            }
            return;
        }
        if ("pause".equals(subcommand)) {
            controlTask(sender, arguments, Control.PAUSE, runtime);
            return;
        }
        if ("resume".equals(subcommand)) {
            controlTask(sender, arguments, Control.RESUME, runtime);
            return;
        }
        if ("cancel".equals(subcommand)) {
            controlTask(sender, arguments, Control.CANCEL, runtime);
            return;
        }
        if ("dryrun".equals(subcommand)) {
            setDryRun(sender, arguments, runtime);
            return;
        }
        if ("stop".equals(subcommand)) {
            stopAutomation(sender, runtime);
            return;
        }
        if ("reset".equals(subcommand)) {
            resetAutomation(sender, runtime);
            return;
        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    private static boolean isRuntimeCommand(String subcommand) {
        return "status".equals(subcommand) || "task".equals(subcommand)
            || "goto".equals(subcommand)
            || "navcancel".equals(subcommand)
            || "pause".equals(subcommand)
            || "resume".equals(subcommand)
            || "cancel".equals(subcommand)
            || "dryrun".equals(subcommand)
            || "stop".equals(subcommand)
            || "reset".equals(subcommand);
    }

    private static void showUnavailable(ICommandSender sender, CurrentRuntimeUiResolver.Resolution resolution) {
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + "Horizonwright session unavailable: " + resolution.getDiagnostic()));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            return getListOfStringsMatchingLastWord(
                arguments,
                "panel",
                "profile",
                "status",
                "task",
                "goto",
                "pause",
                "resume",
                "cancel",
                "navcancel",
                "dryrun",
                "stop",
                "reset");
        }
        if (arguments.length == 2 && "profile".equalsIgnoreCase(arguments[0])) {
            return getListOfStringsMatchingLastWord(arguments, "status", "enroll", "recover", "reassociate");
        }
        if (arguments.length == 3 && "profile".equalsIgnoreCase(arguments[0])
            && "reassociate".equalsIgnoreCase(arguments[1])
            && profileBindings != null) {
            return getListOfStringsFromIterableMatchingLastWord(
                arguments,
                profileBindings.getSnapshot()
                    .getReassociationCandidateProfileIds());
        }
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) {
            return Collections.emptyList();
        }
        if (arguments.length != 2) {
            return Collections.emptyList();
        }
        ControllerSnapshot snapshot;
        try {
            snapshot = resolution.getRuntime()
                .controllerSnapshot();
        } catch (RuntimeException failure) {
            return Collections.emptyList();
        }
        if ("resume".equalsIgnoreCase(arguments[0])) {
            return getListOfStringsFromIterableMatchingLastWord(arguments, taskIds(resumeCandidates(snapshot)));
        }
        if ("pause".equalsIgnoreCase(arguments[0]) || "cancel".equalsIgnoreCase(arguments[0])
            || "task".equalsIgnoreCase(arguments[0])) {
            return getListOfStringsFromIterableMatchingLastWord(arguments, taskIds(snapshot.getTasks()));
        }
        if ("dryrun".equalsIgnoreCase(arguments[0])) {
            return getListOfStringsMatchingLastWord(arguments, "on", "off");
        }
        return Collections.emptyList();
    }

    private void controlProfile(ICommandSender sender, String[] arguments) {
        if (profileBindings == null) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.YELLOW + "Profile enrollment is unavailable."));
            return;
        }
        String operation = arguments.length < 2 ? "status" : arguments[1].toLowerCase();
        try {
            ClientProfileBindingSnapshot result;
            if ("status".equals(operation) && (arguments.length == 1 || arguments.length == 2)) {
                result = profileBindings.getSnapshot();
            } else if ("enroll".equals(operation) && arguments.length == 2) {
                result = profileBindings.confirmEnrollment(true);
            } else if ("recover".equals(operation) && arguments.length == 2) {
                result = profileBindings.recoverInterruptedUpdate();
            } else if ("reassociate".equals(operation) && arguments.length == 3) {
                profileBindings.requestReassociation(arguments[2]);
                result = profileBindings.confirmReassociation(arguments[2], true);
            } else {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED
                            + "Usage: /hw profile [status|enroll|recover|reassociate <profile-id>]"));
                return;
            }
            showProfileSnapshot(sender, result);
        } catch (RuntimeException failure) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Profile operation refused safely: " + safeMessage(failure)));
        }
    }

    private static void showProfileSnapshot(ICommandSender sender, ClientProfileBindingSnapshot snapshot) {
        EnumChatFormatting color = snapshot.getSelectedIdentity()
            .isPresent() ? EnumChatFormatting.GREEN
                : snapshot.getState() == ClientProfileBindingState.FAILED ? EnumChatFormatting.RED
                    : EnumChatFormatting.YELLOW;
        sender.addChatMessage(
            new ChatComponentText(
                color + "Horizonwright profile " + snapshot.getState() + ": " + snapshot.getDiagnostic()));
        if (!snapshot.getReassociationCandidateProfileIds()
            .isEmpty()) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + "Candidates: "
                        + String.join(", ", snapshot.getReassociationCandidateProfileIds())));
        }
    }

    private void startNavigation(ICommandSender sender, String[] arguments, HorizonwrightRuntime runtime) {
        if (arguments.length != 4 && arguments.length != 5) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
            return;
        }
        try {
            ChunkCoordinates current = sender.getPlayerCoordinates();
            int x = parseCoordinate(arguments[1], current.posX);
            int y = parseCoordinate(arguments[2], current.posY);
            int z = parseCoordinate(arguments[3], current.posZ);
            int tolerance = arguments.length == 5 ? Integer.parseInt(arguments[4]) : 1;
            TaskSnapshot submitted = runtime
                .submitGoTo(sender.getEntityWorld().provider.dimensionId, x, y, z, tolerance);
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + "Horizonwright queued task "
                        + submitted.getSpec()
                            .getId()
                        + " to "
                        + x
                        + ", "
                        + y
                        + ", "
                        + z
                        + " (tolerance "
                        + tolerance
                        + ")."));
        } catch (RuntimeException failure) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Navigation not started: " + failure.getMessage()));
        }
    }

    private void showStatus(ICommandSender sender, HorizonwrightRuntime runtime) {
        RuntimeSnapshot runtimeSnapshot = runtime.snapshot();
        ControllerSnapshot controller = runtimeSnapshot.getController();
        String active = controller.getActiveTaskId()
            .isPresent()
                ? controller.getActiveTaskId()
                    .get()
                : "none";
        int queued = nonTerminalCount(controller);
        String status = actionMode(runtimeSnapshot, active);
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + "Horizonwright: "
                    + status
                    + EnumChatFormatting.GRAY
                    + ", queued "
                    + queued
                    + ", epoch "
                    + controller.getActionEpoch()
                    + (runtimeSnapshot.isDryRun() ? ", DRY-RUN" : "")
                    + ", "
                    + runtimeSnapshot.getNavigationDiagnostic()
                    + navigationSuffix(runtimeSnapshot.getNavigationProgress())
                    + resumeSuffix(controller)));
    }

    private void resetAutomation(ICommandSender sender, HorizonwrightRuntime runtime) {
        try {
            boolean reset = runtime.resetAutomationStop();
            sender.addChatMessage(
                new ChatComponentText(
                    reset
                        ? EnumChatFormatting.GREEN
                            + "Horizonwright automation re-armed. Use /hw resume to continue the suspended task."
                        : EnumChatFormatting.GRAY + "Horizonwright has no manual automation stop to reset."));
        } catch (RuntimeException failure) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Automation reset not ready: " + failure.getMessage()));
        }
    }

    private static void stopAutomation(ICommandSender sender, HorizonwrightRuntime runtime) {
        try {
            runtime.stopAutomation("manual /hw stop command");
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Horizonwright automation stopped. Player control returns after the "
                        + "queued automation-packet drain; use /hw reset to re-arm."));
        } catch (RuntimeException failure) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Automation stop failed safely: " + failure.getMessage()));
        }
    }

    private static String actionMode(RuntimeSnapshot snapshot, String activeTaskId) {
        if (snapshot.getActionBroker()
            .isDeathSafetyLocked()) {
            return snapshot.getActionBroker()
                .isAutomationLocked() ? "DEATH SAFETY + AUTOMATION STOPPED" : "DEATH SAFETY LOCKED";
        }
        return snapshot.getActionBroker()
            .isAutomationLocked() ? "AUTOMATION STOPPED" : "active=" + activeTaskId;
    }

    private void showTask(ICommandSender sender, String[] arguments, HorizonwrightRuntime runtime) {
        if (arguments.length > 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
            return;
        }
        ControllerSnapshot snapshot = runtime.controllerSnapshot();
        if (arguments.length == 2) {
            Optional<TaskSnapshot> task = snapshot.findTask(arguments[1]);
            sender.addChatMessage(
                new ChatComponentText(
                    task.isPresent() ? formatTask(task.get())
                        : EnumChatFormatting.RED + "Unknown Horizonwright task: " + arguments[1]));
            return;
        }
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + "Horizonwright tasks: "
                    + nonTerminalCount(snapshot)
                    + " live, active "
                    + (snapshot.getActiveTaskId()
                        .isPresent()
                            ? snapshot.getActiveTaskId()
                                .get()
                            : "none")));
        for (TaskLane lane : TaskLane.values()) {
            if (!snapshot.getQueue()
                .getLane(lane)
                .isEmpty()) {
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.GRAY + lane.name() + ": " + joinTaskIds(snapshot, lane)));
            }
        }
    }

    private void controlTask(ICommandSender sender, String[] arguments, Control control, HorizonwrightRuntime runtime) {
        if ((control == Control.PAUSE && arguments.length > 2) || (control == Control.RESUME && arguments.length > 2)
            || (control == Control.CANCEL && arguments.length != 2)) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
            return;
        }
        try {
            TaskSnapshot result;
            if (control == Control.PAUSE && arguments.length == 1) {
                Optional<TaskSnapshot> paused = runtime.pauseActiveTask();
                if (!paused.isPresent()) {
                    sender.addChatMessage(
                        new ChatComponentText(EnumChatFormatting.GRAY + "Horizonwright has no active task to pause."));
                    return;
                }
                result = paused.get();
            } else if (control == Control.PAUSE) {
                result = runtime.pauseTask(arguments[1]);
            } else if (control == Control.RESUME && arguments.length == 1) {
                TaskResumeCandidates candidates = TaskResumeCandidates.from(
                    runtime.controllerSnapshot()
                        .getTasks());
                Optional<TaskSnapshot> only = candidates.onlyCandidate();
                if (!only.isPresent()) {
                    showResumeChoice(sender, candidates);
                    return;
                }
                result = runtime.resumeTask(
                    only.get()
                        .getSpec()
                        .getId());
            } else if (control == Control.RESUME) {
                result = runtime.resumeTask(arguments[1]);
            } else {
                result = runtime.cancelTask(arguments[1]);
            }
            sender.addChatMessage(
                new ChatComponentText(
                    control == Control.RESUME ? EnumChatFormatting.GREEN + "Resumed "
                        + result.getSpec()
                            .getId()
                        + EnumChatFormatting.GRAY
                        + " - "
                        + result.getDetail() : formatTask(result)));
        } catch (RuntimeException failure) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Task control failed safely: " + failure.getMessage()));
        }
    }

    private void setDryRun(ICommandSender sender, String[] arguments, HorizonwrightRuntime runtime) {
        if (arguments.length > 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
            return;
        }
        boolean enabled;
        if (arguments.length == 1) {
            enabled = !runtime.isDryRun();
        } else if ("on".equalsIgnoreCase(arguments[1])) {
            enabled = true;
        } else if ("off".equalsIgnoreCase(arguments[1])) {
            enabled = false;
        } else {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Use /hw dryrun on or off."));
            return;
        }
        runtime.setDryRun(enabled);
        sender.addChatMessage(
            new ChatComponentText(
                enabled ? EnumChatFormatting.YELLOW + "Horizonwright dry-run enabled; active work is pausing."
                    : EnumChatFormatting.GREEN + "Horizonwright dry-run disabled."));
    }

    private static int parseCoordinate(String value, int base) {
        long coordinate;
        if (value.startsWith("~")) {
            coordinate = value.length() == 1 ? base : (long) base + Long.parseLong(value.substring(1));
        } else {
            coordinate = Long.parseLong(value);
        }
        if (coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new NumberFormatException("coordinate is outside the integer range: " + value);
        }
        return (int) coordinate;
    }

    private static String navigationSuffix(NavigationProgress progress) {
        if (progress == null) {
            return "";
        }
        return ", " + progress.getRequestId() + " " + progress.getState() + " (" + progress.getDetail() + ")";
    }

    private static String resumeSuffix(ControllerSnapshot snapshot) {
        TaskResumeCandidates candidates = TaskResumeCandidates.from(snapshot.getTasks());
        if (candidates.isEmpty()) {
            return "";
        }
        if (candidates.size() == 1) {
            return ", resumable=" + candidates.onlyCandidate()
                .get()
                .getSpec()
                .getId() + " (use /hw resume)";
        }
        return ", " + candidates.size() + " resumable tasks (use /hw resume)";
    }

    private static void showResumeChoice(ICommandSender sender, TaskResumeCandidates candidates) {
        if (candidates.isEmpty()) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GRAY + "Horizonwright has no suspended task to resume."));
            return;
        }
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + "Several Horizonwright tasks can resume; click one to continue:"));
        for (TaskSnapshot candidate : candidates.asList()) {
            sender.addChatMessage(clickableResumeChoice(candidate));
        }
    }

    static IChatComponent clickableResumeChoice(TaskSnapshot candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        String taskId = candidate.getSpec()
            .getId();
        ChatComponentText choice = new ChatComponentText(
            "[Resume] " + candidate.getSpec()
                .getDisplayName() + "  [" + taskId + "]");
        choice.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setUnderlined(Boolean.TRUE)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hw resume " + taskId))
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText(
                        "Click to resume " + candidate.getSpec()
                            .getDisplayName()
                            + "\nTask: "
                            + taskId
                            + "\nState: "
                            + candidate.getState()
                            + "\n"
                            + candidate.getDetail())));
        return choice;
    }

    private static Iterable<TaskSnapshot> resumeCandidates(ControllerSnapshot snapshot) {
        return TaskResumeCandidates.from(snapshot.getTasks())
            .asList();
    }

    private static Iterable<String> taskIds(Iterable<TaskSnapshot> tasks) {
        List<String> ids = new ArrayList<>();
        for (TaskSnapshot task : tasks) {
            ids.add(
                task.getSpec()
                    .getId());
        }
        return ids;
    }

    private static int nonTerminalCount(ControllerSnapshot snapshot) {
        int count = 0;
        for (TaskSnapshot task : snapshot.getTasks()) {
            if (!task.getState()
                .isTerminal()) {
                count++;
            }
        }
        return count;
    }

    private static String joinTaskIds(ControllerSnapshot snapshot, TaskLane lane) {
        StringBuilder joined = new StringBuilder();
        for (TaskSnapshot task : snapshot.getQueue()
            .getLane(lane)) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(
                task.getSpec()
                    .getId());
        }
        return joined.toString();
    }

    private static String formatTask(TaskSnapshot task) {
        String blocked = task.getBlockedReason()
            .isPresent()
                ? ": " + task.getBlockedReason()
                    .get()
                    .getDetail()
                : "";
        return EnumChatFormatting.AQUA + task.getSpec()
            .getId() + EnumChatFormatting.GRAY + " " + task.getState() + " - " + task.getDetail() + blocked;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.trim()
            .isEmpty() ? failure.getClass()
                .getSimpleName() : message;
    }

    private enum Control {
        PAUSE,
        RESUME,
        CANCEL
    }
}
