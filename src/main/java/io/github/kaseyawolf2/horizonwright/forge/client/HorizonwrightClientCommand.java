package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime.RuntimeSnapshot;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;

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
        return "/hw [panel|status|goto <x> <y> <z> [tolerance]|navcancel|stop]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        String subcommand = arguments.length == 0 ? "panel" : arguments[0].toLowerCase();
        if ("panel".equals(subcommand)) {
            ClientBootstrap.openDashboard();
            return;
        }
        if ("status".equals(subcommand)) {
            RuntimeSnapshot snapshot = runtime.snapshot();
            String status = snapshot.getActionBroker()
                .isSafetyLocked() ? "SAFETY LOCKED" : "idle";
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + "Horizonwright: "
                        + status
                        + EnumChatFormatting.GRAY
                        + ", epoch "
                        + snapshot.getActionBroker()
                            .getEpoch()
                        + ", "
                        + snapshot.getNavigationDiagnostic()
                        + navigationSuffix(snapshot.getNavigationProgress())));
            return;
        }
        if ("goto".equals(subcommand)) {
            startNavigation(sender, arguments);
            return;
        }
        if ("navcancel".equals(subcommand)) {
            try {
                boolean cancelled = runtime.cancelNavigation("manual /hw navcancel command");
                sender.addChatMessage(
                    new ChatComponentText(
                        cancelled ? EnumChatFormatting.YELLOW + "Horizonwright navigation cancelled."
                            : EnumChatFormatting.GRAY + "Horizonwright has no active navigation request."));
            } catch (RuntimeException failure) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "Navigation cancellation failed safely: " + failure.getMessage()));
            }
            return;
        }
        if ("stop".equals(subcommand)) {
            runtime.emergencyStop("manual /hw stop command");
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Horizonwright emergency stop latched for this session."));
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
            String requestId = runtime
                .startNavigation(sender.getEntityWorld().provider.dimensionId, x, y, z, tolerance);
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + "Horizonwright submitted "
                        + requestId
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
}
