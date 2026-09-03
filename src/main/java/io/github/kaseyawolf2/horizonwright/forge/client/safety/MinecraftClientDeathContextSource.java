package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryStack;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;

/** Immutable Minecraft-thread capture used by the pre-S06 death hook. */
public final class MinecraftClientDeathContextSource implements ClientDeathContextSource {

    private final DimensionBlockPosition playerPosition;
    private final String playerIdentity;
    private final String activeTaskId;
    private final ClientInventorySnapshot inventory;

    public MinecraftClientDeathContextSource(Minecraft minecraft, HorizonwrightRuntime runtime) {
        if (minecraft == null || runtime == null) {
            throw new IllegalArgumentException("minecraft and runtime must not be null");
        }
        if (!minecraft.func_152345_ab()) {
            throw new IllegalStateException("death context must be captured on the Minecraft client thread");
        }
        EntityClientPlayerMP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null) {
            throw new IllegalStateException("a joined player and world are required for death-context capture");
        }
        playerPosition = new DimensionBlockPosition(
            minecraft.theWorld.provider.dimensionId,
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posY),
            MathHelper.floor_double(player.posZ));
        playerIdentity = MinecraftRuntimeAccess.uniqueId(player) + "@"
            + Integer.toHexString(System.identityHashCode(player));
        activeTaskId = runtime.controllerSnapshot()
            .getActiveTaskId()
            .orElse(null);
        inventory = captureInventory(player);
    }

    @Override
    public DimensionBlockPosition getPlayerPosition() {
        return playerPosition;
    }

    @Override
    public String getPlayerIdentity() {
        return playerIdentity;
    }

    @Override
    public String getActiveTaskId() {
        return activeTaskId;
    }

    @Override
    public ClientInventorySnapshot getInventorySnapshot() {
        return inventory;
    }

    private static ClientInventorySnapshot captureInventory(EntityClientPlayerMP player) {
        return captureInventory(
            player.inventory.mainInventory,
            player.inventory.armorInventory,
            player.inventory.getItemStack());
    }

    static ClientInventorySnapshot captureInventory(ItemStack[] mainInventory, ItemStack[] armorInventory,
        ItemStack carriedStack) {
        return captureInventory(mainInventory, armorInventory, carriedStack, new ItemStackSnapshotter() {

            @Override
            public InventoryStack snapshot(ItemStack stack) {
                return snapshotStack(stack);
            }
        });
    }

    static ClientInventorySnapshot captureInventory(ItemStack[] mainInventory, ItemStack[] armorInventory,
        ItemStack carriedStack, ItemStackSnapshotter snapshotter) {
        if (mainInventory == null || armorInventory == null) {
            throw new IllegalArgumentException("inventory arrays must not be null");
        }
        if (snapshotter == null) {
            throw new IllegalArgumentException("snapshotter must not be null");
        }
        List<InventoryStack> stacks = new ArrayList<InventoryStack>();
        appendStacks(stacks, mainInventory, snapshotter);
        appendStacks(stacks, armorInventory, snapshotter);
        int slotCount = mainInventory.length + armorInventory.length;
        if (carriedStack != null) {
            appendStack(stacks, carriedStack, snapshotter);
            slotCount++;
        }
        return new ClientInventorySnapshot(slotCount, stacks);
    }

    private static void appendStacks(List<InventoryStack> target, ItemStack[] source,
        ItemStackSnapshotter snapshotter) {
        for (ItemStack stack : source) {
            if (stack == null) {
                continue;
            }
            appendStack(target, stack, snapshotter);
        }
    }

    private static void appendStack(List<InventoryStack> target, ItemStack stack, ItemStackSnapshotter snapshotter) {
        target.add(snapshotter.snapshot(stack));
    }

    private static InventoryStack snapshotStack(ItemStack stack) {
        return new InventoryStack(fingerprint(stack), stack.stackSize, stack.getMaxStackSize());
    }

    static String fingerprint(ItemStack stack) {
        Object registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null) {
            throw new IllegalStateException("an inventory item has no registry identity");
        }
        NBTTagCompound tag = stack.getTagCompound();
        String nbt = tag == null ? "none" : sha256(canonicalNbt(tag));
        return registryName + "|meta=" + stack.getItemDamage() + "|nbt=" + nbt;
    }

    static String canonicalNbt(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            StringBuilder value = new StringBuilder("{");
            Set<String> keys = new TreeSet<String>(compound.func_150296_c());
            for (String key : keys) {
                value.append(key.length())
                    .append(':')
                    .append(key)
                    .append('=')
                    .append(canonicalNbt(compound.getTag(key)))
                    .append(';');
            }
            return value.append('}')
                .toString();
        }
        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            StringBuilder value = new StringBuilder("[").append(list.func_150303_d())
                .append(':');
            for (int index = 0; index < list.tagCount(); index++) {
                if (list.func_150303_d() == 10) {
                    value.append(canonicalNbt(list.getCompoundTagAt(index)));
                } else {
                    value.append(list.getStringTagAt(index));
                }
                value.append(';');
            }
            return value.append(']')
                .toString();
        }
        return tag.getId() + ":" + tag.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                encoded.append(String.format("%02x", current & 0xff));
            }
            return encoded.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    interface ItemStackSnapshotter {

        InventoryStack snapshot(ItemStack stack);
    }
}
