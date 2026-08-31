package io.github.kaseyawolf2.horizonwright.forge.client.container;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;

/** Captures an immutable, exact view of the currently synchronized client container. */
public final class MinecraftContainerSnapshotter {

    interface ItemIdentityResolver {

        String identity(Item item);
    }

    private final ItemIdentityResolver itemIdentities;

    public MinecraftContainerSnapshotter() {
        this(item -> {
            Object registryName = Item.itemRegistry.getNameForObject(item);
            return registryName == null ? null : registryName.toString();
        });
    }

    MinecraftContainerSnapshotter(ItemIdentityResolver itemIdentities) {
        if (itemIdentities == null) {
            throw new IllegalArgumentException("itemIdentities must not be null");
        }
        this.itemIdentities = itemIdentities;
    }

    public ContainerSnapshot captureCurrent(Minecraft minecraft, long revision) {
        if (minecraft == null || !minecraft.func_152345_ab()
            || minecraft.thePlayer == null
            || minecraft.thePlayer.openContainer == null) {
            throw new IllegalStateException("a joined client thread with an open container is required");
        }
        return capture(minecraft.thePlayer.openContainer, minecraft.thePlayer.inventory.getItemStack(), revision);
    }

    public ContainerSnapshot capture(Container container, ItemStack cursor, long revision) {
        if (container == null) {
            throw new IllegalArgumentException("container must not be null");
        }
        List<ItemFingerprint> slots = new ArrayList<ItemFingerprint>(container.inventorySlots.size());
        StringBuilder layout = new StringBuilder();
        for (int index = 0; index < container.inventorySlots.size(); index++) {
            Object value = container.inventorySlots.get(index);
            if (!(value instanceof Slot)) {
                throw new IllegalStateException("container slot " + index + " is not a Minecraft Slot");
            }
            Slot slot = (Slot) value;
            if (slot.slotNumber != index) {
                throw new IllegalStateException("container slot numbers are not contiguous at index " + index);
            }
            layout.append(index)
                .append(':')
                .append(
                    slot.getClass()
                        .getName())
                .append(':')
                .append(
                    slot.inventory.getClass()
                        .getName())
                .append(':')
                .append(slot.getSlotIndex())
                .append(':')
                .append(slot.xDisplayPosition)
                .append(':')
                .append(slot.yDisplayPosition)
                .append(';');
            slots.add(fingerprint(slot.getStack()));
        }
        return new ContainerSnapshot(
            container.windowId,
            container.getClass()
                .getName(),
            "slots-" + container.inventorySlots.size() + "-sha256-" + sha256(layout.toString()),
            revision,
            slots,
            fingerprint(cursor));
    }

    ItemFingerprint fingerprint(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        String registryName = itemIdentities.identity(stack.getItem());
        if (registryName == null || registryName.trim()
            .isEmpty()) {
            throw new IllegalStateException("container item has no registry identity");
        }
        NBTTagCompound tag = stack.getTagCompound();
        String dataHash = tag == null ? "none" : sha256(canonicalNbt(tag));
        return new ItemFingerprint(registryName, stack.getItemDamage(), dataHash, stack.stackSize);
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
}
