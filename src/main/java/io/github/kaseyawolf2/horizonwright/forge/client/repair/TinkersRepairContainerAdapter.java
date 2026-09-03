package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import java.lang.reflect.Field;
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
import net.minecraft.tileentity.TileEntity;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.repair.TinkersRepairContainerInspection.Status;

/** Reflection-isolated adapter for the pinned TConstruct repair-capable layouts. */
public final class TinkersRepairContainerAdapter {

    static final String TOOL_STATION_CONTAINER = "tconstruct.tools.inventory.ToolStationContainer";
    static final String TOOL_FORGE_CONTAINER = "tconstruct.tools.inventory.ToolForgeContainer";
    static final String CRAFTING_STATION_CONTAINER = "tconstruct.tools.inventory.CraftingStationContainer";
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
                "open container is not a pinned TConstruct repair container");
        }
        try {
            validateLayout(container, playerInventory, layout);
            ItemStack input = slot(container, layout.inputSlot).getStack();
            if (input == null) {
                throw new IllegalStateException("recognized repair container has no input tool in semantic slot 1");
            }
            RepairToolSnapshot toolEvidence = readTool(input, reservedInventorySlot);
            List<ItemFingerprint> materials = new ArrayList<>();
            List<ItemStack> materialStacks = new ArrayList<>();
            for (int index : layout.materialSlots) {
                ItemStack material = slot(container, index).getStack();
                materials.add(material == null ? null : fingerprint(material));
                materialStacks.add(material);
            }
            ItemStack finalizedOutput = finalizedOutput(slot(container, layout.outputSlot).getStack(), materialStacks);
            RepairToolSnapshot predictedOutput = finalizedOutput == null ? null
                : readTool(finalizedOutput, reservedInventorySlot);
            int predictedMaterialConsumed = finalizedOutput == null ? 0
                : predictedMaterialConsumed(slot(container, layout.outputSlot).getStack(), materialStacks);
            int reservedContainerSlot = containerSlotForPlayerInventory(layout, reservedInventorySlot);
            return TinkersRepairContainerInspection.recognized(
                new TinkersRepairContainerEvidence(
                    layout.kind,
                    container.windowId,
                    layout.stationSlotCount,
                    reservedContainerSlot,
                    toolEvidence,
                    predictedOutput,
                    predictedMaterialConsumed,
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
            return Layout.station(TinkersStationKind.TOOL_STATION, 4);
        }
        if (TOOL_FORGE_CONTAINER.equals(className)) {
            return Layout.station(TinkersStationKind.TOOL_FORGE, 5);
        }
        if (CRAFTING_STATION_CONTAINER.equals(className)) {
            return new Layout(TinkersStationKind.TINKER_TABLE, 10, 0, 5, new int[] { 1, 2, 3, 4, 6, 7, 8, 9 }, 10, 46);
        }
        return null;
    }

    static Layout layoutForStationSlotCount(int stationSlotCount) {
        if (stationSlotCount == 4) return Layout.station(TinkersStationKind.TOOL_STATION, 4);
        if (stationSlotCount == 5) return Layout.station(TinkersStationKind.TOOL_FORGE, 5);
        if (stationSlotCount == 10) return layoutFor(CRAFTING_STATION_CONTAINER);
        return null;
    }

    static boolean belongsToTile(Container container, TileEntity tile) {
        if (container == null || tile == null) return false;
        Layout layout = layoutFor(
            container.getClass()
                .getName());
        if (layout == null) return false;
        if (layout.kind != TinkersStationKind.TINKER_TABLE) {
            return !container.inventorySlots.isEmpty() && slot(container, 0).inventory == tile;
        }
        try {
            Field logic = container.getClass()
                .getField("logic");
            return logic.get(container) == tile;
        } catch (NoSuchFieldException | IllegalAccessException failure) {
            throw new IllegalStateException("could not bind the open Tinker Table to its saved block", failure);
        }
    }

    static int containerSlotForPlayerInventory(int stationSlotCount, int playerInventorySlot) {
        if ((stationSlotCount != 4 && stationSlotCount != 5 && stationSlotCount != 10) || playerInventorySlot < 0
            || playerInventorySlot >= PLAYER_SLOT_COUNT) {
            throw new IllegalArgumentException("unsupported station slot count or player inventory slot");
        }
        return playerInventorySlot < 9 ? stationSlotCount + 27 + playerInventorySlot
            : stationSlotCount + playerInventorySlot - 9;
    }

    static int containerSlotForPlayerInventory(Layout layout, int playerInventorySlot) {
        if (layout == null || playerInventorySlot < 0 || playerInventorySlot >= PLAYER_SLOT_COUNT) {
            throw new IllegalArgumentException("layout and player inventory slot are required");
        }
        return playerInventorySlot < 9 ? layout.playerSlotStart + 27 + playerInventorySlot
            : layout.playerSlotStart + playerInventorySlot - 9;
    }

    static Layout requirePinnedLayout(Container container, InventoryPlayer playerInventory) {
        if (container == null || playerInventory == null) {
            throw new IllegalArgumentException("container and playerInventory are required");
        }
        Layout layout = layoutFor(
            container.getClass()
                .getName());
        if (layout == null) {
            throw new IllegalStateException("open container is not a pinned TConstruct repair container");
        }
        validateLayout(container, playerInventory, layout);
        return layout;
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
        NBTTagCompound stableBase = stableTag.getCompoundTag(baseTagName);
        // These are the exact fields ModRepair intentionally mutates. They describe repair state,
        // not the constructed tool's identity; material parts, modifiers, and durability remain bound.
        stableBase.removeTag("Damage");
        stableBase.removeTag("RepairCount");
        stableBase.removeTag("Broken");
        stableBase.removeTag("ToRemove");
        String stableIdentity = registryIdentity.trim() + "|meta="
            + stack.getItemDamage()
            + "|stableNbt="
            + sha256(canonicalNbt(stableTag));
        return new RepairToolSnapshot(stableIdentity, damage, maximumDamage, reservedInventorySlot);
    }

    static ItemStack finalizedOutput(ItemStack preview, List<ItemStack> materials) {
        if (preview == null) return null;
        predictedMaterialConsumed(preview, materials);
        ItemStack output = preview.copy();
        String baseTagName = baseTagName(output.getItem());
        output.getTagCompound()
            .getCompoundTag(baseTagName)
            .removeTag("ToRemove");
        return output;
    }

    static int predictedMaterialConsumed(ItemStack preview, List<ItemStack> materials) {
        int total = 0;
        for (Integer amount : predictedMaterialRemovals(preview, materials)) {
            total = Math.addExact(total, amount);
        }
        return total;
    }

    static List<Integer> predictedMaterialRemovals(ItemStack preview, List<ItemStack> materials) {
        List<Integer> result = new ArrayList<>();
        if (materials == null) throw new IllegalArgumentException("materials must not be null");
        if (preview == null) {
            for (int index = 0; index < materials.size(); index++) result.add(0);
            return result;
        }
        if (allEmpty(materials)) {
            for (int index = 0; index < materials.size(); index++) result.add(0);
            return result;
        }
        String baseTagName = baseTagName(preview.getItem());
        NBTTagCompound tag = preview.getTagCompound();
        if (tag == null || !tag.hasKey(baseTagName, 10)) {
            throw new IllegalStateException("repair preview lacks its " + baseTagName + " compound");
        }
        NBTTagCompound base = tag.getCompoundTag(baseTagName);
        int[] removals = base.hasKey("ToRemove") ? base.getIntArray("ToRemove") : null;
        int removalIndex = 0;
        for (ItemStack material : materials) {
            if (material == null) {
                result.add(0);
                continue;
            }
            int amount = removals == null || removalIndex >= removals.length ? 1 : removals[removalIndex++];
            if (amount <= 0 || amount > material.stackSize) {
                throw new IllegalStateException("repair preview requests an invalid material count");
            }
            result.add(amount);
        }
        return result;
    }

    private static boolean allEmpty(List<ItemStack> materials) {
        for (ItemStack material : materials) if (material != null) return false;
        return true;
    }

    static RepairToolSnapshot readTool(ItemStack stack, int reservedInventorySlot) {
        Object registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null) {
            throw new IllegalStateException("Tinkers tool has no item registry identity");
        }
        return readTool(stack, reservedInventorySlot, registryName.toString());
    }

    private static void validateLayout(Container container, InventoryPlayer playerInventory, Layout layout) {
        int expectedMinimum = layout.playerSlotStart + PLAYER_SLOT_COUNT;
        if (container.inventorySlots.size() < expectedMinimum
            || layout.chestSlotStart < 0 && container.inventorySlots.size() != expectedMinimum) {
            throw new IllegalStateException("pinned " + layout.kind + " layout has an unexpected container slot count");
        }
        for (int index = 0; index < container.inventorySlots.size(); index++) {
            Slot slot = slot(container, index);
            if (slot.slotNumber != index) {
                throw new IllegalStateException("container slot numbering is not contiguous at " + index);
            }
        }
        for (int playerSlot = 0; playerSlot < PLAYER_SLOT_COUNT; playerSlot++) {
            int containerSlot = containerSlotForPlayerInventory(layout, playerSlot);
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
        private final int outputSlot;
        private final int inputSlot;
        private final int[] materialSlots;
        private final int playerSlotStart;
        private final int chestSlotStart;

        private Layout(TinkersStationKind kind, int stationSlotCount, int outputSlot, int inputSlot,
            int[] materialSlots, int playerSlotStart, int chestSlotStart) {
            this.kind = kind;
            this.stationSlotCount = stationSlotCount;
            this.outputSlot = outputSlot;
            this.inputSlot = inputSlot;
            this.materialSlots = materialSlots;
            this.playerSlotStart = playerSlotStart;
            this.chestSlotStart = chestSlotStart;
        }

        private static Layout station(TinkersStationKind kind, int stationSlotCount) {
            int[] materials = new int[stationSlotCount - 2];
            for (int index = 0; index < materials.length; index++) materials[index] = index + 2;
            return new Layout(kind, stationSlotCount, 0, 1, materials, stationSlotCount, -1);
        }

        TinkersStationKind getKind() {
            return kind;
        }

        int getStationSlotCount() {
            return stationSlotCount;
        }

        int getOutputSlot() {
            return outputSlot;
        }

        int getInputSlot() {
            return inputSlot;
        }

        int[] getMaterialSlots() {
            return materialSlots.clone();
        }

        int getChestSlotStart() {
            return chestSlotStart;
        }
    }
}
