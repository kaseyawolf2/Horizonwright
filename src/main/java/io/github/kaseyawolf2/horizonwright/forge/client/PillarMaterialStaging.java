package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;

/** One guarded hotbar swap before navigation; the staged building stack remains available for later logs. */
public final class PillarMaterialStaging implements AutoCloseable {

    private final Minecraft mc;
    private final ActionSessionGuard guard;
    private final ActionLease lease;
    private final int hotbar;
    private final ItemStack expected;
    private volatile boolean dispatched;
    private int settleTicks;
    private boolean ended;

    public static PillarMaterialStaging prepare(Minecraft mc, ActionSessionGuard guard, ActionLease lease,
        int excludedSource, int excludedHotbar) {
        ItemStack[] inventory = mc.thePlayer.inventory.mainInventory;
        if (!PillarMaterialPolicy.hotbarItems(inventory)
            .isEmpty()) return null;
        int source = PillarMaterialPolicy.findSlot(inventory, excludedSource);
        if (source < 9) return null;
        int destination = stagingSlot(inventory, excludedHotbar, mc.thePlayer.inventory.currentItem);
        return new PillarMaterialStaging(mc, guard, lease, source, destination);
    }

    static int stagingSlot(ItemStack[] inventory, int protectedHotbar, int selected) {
        for (int i = 8; i >= 0; i--) if (i != protectedHotbar && i != selected && inventory[i] == null) return i;
        for (int i = 8; i >= 0; i--) if (i != protectedHotbar && i != selected) return i;
        throw new IllegalStateException("No hotbar slot available for pillar blocks");
    }

    private PillarMaterialStaging(Minecraft mc, ActionSessionGuard guard, ActionLease lease, int source, int hotbar) {
        this.mc = mc;
        this.guard = guard;
        this.lease = lease;
        this.hotbar = hotbar;
        if (!lease.isValid() || !lease.getCapabilities()
            .contains(ActionCapability.CONTAINER)
            || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
            || mc.thePlayer.inventory.getItemStack() != null)
            throw new IllegalStateException("Close other containers before staging pillar blocks");
        expected = mc.thePlayer.inventory.mainInventory[source].copy();
        guard.begin(lease);
        try {
            mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, source, hotbar, 2, mc.thePlayer);
            ActionPacketDispatch.afterPendingWrites(mc, () -> dispatched = true);
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    public boolean poll() {
        if (!lease.isValid()) throw new IllegalStateException("Pillar inventory authority changed");
        if (ended) return guard.isReadyForSession();
        if (!guard.isActiveLease(lease)) throw new IllegalStateException("Pillar inventory session changed");
        if (!dispatched || ++settleTicks < 3) return false;
        if (!ItemStack.areItemStacksEqual(expected, mc.thePlayer.inventory.mainInventory[hotbar]))
            throw new IllegalStateException("Pillar blocks were not synchronized into the hotbar");
        close();
        return false;
    }

    @Override
    public void close() {
        if (ended) return;
        ended = true;
        guard.quarantine(lease);
        guard.end(lease);
    }
}
