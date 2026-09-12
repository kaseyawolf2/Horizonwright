package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.util.EnumChatFormatting;

import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;

/** Routine service handoffs are informational; actionable failures retain error colors. */
final class TaskNoticeFormatting {

    private TaskNoticeFormatting() {}

    static EnumChatFormatting color(BlockedReason reason) {
        switch (reason.getCause()) {
            case SAFETY_FAILURE:
            case EXTERNAL_FAILURE:
            case INVALID_CONFIGURATION:
            case RETRY_EXHAUSTED:
            case UNSAFE_TO_CONTINUE:
                return EnumChatFormatting.RED;
            default:
                return "verified unloading".equals(reason.getMissingRequirement()) ? EnumChatFormatting.AQUA
                    : EnumChatFormatting.YELLOW;
        }
    }
}
