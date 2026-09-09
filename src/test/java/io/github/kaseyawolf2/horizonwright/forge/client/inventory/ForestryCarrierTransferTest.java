package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerClickCorrelation;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

public class ForestryCarrierTransferTest {

    private final Item bag = new Item();
    private final MinecraftSnapshots fixture = new MinecraftSnapshots();

    @Test
    public void observedEmptyBagResyncAllowsPlacementWithoutReplayingPickup() {
        ItemStack serverBag = bag(false);
        ItemStack clientBag = bag(true);
        assertTrue(
            fixture.raw.fingerprint(serverBag)
                .getDataHash()
                .startsWith("6c5d247f76f0"));
        assertTrue(
            fixture.raw.fingerprint(clientBag)
                .getDataHash()
                .startsWith("cacec84db6cc"));
        fixture.inventory.setInventorySlotContents(0, clientBag);
        ContainerSnapshot before = fixture.capture(null);
        ContainerTransaction transaction = new ContainerTransaction(
            "return-bag",
            7,
            InventoryTransferClicks.swap("return-bag", before, 0, 1, 1, 1));
        ContainerClickCorrelation correlation = new ContainerClickCorrelation(transaction);
        assertEquals(
            0,
            correlation.prepare(before, 7, 0, 100)
                .get()
                .getSlot());
        correlation.observeWrite(0, 0, 0, 0, (short) 1, 1);
        correlation.observeConfirmation(0, (short) 1, false, 2);
        fixture.inventory.setInventorySlotContents(0, null);
        ContainerSnapshot resynced = fixture.normalized.capture(fixture.container, serverBag, 1);
        assertFalse(correlation.observeSynchronizedSnapshot(resynced, 7, 3));
        correlation.observeAuthoritativeResync(0, 4);
        assertFalse(correlation.observeSynchronizedSnapshot(resynced, 7, 5));
        correlation.observeAuthoritativeCursorResync(6);
        assertTrue(correlation.observeSynchronizedSnapshot(resynced, 7, 7));
        assertEquals(
            1,
            correlation.prepare(resynced, 7, 8, 100)
                .get()
                .getSlot());
        correlation.observeWrite(0, 1, 0, 0, (short) 2, 9);
        correlation.observeConfirmation(0, (short) 2, true, 10);
        fixture.inventory.setInventorySlotContents(1, serverBag);
        assertTrue(
            correlation.observeSynchronizedSnapshot(fixture.normalized.capture(fixture.container, null, 2), 7, 11));
        assertEquals(ContainerClickCorrelation.State.COMPLETED, correlation.getState());
        assertNull(
            fixture.capture(null)
                .getCursor());
        assertTrue(
            clientBag.getTagCompound()
                .hasKey("Slots", 10));
    }

    @Test
    public void identityStoredContentsAndOtherTagsStillChangeTheCursorFingerprint() {
        ContainerSnapshot empty = fixture.capture(bag(false));
        ItemStack different = bag(true);
        different.getTagCompound()
            .setInteger("UID", 123);
        assertNotEquals(empty, fixture.capture(different));
        different = bag(true);
        NBTTagCompound cargo = new NBTTagCompound();
        cargo.setString("id", "test:ore");
        cargo.setByte("Count", (byte) 1);
        different.getTagCompound()
            .getCompoundTag("Slots")
            .setTag("0", cargo);
        assertNotEquals(empty, fixture.capture(different));
        different = bag(true);
        different.getTagCompound()
            .setString("name", "changed");
        assertNotEquals(empty, fixture.capture(different));
    }

    @Test
    public void legacyFallbackMalformedContentsAndUnidentifiedBagsArePreserved() {
        ItemStack legacy = bag(true);
        legacy.getTagCompound()
            .setTag("Items", new NBTTagList());
        assertSame(legacy, CarrierInitialization.forestryTransport(legacy));
        ItemStack malformed = bag(false);
        malformed.getTagCompound()
            .setString("Slots", "unexpected");
        assertSame(malformed, CarrierInitialization.forestryTransport(malformed));
        ItemStack unidentified = bag(true);
        unidentified.getTagCompound()
            .removeTag("UID");
        assertSame(unidentified, CarrierInitialization.forestryTransport(unidentified));
    }

    private ItemStack bag(boolean emptySlots) {
        ItemStack stack = new ItemStack(bag);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger("UID", -394236326);
        if (emptySlots) stack.getTagCompound()
            .setTag("Slots", new NBTTagCompound());
        return stack;
    }

    private static final class MinecraftSnapshots {

        final MinecraftContainerSnapshotter raw = new MinecraftContainerSnapshotter(item -> "Forestry:minerBag");
        final MinecraftContainerSnapshotter normalized = new MinecraftContainerSnapshotter(
            item -> "Forestry:minerBag",
            (slot, stack) -> stack,
            CarrierInitialization::forestryTransport);
        final InventoryBasic inventory = new InventoryBasic("player", false, 2);
        final Container container = new Container() {

            {
                addSlotToContainer(new Slot(inventory, 0, 0, 0));
                addSlotToContainer(new Slot(inventory, 1, 18, 0));
            }

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        };

        ContainerSnapshot capture(ItemStack cursor) {
            return normalized.capture(container, cursor, 0);
        }
    }
}
