package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * AE2 rv3-beta-1000-GTNH terminal protocol, including WirelessCraftingTerminal 1.12.16.
 * Network rows are virtual and must never be clicked as vanilla container slots. Only the live GUI's
 * server-synchronized item view is inspected; filters, power, permissions and range remain authoritative.
 */
public final class Ae2TerminalAdapter implements PortableInventoryAdapter {

    private static final String CONTAINER = "appeng.container.implementations.ContainerMEMonitorable";
    private static final String GUI = "appeng.client.gui.implementations.GuiMEMonitorable";
    private static final String ITEM_STACK = "appeng.api.storage.data.IAEItemStack";

    @Override
    public String id() {
        return "ae2-terminal";
    }

    static boolean isTerminalItem(ItemStack stack) {
        return PortableInventoryAdapters.itemIs(stack, "appeng.items.tools.powered.ToolWirelessTerminal")
            || PortableInventoryAdapters
                .itemIs(stack, "net.p455w0rd.wirelesscraftingterminal.items.ItemWirelessCraftingTerminal");
    }

    @Override
    public boolean supports(ItemStack stack) {
        return isTerminalItem(stack);
    }

    @Override
    public List<ItemStack> readContents(ItemStack stack) {
        return Collections.emptyList();
    }

    @Override
    public boolean contentsKnown(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canStore(ItemStack bag, ItemStack cargo) {
        return supports(bag) && cargo != null && cargo.stackSize > 0 && !PortableInventoryAdapters.carrierItem(cargo);
    }

    @Override
    public boolean matches(Container opened) {
        return OptionalInventoryReflection.instance(opened, CONTAINER);
    }

    @Override
    public List<Slot> storageSlots(Container opened) {
        // View cells, crafting matrices, upgrades and trash slots are not ME network storage.
        return Collections.emptyList();
    }

    @Override
    public boolean matchesCarrier(Container opened, ItemStack carrier) {
        if (!matches(opened) || !supports(carrier)) return false;
        Object target = OptionalInventoryReflection.call(opened, "getTarget");
        if (!OptionalInventoryReflection.instance(target, "appeng.helpers.WirelessTerminalGuiObject")) return false;
        Object rawParent = OptionalInventoryReflection.call(target, "getItemStack");
        if (!(rawParent instanceof ItemStack)
            || !ItemStack.areItemStacksEqual(stableCarrier((ItemStack) rawParent), stableCarrier(carrier)))
            return false;
        Object playerInventory = OptionalInventoryReflection.call(opened, "getPlayerInv");
        int inventorySlot = ((Number) OptionalInventoryReflection.call(target, "getInventorySlot")).intValue();
        return playerInventory instanceof InventoryPlayer && inventorySlot >= 0
            && inventorySlot < 36
            && ((InventoryPlayer) playerInventory).mainInventory[inventorySlot] == carrier;
    }

    @Override
    public String identity(ItemStack stack) {
        // Encryption keys identify the network, not a unique physical terminal.
        return "";
    }

    @Override
    public ItemStack stableCarrier(ItemStack stack) {
        ItemStack copy = stack == null ? null : stack.copy();
        if (copy != null && copy.hasTagCompound()) copy.getTagCompound()
            .removeTag("internalCurrentPower");
        return copy;
    }

    @Override
    public void openHeld(Minecraft mc, ItemStack stack) {
        PortableInventoryAdapters.requireHeld(mc, stack);
        if (!supports(stack)) throw new IllegalArgumentException("Not a supported wireless terminal");
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
    }

    public boolean matches(GuiScreen screen) {
        return OptionalInventoryReflection.instance(screen, GUI) && screen instanceof GuiContainer
            && matches(((GuiContainer) screen).inventorySlots);
    }

    /** True only when vanilla shift-click cannot route this physical stack to an upgrade or filter slot. */
    public boolean canDeposit(Container opened, Slot source) {
        if (!matches(opened) || source == null
            || source.getStack() == null
            || !OptionalInventoryReflection.instance(source, "appeng.container.slot.AppEngSlot")
            || !Boolean.TRUE.equals(OptionalInventoryReflection.call(source, "isPlayerSide"))) return false;
        Object inventory = OptionalInventoryReflection.call(opened, "getPlayerInv");
        if (source.inventory != inventory || source.getSlotIndex() < 0
            || source.getSlotIndex() >= 36
            || PortableInventoryAdapters.carrierItem(source.getStack())) return false;
        Object destinations = OptionalInventoryReflection.invoke(
            opened,
            "getValidDestinationSlots",
            new Class<?>[] { boolean.class, ItemStack.class },
            true,
            source.getStack());
        return destinations instanceof List<?> && ((List<?>) destinations).isEmpty()
            && OptionalInventoryReflection
                .invoke(opened, "getValidDestinationFakeSlot", new Class<?>[] { ItemStack.class }, source.getStack())
                == null;
    }

    public boolean powered(GuiScreen screen) {
        return matches(screen) && Boolean.TRUE.equals(OptionalInventoryReflection.call(repo(screen), "hasPower"));
    }

    /** Exact server-reported long count; -1 means unavailable, 0 means absent from the powered view. */
    public long availableCount(GuiScreen screen, ItemStack requested) {
        if (requested == null || !powered(screen)) return -1;
        for (Object entry : entries(screen)) {
            ItemStack available = toItem(entry);
            if (available != null && available.isItemEqual(requested)
                && ItemStack.areItemStackTagsEqual(available, requested)) return networkCount(entry);
        }
        return 0;
    }

    static long networkCount(Object entry) {
        return Math.max(0L, ((Number) OptionalInventoryReflection.call(entry, "getStackSize")).longValue());
    }

    /** Full filtered view, independent of scroll position. Craftable-only entries and fluids are excluded. */
    public List<ItemStack> readContents(GuiScreen screen) {
        if (!powered(screen)) return Collections.emptyList();
        List<ItemStack> result = new ArrayList<>();
        for (Object stack : entries(screen)) {
            ItemStack item = toItem(stack);
            if (item != null) result.add(item);
        }
        return result;
    }

    /**
     * Requests one normal stack with the native shift-click action. Does not confirm delivery: caller
     * must observe the exact player-inventory increase, hold its action lease until settled, and stop on
     * GUI replacement or timeout. Sending success is never treated as successful extraction.
     */
    public boolean requestStack(Minecraft mc, ItemStack requested) {
        if (mc == null || mc.thePlayer == null
            || requested == null
            || !powered(mc.currentScreen)
            || ((GuiContainer) mc.currentScreen).inventorySlots != mc.thePlayer.openContainer
            || mc.thePlayer.inventory.getItemStack() != null) return false;
        Object selected = null;
        for (Object entry : entries(mc.currentScreen)) {
            ItemStack available = toItem(entry);
            if (available != null && available.isItemEqual(requested)
                && ItemStack.areItemStackTagsEqual(available, requested)) {
                selected = entry;
                break;
            }
        }
        if (selected == null) return false;
        Class<?> actionType = OptionalInventoryReflection.type("appeng.helpers.MonitorableAction");
        Object shiftClick = OptionalInventoryReflection.field(actionType, "SHIFT_CLICK");
        Class<?> packetTypes = OptionalInventoryReflection.type("appeng.core.sync.AppEngPacketHandlerBase$PacketTypes");
        if (((Enum<?>) OptionalInventoryReflection.field(packetTypes, "PACKET_PARTIAL_ITEM")).ordinal() != 21
            || ((Enum<?>) OptionalInventoryReflection.field(packetTypes, "PACKET_MONITORABLE_ACTION")).ordinal() != 42
            || ((Enum<?>) shiftClick).ordinal() != 2) {
            throw new IllegalStateException("AE2 packet layout differs from the verified inventory firewall protocol");
        }
        // This native method calls AEBaseContainer.setTargetStack (which synchronizes the target first),
        // then sends PacketMonitorableAction. The old PacketInventoryAction is not this version's protocol.
        OptionalInventoryReflection.invoke(
            mc.currentScreen,
            "sendAction",
            new Class<?>[] { actionType, OptionalInventoryReflection.type("appeng.api.storage.data.IAEStack"),
                int.class },
            shiftClick,
            selected,
            -1);
        return true;
    }

    private Object repo(GuiScreen screen) {
        return OptionalInventoryReflection.field(screen, "repo");
    }

    private List<?> entries(GuiScreen screen) {
        Object repository = repo(screen);
        if (!OptionalInventoryReflection.instance(repository, "appeng.client.me.ItemRepo")) {
            throw new IllegalStateException("Unsupported AE2 terminal repository implementation");
        }
        Object view = OptionalInventoryReflection.field(repository, "view");
        if (!(view instanceof List<?>)) throw new IllegalStateException("Unsupported AE2 terminal view");
        return new ArrayList<>((List<?>) view);
    }

    private ItemStack toItem(Object stack) {
        if (!OptionalInventoryReflection.instance(stack, ITEM_STACK)) return null;
        long count = networkCount(stack);
        if (count <= 0) return null;
        Object raw = OptionalInventoryReflection.call(stack, "getItemStack");
        if (!(raw instanceof ItemStack)) return null;
        ItemStack copy = ((ItemStack) raw).copy();
        copy.stackSize = (int) Math.min(Integer.MAX_VALUE, count);
        return copy;
    }
}
