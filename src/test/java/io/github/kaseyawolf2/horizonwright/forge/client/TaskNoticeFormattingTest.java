package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.*;

import net.minecraft.util.EnumChatFormatting;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.task.BlockedCause;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;

public class TaskNoticeFormattingTest {

    @Test
    public void routineUnloadingIsInformationalWhileFailuresRemainRed() {
        assertEquals(
            EnumChatFormatting.AQUA,
            TaskNoticeFormatting.color(BlockedReason.missingRequirement("Unload", "quarry", "verified unloading", "")));
        assertEquals(
            EnumChatFormatting.YELLOW,
            TaskNoticeFormatting.color(BlockedReason.missingRequirement("Refill", "quarry", "cobblestone", "")));
        assertEquals(
            EnumChatFormatting.RED,
            TaskNoticeFormatting.color(new BlockedReason(BlockedCause.SAFETY_FAILURE, "Firewall", "", 0, "", "")));
        assertEquals(
            EnumChatFormatting.RED,
            TaskNoticeFormatting.color(
                new BlockedReason(BlockedCause.RETRY_EXHAUSTED, "Cannot unload", "", 3, "verified unloading", "")));
    }
}
