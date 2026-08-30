package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime.RuntimeSnapshot;

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
        return "/hw [panel|status|stop]";
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
                        + snapshot.getNavigationDiagnostic()));
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
}
