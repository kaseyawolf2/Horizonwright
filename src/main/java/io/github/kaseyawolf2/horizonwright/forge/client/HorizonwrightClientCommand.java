package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.Optional;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime.RuntimeSnapshot;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;

public final class HorizonwrightClientCommand extends CommandBase {

    private final HorizonwrightRuntime runtime;

    public HorizonwrightClientCommand(HorizonwrightRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public String getCommandName() {
        return "hw";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/hw [panel|status|task [id]|goto <x> <y> <z> [tolerance]|pause [id]|resume <id>|cancel <id>|navcancel|dryrun [on|off]|stop|reset]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        String subcommand = arguments.length == 0 ? "panel" : arguments[0].toLowerCase();
        if ("panel".equals(subcommand)) {
            ClientBootstrap.openDashboard();
            return;
        }
        if ("status".equals(subcommand)) {
            showStatus(sender);
            return;
        }
        if ("task".equals(subcommand)) {
            showTask(sender, arguments);
            return;
        }
        if ("goto".equals(subcommand)) {
            startNavigation(sender, arguments);
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
            controlTask(sender, arguments, Control.PAUSE);
            return;
        }
        if ("resume".equals(subcommand)) {
            controlTask(sender, arguments, Control.RESUME);
            return;
        }
        if ("cancel".equals(subcommand)) {
            controlTask(sender, arguments, Control.CANCEL);
            return;
        }
        if ("dryrun".equals(subcommand)) {
            setDryRun(sender, arguments);
            return;
        }
        if ("stop".equals(subcommand)) {
            runtime.stopAutomation("manual /hw stop command");
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Horizonwright automation stopped. Player control returns after the "
                        + "queued automation-packet drain; use /hw reset to re-arm."));
            return;
        }
        if ("reset".equals(subcommand)) {
            resetAutomation(sender);
            return;
        }
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.RED + "Unknown Horizonwright command. " + getCommandUsage(sender)));
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    private void startNavigation(ICommandSender sender, String[] arguments) {
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

    private void showStatus(ICommandSender sender) {
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
                    + navigationSuffix(runtimeSnapshot.getNavigationProgress())));
    }

    private void resetAutomation(ICommandSender sender) {
        try {
            boolean reset = runtime.resetAutomationStop();
            sender.addChatMessage(
                new ChatComponentText(
                    reset
                        ? EnumChatFormatting.GREEN
                            + "Horizonwright automation re-armed; blocked tasks still require explicit resume."
                        : EnumChatFormatting.GRAY + "Horizonwright has no manual automation stop to reset."));
        } catch (RuntimeException failure) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Automation reset not ready: " + failure.getMessage()));
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

    private void showTask(ICommandSender sender, String[] arguments) {
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

    private void controlTask(ICommandSender sender, String[] arguments, Control control) {
        if ((control == Control.PAUSE && arguments.length > 2) || (control != Control.PAUSE && arguments.length != 2)) {
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
            } else if (control == Control.RESUME) {
                result = runtime.resumeTask(arguments[1]);
            } else {
                result = runtime.cancelTask(arguments[1]);
            }
            sender.addChatMessage(new ChatComponentText(formatTask(result)));
        } catch (RuntimeException failure) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Task control failed safely: " + failure.getMessage()));
        }
    }

    private void setDryRun(ICommandSender sender, String[] arguments) {
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

    private enum Control {
        PAUSE,
        RESUME,
        CANCEL
    }
}
