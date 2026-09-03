package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * Calls inherited Minecraft methods through the class or interface that declares them.
 *
 * <p>
 * The legacy Forge reobfuscator does not reliably remap a method when bytecode names a concrete client
 * subclass as its owner. Keeping these calls here makes the production owner unambiguous and prevents
 * development names from leaking into the shipped jar.
 */
public final class MinecraftRuntimeAccess {

    private MinecraftRuntimeAccess() {}

    public static String folderName(MinecraftServer server) {
        return server.getFolderName();
    }

    public static void addChatMessage(ICommandSender sender, IChatComponent message) {
        sender.addChatMessage(message);
    }

    public static ChatStyle chatStyle(IChatComponent component) {
        return component.getChatStyle();
    }

    public static long worldTime(World world) {
        return world.getWorldTime();
    }

    public static long totalWorldTime(World world) {
        return world.getTotalWorldTime();
    }

    public static boolean blockExists(World world, int x, int y, int z) {
        return world.blockExists(x, y, z);
    }

    public static Block block(World world, int x, int y, int z) {
        return world.getBlock(x, y, z);
    }

    public static int blockMetadata(World world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z);
    }

    public static TileEntity tileEntity(World world, int x, int y, int z) {
        return world.getTileEntity(x, y, z);
    }

    public static boolean isAirBlock(World world, int x, int y, int z) {
        return world.isAirBlock(x, y, z);
    }

    public static IChunkProvider chunkProvider(World world) {
        return world.getChunkProvider();
    }

    public static MovingObjectPosition rayTraceBlocks(World world, Vec3 from, Vec3 to, boolean stopOnLiquid) {
        return world.rayTraceBlocks(from, to, stopOnLiquid);
    }

    public static List getEntitiesWithinAabb(World world, Class entityType, AxisAlignedBB box) {
        return world.getEntitiesWithinAABB(entityType, box);
    }

    public static float eyeHeight(Entity entity) {
        return entity.getEyeHeight();
    }

    public static ItemStack heldItem(EntityPlayer player) {
        return player.getHeldItem();
    }

    public static boolean isPlayerSleeping(EntityPlayer player) {
        return player.isPlayerSleeping();
    }

    public static float health(EntityLivingBase entity) {
        return entity.getHealth();
    }

    public static float maximumHealth(EntityLivingBase entity) {
        return entity.getMaxHealth();
    }

    public static boolean isSneaking(Entity entity) {
        return entity.isSneaking();
    }

    public static void setSneaking(Entity entity, boolean sneaking) {
        entity.setSneaking(sneaking);
    }

    public static UUID uniqueId(Entity entity) {
        return entity.getUniqueID();
    }

    public static String commandSenderName(ICommandSender sender) {
        return sender.getCommandSenderName();
    }

    public static void clearItemInUse(EntityPlayer player) {
        player.clearItemInUse();
    }
}
