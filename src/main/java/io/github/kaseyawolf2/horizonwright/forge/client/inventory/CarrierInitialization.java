package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Exact, non-mutating comparisons for native first-open serialization; no arbitrary NBT migration. */
final class CarrierInitialization {

    private CarrierInitialization() {}

    static ItemStack forestryTransport(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return stack;
        NBTTagCompound tag = stack.getTagCompound();
        // ItemInventory.markDirty writes Slots={} while synchronizing an empty GUI on the client.
        // The server may retain no Slots tag. Do not erase legacy fallback contents or nonempty data.
        if (!tag.hasKey("Slots", 10) || !tag.getCompoundTag("Slots")
            .hasNoTags() || tag.hasKey("Items") || forestryIdentity(stack).isEmpty()) return stack;
        ItemStack copy = stack.copy();
        copy.getTagCompound()
            .removeTag("Slots");
        return copy;
    }

    static boolean forestry(ItemStack before, ItemStack after) {
        if (!sameItem(before, after)) return false;
        NBTTagCompound previous = root(before);
        NBTTagCompound current = root(after);
        stripForestryContents(previous);
        stripForestryContents(current);
        if (previous.equals(current)) return true;
        // 4.11.31 ItemInventory.setUID uses setInteger(Random.nextInt()). Existing UID of any
        // type must remain unchanged; empty/malformed existing values are not uninitialized.
        if (previous.hasKey("UID") || !current.hasKey("UID", 3)) return false;
        current.removeTag("UID");
        return previous.equals(current);
    }

    static String forestryIdentity(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return "";
        NBTTagCompound root = stack.getTagCompound();
        if (root.hasKey("UID", 3)) return "int:" + root.getInteger("UID");
        return root.hasKey("UID", 8) ? root.getString("UID") : "";
    }

    static boolean adventure(ItemStack before, ItemStack after) {
        if (!sameItem(before, after)) return false;
        NBTTagCompound previous = root(before);
        NBTTagCompound current = root(after);
        // Legacy backpackData conversion can carry tanks and variant identity. It must be done by
        // the mod and observed before automation, rather than accepting an unbounded migration here.
        if (previous.hasKey("backpackData")) return false;
        if (previous.hasKey("wearableData") && !previous.hasKey("wearableData", 10)
            || current.hasKey("wearableData") && !current.hasKey("wearableData", 10)) return false;
        NBTTagCompound oldWearable = previous.getCompoundTag("wearableData");
        NBTTagCompound newWearable = current.getCompoundTag("wearableData");
        oldWearable.removeTag("inventory");
        newWearable.removeTag("inventory");
        NBTTagCompound defaults = new NBTTagCompound();
        defaults.setByte("type", (byte) 0);
        defaults.setBoolean("disableCycling", false);
        defaults.setBoolean("disableNVision", false);
        defaults.setInteger("lastTime", 0);
        defaults.setTag("extendedProperties", new NBTTagCompound());
        NBTTagCompound emptyTank = new NBTTagCompound();
        emptyTank.setString("Empty", "");
        defaults.setTag("leftTank", emptyTank);
        defaults.setTag("rightTank", emptyTank.copy());
        for (Object raw : defaults.func_150296_c()) {
            String key = (String) raw;
            if (!oldWearable.hasKey(key) && defaults.getTag(key)
                .equals(newWearable.getTag(key))) {
                newWearable.removeTag(key);
            }
        }
        // A missing wearable compound may be created by getOrCreateWearableCompound even before
        // its first full save. Only an empty remainder can be collapsed back to absent.
        if (!previous.hasKey("wearableData") && newWearable.hasNoTags()) current.removeTag("wearableData");
        return previous.equals(current);
    }

    private static boolean sameItem(ItemStack before, ItemStack after) {
        return before != null && after != null
            && before.getItem() == after.getItem()
            && before.getItemDamage() == after.getItemDamage()
            && before.stackSize == after.stackSize;
    }

    private static NBTTagCompound root(ItemStack stack) {
        return stack.hasTagCompound() ? (NBTTagCompound) stack.getTagCompound()
            .copy() : new NBTTagCompound();
    }

    private static void stripForestryContents(NBTTagCompound root) {
        root.removeTag("Slots");
        root.removeTag("Items");
    }
}
