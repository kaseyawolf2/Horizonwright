package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/** Adapters verified against the canonical GTNH instance's Adventure 1.4.22, Forestry 4.11.31 and Backpack 2.6.13. */
public final class PortableInventoryAdapters {

    private static final String ADVENTURE_ITEM = "com.darkona.adventurebackpack.item.ItemAdventureBackpack";
    private static final String FORESTRY_ITEM = "forestry.storage.items.ItemBackpack";
    private static final String BACKPACK_ITEM = "de.eydamos.backpack.item.ItemBackpackBase";
    private final List<PortableInventoryAdapter> adapters = Collections
        .unmodifiableList(Arrays.asList(new Adventure(), new Forestry(), new Eydamos(), new Ae2TerminalAdapter()));

    public PortableInventoryAdapter find(ItemStack stack) {
        for (PortableInventoryAdapter adapter : adapters) if (adapter.supports(stack)) return adapter;
        return null;
    }

    public List<PortableInventoryAdapter> adapters() {
        return adapters;
    }

    public ItemStack transportStack(ItemStack stack) {
        return itemIs(stack, FORESTRY_ITEM) && !itemIs(stack, "forestry.storage.items.ItemBackpackNaturalist")
            ? CarrierInitialization.forestryTransport(stack)
            : stack;
    }

    public static ItemStack wornAdventureBackpack(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) return null;
        try {
            Class<?> propertyType = Class.forName("com.darkona.adventurebackpack.playerProperties.BackpackProperty");
            Object property = OptionalInventoryReflection
                .invoke(propertyType, "get", new Class<?>[] { EntityPlayer.class }, mc.thePlayer);
            if (property == null) return null;
            Object stack = OptionalInventoryReflection.call(property, "getWearable");
            return stack instanceof ItemStack && itemIs((ItemStack) stack, ADVENTURE_ITEM) ? (ItemStack) stack : null;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return null;
        }
    }

    /** Uses Adventure's native wearing GUI request. Caller owns the same capabilities as openHeld. */
    public static void openWornAdventureBackpack(Minecraft mc) {
        if (wornAdventureBackpack(mc) == null) throw new IllegalStateException("No worn Adventure Backpack");
        openAdventure((byte) 0);
    }

    private static void openAdventure(byte source) {
        try {
            Class<?> messageType = OptionalInventoryReflection
                .type("com.darkona.adventurebackpack.network.GUIPacket$GUImessage");
            IMessage message = (IMessage) messageType.getConstructor(byte.class, byte.class)
                .newInstance((byte) 1, source);
            SimpleNetworkWrapper network = (SimpleNetworkWrapper) OptionalInventoryReflection
                .field(OptionalInventoryReflection.type("com.darkona.adventurebackpack.init.ModNetwork"), "net");
            network.sendToServer(message);
        } catch (ReflectiveOperationException failure) {
            throw OptionalInventoryReflection.incompatible(failure);
        }
    }

    static boolean itemIs(ItemStack stack, String name) {
        return stack != null && OptionalInventoryReflection.instance(stack.getItem(), name);
    }

    static boolean carrierItem(ItemStack stack) {
        return itemIs(stack, ADVENTURE_ITEM) || itemIs(stack, FORESTRY_ITEM)
            || itemIs(stack, BACKPACK_ITEM)
            || Ae2TerminalAdapter.isTerminalItem(stack);
    }

    static List<ItemStack> readList(NBTTagList list, int limit) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot >= limit) continue;
            addStack(result, entry);
        }
        return result;
    }

    static void addStack(List<ItemStack> result, NBTTagCompound nbt) {
        ItemStack stack = ItemStack.loadItemStackFromNBT(nbt);
        if (stack != null && stack.stackSize > 0) result.add(stack.copy());
    }

    private abstract static class Bag implements PortableInventoryAdapter {

        @Override
        public ItemStack stableCarrier(ItemStack stack) {
            return stack == null ? null : stack.copy();
        }

        @Override
        public String identity(ItemStack stack) {
            return "";
        }

        @Override
        public void openHeld(Minecraft mc, ItemStack stack) {
            requireHeld(mc, stack);
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
        }

        boolean acceptsCargo(ItemStack bag, ItemStack cargo) {
            return supports(bag) && cargo != null && cargo.stackSize > 0 && !carrierItem(cargo);
        }
    }

    static void requireHeld(Minecraft mc, ItemStack stack) {
        if (mc == null || mc.thePlayer == null
            || mc.theWorld == null
            || mc.playerController == null
            || stack == null
            || mc.thePlayer.getHeldItem() != stack
            || mc.thePlayer.isSneaking()) {
            throw new IllegalStateException("Inventory carrier must be held by a standing player before opening");
        }
    }

    private static final class Adventure extends Bag {

        @Override
        public String id() {
            return "adventure-backpack";
        }

        @Override
        public boolean initializedCarrierMatches(ItemStack before, ItemStack after) {
            return supports(before) && supports(after) && CarrierInitialization.adventure(before, after);
        }

        @Override
        public boolean supports(ItemStack stack) {
            return itemIs(stack, ADVENTURE_ITEM);
        }

        @Override
        public boolean contentsKnown(ItemStack stack) {
            return supports(stack) && stack.hasTagCompound()
                && stack.getTagCompound()
                    .getCompoundTag("wearableData")
                    .hasKey("inventory", 9);
        }

        @Override
        public List<ItemStack> readContents(ItemStack stack) {
            if (!contentsKnown(stack)) return Collections.emptyList();
            return readList(
                stack.getTagCompound()
                    .getCompoundTag("wearableData")
                    .getTagList("inventory", 10),
                50);
        }

        @Override
        public boolean canStore(ItemStack bag, ItemStack cargo) {
            return acceptsCargo(bag, cargo) && Boolean.TRUE.equals(
                OptionalInventoryReflection.invoke(
                    OptionalInventoryReflection.type("com.darkona.adventurebackpack.inventory.SlotBackpack"),
                    "isValidItem",
                    new Class<?>[] { ItemStack.class },
                    cargo));
        }

        @Override
        public boolean matches(Container opened) {
            return OptionalInventoryReflection
                .instance(opened, "com.darkona.adventurebackpack.inventory.ContainerBackpack");
        }

        @Override
        public List<Slot> storageSlots(Container opened) {
            if (!matches(opened)) return Collections.emptyList();
            List<Slot> result = new ArrayList<>();
            for (Object raw : opened.inventorySlots) {
                Slot slot = (Slot) raw;
                String name = slot.getClass()
                    .getName();
                if (name.equals("com.darkona.adventurebackpack.inventory.SlotBackpack")
                    || name.equals("com.darkona.adventurebackpack.inventory.SlotTool")) result.add(slot);
            }
            return result;
        }

        @Override
        public boolean matchesCarrier(Container opened, ItemStack carrier) {
            if (!supports(carrier) || !matches(opened)) return false;
            Object inventory = OptionalInventoryReflection.call(opened, "getInventoryBackpack");
            if (inventory == null) return false;
            Object rawParent = OptionalInventoryReflection.call(inventory, "getParentItem");
            if (!(rawParent instanceof ItemStack)) return false;
            ItemStack parent = (ItemStack) rawParent;
            if (!initializedCarrierMatches(parent, carrier)) return false;
            // Vanilla slot synchronization may replace the ItemStack object. Bind the parent to the
            // container's explicit HOLDING/WEARING source as well as all stable carrier tags.
            Object rawPlayer = OptionalInventoryReflection.field(opened, "player");
            if (!(rawPlayer instanceof EntityPlayer)) return false;
            EntityPlayer player = (EntityPlayer) rawPlayer;
            String source = ((Enum<?>) OptionalInventoryReflection.field(opened, "source")).name();
            if ("HOLDING".equals(source)) return player.getHeldItem() == carrier;
            if (!"WEARING".equals(source)) return false;
            Object property = OptionalInventoryReflection.invoke(
                OptionalInventoryReflection.type("com.darkona.adventurebackpack.playerProperties.BackpackProperty"),
                "get",
                new Class<?>[] { EntityPlayer.class },
                player);
            return property != null && OptionalInventoryReflection.call(property, "getWearable") == carrier;
        }

        @Override
        public ItemStack stableCarrier(ItemStack stack) {
            ItemStack copy = super.stableCarrier(stack);
            if (copy != null && copy.hasTagCompound()) copy.getTagCompound()
                .getCompoundTag("wearableData")
                .removeTag("inventory");
            return copy;
        }

        @Override
        public void openHeld(Minecraft mc, ItemStack stack) {
            requireHeld(mc, stack);
            if (!supports(stack)) throw new IllegalArgumentException("Not an Adventure Backpack");
            openAdventure((byte) 1);
        }
    }

    private static final class Forestry extends Bag {

        @Override
        public String id() {
            return "forestry-backpack";
        }

        @Override
        public boolean initializedCarrierMatches(ItemStack before, ItemStack after) {
            return supports(before) && supports(after) && CarrierInitialization.forestry(before, after);
        }

        @Override
        public boolean supports(ItemStack stack) {
            // The naturalist variant pages its inventory and needs a separate page protocol.
            return itemIs(stack, FORESTRY_ITEM) && !itemIs(stack, "forestry.storage.items.ItemBackpackNaturalist");
        }

        @Override
        public boolean contentsKnown(ItemStack stack) {
            return supports(stack) && stack.hasTagCompound()
                && (stack.getTagCompound()
                    .hasKey("Slots", 10)
                    || stack.getTagCompound()
                        .hasKey("Items", 9));
        }

        @Override
        public List<ItemStack> readContents(ItemStack stack) {
            if (!contentsKnown(stack)) return Collections.emptyList();
            int count = ((Number) OptionalInventoryReflection.call(stack.getItem(), "getBackpackSize")).intValue();
            NBTTagCompound nbt = stack.getTagCompound();
            if (!nbt.hasKey("Slots", 10)) return readList(nbt.getTagList("Items", 10), count);
            List<ItemStack> result = new ArrayList<>();
            NBTTagCompound slots = nbt.getCompoundTag("Slots");
            for (int slot = 0; slot < count; slot++) {
                String key = Integer.toString(slot, 36);
                if (slots.hasKey(key, 10)) addStack(result, slots.getCompoundTag(key));
            }
            return result;
        }

        @Override
        public boolean canStore(ItemStack bag, ItemStack cargo) {
            if (!acceptsCargo(bag, cargo)) return false;
            Object definition = OptionalInventoryReflection.call(bag.getItem(), "getDefinition");
            return definition != null && Boolean.TRUE.equals(
                OptionalInventoryReflection
                    .invoke(definition, "isValidItem", new Class<?>[] { ItemStack.class }, cargo));
        }

        @Override
        public boolean matches(Container opened) {
            return opened != null && opened.getClass()
                .getName()
                .equals("forestry.storage.gui.ContainerBackpack");
        }

        @Override
        public List<Slot> storageSlots(Container opened) {
            if (!matches(opened)) return Collections.emptyList();
            Object inventory = OptionalInventoryReflection.field(opened, "inventory");
            List<Slot> result = new ArrayList<>();
            for (Object raw : opened.inventorySlots) {
                Slot slot = (Slot) raw;
                if (slot.inventory == inventory) result.add(slot);
            }
            return result;
        }

        @Override
        public boolean matchesCarrier(Container opened, ItemStack carrier) {
            if (!supports(carrier) || !matches(opened)) return false;
            Object inventory = OptionalInventoryReflection.field(opened, "inventory");
            if (inventory == null) return false;
            Object rawParent = OptionalInventoryReflection.field(inventory, "parent");
            if (!(rawParent instanceof ItemStack) || !initializedCarrierMatches((ItemStack) rawParent, carrier))
                return false;
            Object rawPlayer = OptionalInventoryReflection.call(inventory, "getPlayer");
            if (!(rawPlayer instanceof EntityPlayer)) return false;
            EntityPlayer player = (EntityPlayer) rawPlayer;
            Object location = OptionalInventoryReflection.call(inventory, "getLocation");
            if (location == null) return false;
            String type = ((Enum<?>) OptionalInventoryReflection.call(location, "type")).name();
            // Use the recorded source and typed UID. Forestry 4.11.31 writes an integer UID but its
            // native isSameItemInventory reads a string, which cannot prove an integer identity.
            if ("HELD_BY_PLAYER".equals(type) || "UNKNOWN".equals(type)) return player.getHeldItem() == carrier;
            if (!"PLAYER_INVENTORY".equals(type)) return false;
            int index = ((Number) OptionalInventoryReflection.call(location, "slotIdx")).intValue();
            return index >= 0 && index < 36 && player.inventory.mainInventory[index] == carrier;
        }

        @Override
        public String identity(ItemStack stack) {
            return CarrierInitialization.forestryIdentity(stack);
        }

        @Override
        public ItemStack stableCarrier(ItemStack stack) {
            ItemStack copy = super.stableCarrier(stack);
            if (copy != null && copy.hasTagCompound()) {
                copy.getTagCompound()
                    .removeTag("Slots");
                copy.getTagCompound()
                    .removeTag("Items");
            }
            return copy;
        }
    }

    private static final class Eydamos extends Bag {

        @Override
        public String id() {
            return "eydamos-backpack";
        }

        @Override
        public boolean supports(ItemStack stack) {
            return itemIs(stack, BACKPACK_ITEM);
        }

        @Override
        public boolean contentsKnown(ItemStack stack) {
            // The item only contains backpack-UID; authoritative contents live in the server's save file.
            return false;
        }

        @Override
        public List<ItemStack> readContents(ItemStack stack) {
            return Collections.emptyList();
        }

        @Override
        public boolean canStore(ItemStack bag, ItemStack cargo) {
            if (!acceptsCargo(bag, cargo)) return false;
            try {
                Class<?> slotType = OptionalInventoryReflection.type("de.eydamos.backpack.inventory.slot.SlotBackpack");
                Slot slot = (Slot) slotType.getConstructor(IInventory.class, int.class, int.class, int.class)
                    .newInstance(null, 0, 0, 0);
                return slot.isItemValid(cargo);
            } catch (ReflectiveOperationException failure) {
                throw OptionalInventoryReflection.incompatible(failure);
            }
        }

        @Override
        public boolean matches(Container opened) {
            if (opened == null) return false;
            String name = opened.getClass()
                .getName();
            // Both use exactly identifiable SlotBackpack slots; pickup/personal/phantom containers are excluded.
            return name.equals("de.eydamos.backpack.inventory.container.ContainerAdvanced")
                || name.equals("de.eydamos.backpack.inventory.container.ContainerWorkbenchBackpack");
        }

        @Override
        public List<Slot> storageSlots(Container opened) {
            if (!matches(opened)) return Collections.emptyList();
            List<Slot> result = new ArrayList<>();
            for (Object raw : opened.inventorySlots) {
                Slot slot = (Slot) raw;
                if (slot.getClass()
                    .getName()
                    .equals("de.eydamos.backpack.inventory.slot.SlotBackpack")) result.add(slot);
            }
            return result;
        }

        @Override
        public boolean matchesCarrier(Container opened, ItemStack carrier) {
            if (!supports(carrier) || !matches(opened)) return false;
            String identity = identity(carrier);
            Object save = OptionalInventoryReflection.call(opened, "getBackpackSave");
            return !identity.isEmpty() && save != null
                && identity.equals(OptionalInventoryReflection.call(save, "getUUID"));
        }

        @Override
        public String identity(ItemStack stack) {
            return stack != null && stack.hasTagCompound() ? stack.getTagCompound()
                .getString("backpack-UID") : "";
        }
    }
}
