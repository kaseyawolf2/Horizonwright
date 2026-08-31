package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.repair.TinkersRepairContainerInspection.Status;

/** Reflection-isolated adapter for TConstruct 1.14.93-GTNH Tool Station and Tool Forge layouts. */
public final class TinkersRepairContainerAdapter {

    static final String TOOL_STATION_CONTAINER = "tconstruct.tools.inventory.ToolStationContainer";
    static final String TOOL_FORGE_CONTAINER = "tconstruct.tools.inventory.ToolForgeContainer";
    private static final int PLAYER_SLOT_COUNT = 36;

    public TinkersRepairContainerInspection inspect(Container container, InventoryPlayer playerInventory,
        int reservedInventorySlot) {
        if (container == null || playerInventory == null
            || reservedInventorySlot < 0
            || reservedInventorySlot >= PLAYER_SLOT_COUNT) {
            throw new IllegalArgumentException("container, player inventory, and reserved slot are required");
        }
        Layout layout = layoutFor(
            container.getClass()
                .getName());
        if (layout == null) {
            return TinkersRepairContainerInspection.rejected(
                Status.NOT_TINKERS_REPAIR_CONTAINER,
                "open container is not the pinned TConstruct Tool Station or Tool Forge");
        }
        try {
            validateLayout(container, playerInventory, layout);
            Slot toolSlot = slot(container, 0);
            ItemStack tool = toolSlot.getStack();
            if (tool == null) {
                throw new IllegalStateException("recognized repair container has no tool in semantic slot 0");
            }
            RepairToolSnapshot toolEvidence = readTool(tool, reservedInventorySlot);
            List<ItemFingerprint> materials = new ArrayList<>();
            for (int index = 1; index < layout.stationSlotCount; index++) {
                ItemStack material = slot(container, index).getStack();
                materials.add(material == null ? null : fingerprint(material));
            }
            int reservedContainerSlot = containerSlotForPlayerInventory(layout.stationSlotCount, reservedInventorySlot);
            return TinkersRepairContainerInspection.recognized(
                new TinkersRepairContainerEvidence(
                    layout.kind,
                    container.windowId,
                    layout.stationSlotCount,
                    reservedContainerSlot,
                    toolEvidence,
                    materials));
        } catch (RuntimeException failure) {
            return TinkersRepairContainerInspection.rejected(
                Status.INVALID_LAYOUT_OR_EVIDENCE,
                failure.getClass()
                    .getSimpleName() + ": "
                    + failure.getMessage());
        }
    }

    static Layout layoutFor(String className) {
        if (TOOL_STATION_CONTAINER.equals(className)) {
            return new Layout(TinkersStationKind.TOOL_STATION, 4);
        }
        if (TOOL_FORGE_CONTAINER.equals(className)) {
            return new Layout(TinkersStationKind.TOOL_FORGE, 5);
        }
        return null;
    }

    static int containerSlotForPlayerInventory(int stationSlotCount, int playerInventorySlot) {
        if ((stationSlotCount != 4 && stationSlotCount != 5) || playerInventorySlot < 0
            || playerInventorySlot >= PLAYER_SLOT_COUNT) {
            throw new IllegalArgumentException("unsupported station slot count or player inventory slot");
        }
        return playerInventorySlot < 9 ? stationSlotCount + 27 + playerInventorySlot
            : stationSlotCount + playerInventorySlot - 9;
    }

    static RepairToolSnapshot readTool(ItemStack stack, int reservedInventorySlot, String registryIdentity) {
        if (stack == null || registryIdentity == null
            || registryIdentity.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("tool stack and registry identity are required");
        }
        String baseTagName = baseTagName(stack.getItem());
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(baseTagName, 10)) {
            throw new IllegalStateException("modifiable tool lacks its " + baseTagName + " compound");
        }
        NBTTagCompound base = tag.getCompoundTag(baseTagName);
        if (!base.hasKey("Damage", 3) || !base.hasKey("TotalDurability", 3)) {
            throw new IllegalStateException("modifiable tool lacks integer Damage or TotalDurability evidence");
        }
        int damage = base.getInteger("Damage");
        int maximumDamage = base.getInteger("TotalDurability");
        NBTTagCompound stableTag = (NBTTagCompound) tag.copy();
        stableTag.getCompoundTag(baseTagName)
            .removeTag("Damage");
        String stableIdentity = registryIdentity.trim() + "|meta="
            + stack.getItemDamage()
            + "|stableNbt="
            + sha256(canonicalNbt(stableTag));
        return new RepairToolSnapshot(stableIdentity, damage, maximumDamage, reservedInventorySlot);
    }

    private static RepairToolSnapshot readTool(ItemStack stack, int reservedInventorySlot) {
        Object registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null) {
            throw new IllegalStateException("Tinkers tool has no item registry identity");
        }
        return readTool(stack, reservedInventorySlot, registryName.toString());
    }

    private static void validateLayout(Container container, InventoryPlayer playerInventory, Layout layout) {
        int expected = layout.stationSlotCount + PLAYER_SLOT_COUNT;
        if (container.inventorySlots.size() != expected) {
            throw new IllegalStateException(
                "pinned " + layout.kind + " layout requires " + expected + " container slots");
        }
        for (int index = 0; index < expected; index++) {
            Slot slot = slot(container, index);
            if (slot.slotNumber != index) {
                throw new IllegalStateException("container slot numbering is not contiguous at " + index);
            }
        }
        for (int playerSlot = 0; playerSlot < PLAYER_SLOT_COUNT; playerSlot++) {
            int containerSlot = containerSlotForPlayerInventory(layout.stationSlotCount, playerSlot);
            if (!slot(container, containerSlot).isSlotInInventory(playerInventory, playerSlot)) {
                throw new IllegalStateException("player inventory mapping changed at slot " + playerSlot);
            }
        }
    }

    private static Slot slot(Container container, int index) {
        Object value = container.inventorySlots.get(index);
        if (!(value instanceof Slot)) {
            throw new IllegalStateException("container slot " + index + " is not a Minecraft Slot");
        }
        return (Slot) value;
    }

    private static String baseTagName(Item item) {
        try {
            Method method = item.getClass()
                .getMethod("getBaseTagName");
            Object result = method.invoke(item);
            if (!(result instanceof String) || ((String) result).trim()
                .isEmpty()) {
                throw new IllegalStateException("IModifyable returned no base tag name");
            }
            return ((String) result).trim();
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("repair item does not expose IModifyable.getBaseTagName", failure);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("could not read IModifyable.getBaseTagName", failure);
        }
    }

    private static ItemFingerprint fingerprint(ItemStack stack) {
        Object registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null) {
            throw new IllegalStateException("repair material has no item registry identity");
        }
        String hash = stack.getTagCompound() == null ? "none" : sha256(canonicalNbt(stack.getTagCompound()));
        return new ItemFingerprint(registryName.toString(), stack.getItemDamage(), hash, stack.stackSize);
    }

    private static String canonicalNbt(NBTBase tag) {
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
                value
                    .append(
                        list.func_150303_d() == 10 ? canonicalNbt(list.getCompoundTagAt(index))
                            : list.getStringTagAt(index))
                    .append(';');
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

    static final class Layout {

        private final TinkersStationKind kind;
        private final int stationSlotCount;

        private Layout(TinkersStationKind kind, int stationSlotCount) {
            this.kind = kind;
            this.stationSlotCount = stationSlotCount;
        }

        TinkersStationKind getKind() {
            return kind;
        }

        int getStationSlotCount() {
            return stationSlotCount;
        }
    }
}
