package io.github.kaseyawolf2.horizonwright.forge.client.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;

public class MinecraftContainerSnapshotterTest {

    private static final Item TEST_ITEM = new Item();
    private static final MinecraftContainerSnapshotter SNAPSHOTS = new MinecraftContainerSnapshotter(
        item -> item == TEST_ITEM ? "horizonwright:test_snapshot_item" : null);

    @Test
    public void capturesRegistryMetadataNbtCountCursorAndStableLayout() {
        InventoryBasic inventory = new InventoryBasic("test", false, 2);
        ItemStack first = stackWithOrderedNbt(false);
        inventory.setInventorySlotContents(0, first);
        TestContainer container = new TestContainer(inventory, 10);
        container.windowId = 7;
        ItemStack cursor = new ItemStack(TEST_ITEM, 3, 0);

        ContainerSnapshot snapshot = SNAPSHOTS.capture(container, cursor, 42L);

        assertEquals(7, snapshot.getWindowId());
        assertEquals(42L, snapshot.getRevision());
        assertEquals(
            2,
            snapshot.getSlots()
                .size());
        assertEquals(
            "horizonwright:test_snapshot_item",
            snapshot.getSlots()
                .get(0)
                .getItemId());
        assertEquals(
            2,
            snapshot.getSlots()
                .get(0)
                .getCount());
        assertEquals(
            3,
            snapshot.getCursor()
                .getCount());
        assertEquals(
            SNAPSHOTS.fingerprint(stackWithOrderedNbt(false)),
            SNAPSHOTS.fingerprint(stackWithOrderedNbt(true)));

        TestContainer movedLayout = new TestContainer(inventory, 11);
        movedLayout.windowId = 7;
        ContainerSnapshot moved = SNAPSHOTS.capture(movedLayout, cursor, 42L);
        assertNotEquals(snapshot.getSlotLayout(), moved.getSlotLayout());
    }

    private static ItemStack stackWithOrderedNbt(boolean reverse) {
        ItemStack stack = new ItemStack(TEST_ITEM, 2, 0);
        NBTTagCompound tag = new NBTTagCompound();
        if (reverse) {
            tag.setInteger("beta", 2);
            tag.setString("alpha", "one");
        } else {
            tag.setString("alpha", "one");
            tag.setInteger("beta", 2);
        }
        stack.setTagCompound(tag);
        return stack;
    }

    private static final class TestContainer extends Container {

        private TestContainer(InventoryBasic inventory, int x) {
            addSlotToContainer(new Slot(inventory, 0, x, 10));
            addSlotToContainer(new Slot(inventory, 1, x + 18, 10));
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}
