package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;

public class InventoryPreparationTrackerTest {

    private final Map<Item, String> ids = new IdentityHashMap<>();
    private final MinecraftContainerSnapshotter fingerprints = new MinecraftContainerSnapshotter(ids::get);
    private final Item cargo = item("test:ore");
    private final Item bag = item("test:bag");
    private final Item seeds = register(new ItemSeeds(Blocks.wheat, Blocks.farmland), "test:seeds");
    private final TaskInventoryPolicy policy = new TaskInventoryPolicy(
        new TaskSpec("farm", FarmTask.TYPE, "Farm", TaskLane.CHORE, Collections.emptyMap()),
        new ProfileEnvelope(
            0,
            new WorldProfileIdentity("test", "Test", "local", "world", 0),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()),
        fingerprints,
        ignored -> null);

    @Test
    public void pickupsStackBoundariesAndElapsedTimeDoNotReopenBagsWithSpace() {
        ItemStack[] main = inventory();
        InventoryPreparationTracker tracker = tracker(main);
        for (int count = 1; count <= 64; count++) {
            main[1] = new ItemStack(cargo, count);
            assertFalse(tracker.needsPreparation(observe(main), count * 200));
        }
        main[2] = new ItemStack(cargo, 1, 7);
        assertFalse(tracker.needsPreparation(observe(main), 20000));
    }

    @Test
    public void sortingAndAutomaticBagPickupDoNotTriggerPreparation() {
        ItemStack[] main = inventory();
        main[1] = new ItemStack(seeds, 64);
        InventoryPreparationTracker tracker = tracker(main);
        main[8] = main[0];
        main[0] = main[1];
        main[1] = null;
        main[8].setTagCompound(new NBTTagCompound());
        main[8].getTagCompound()
            .setInteger("automatic-pickup-content", 64);
        assertFalse(tracker.needsPreparation(observe(main), 400));
    }

    @Test
    public void suppliesRefillOnlyAtLowWaterAndEmptyTransitions() {
        ItemStack[] main = inventory();
        main[1] = new ItemStack(seeds, 64);
        InventoryPreparationTracker tracker = tracker(main);
        for (int count = 63; count > 16; count--) {
            main[1].stackSize = count;
            assertFalse(tracker.needsPreparation(observe(main), 100));
        }
        main[1].stackSize = 16;
        assertTrue(tracker.needsPreparation(observe(main), 101));
        tracker = tracker(main); // The bag cannot provide more seeds.
        for (int count = 15; count > 0; count--) {
            main[1].stackSize = count;
            assertFalse(tracker.needsPreparation(observe(main), 200));
        }
        main[1] = null;
        assertTrue(tracker.needsPreparation(observe(main), 201));
    }

    @Test
    public void pickedUpSuppliesCanLaterTriggerARefill() {
        ItemStack[] main = inventory();
        InventoryPreparationTracker tracker = tracker(main);
        main[1] = new ItemStack(seeds, 64);
        assertFalse(tracker.needsPreparation(observe(main), 1));
        main[1].stackSize = 16;
        assertTrue(tracker.needsPreparation(observe(main), 2));
    }

    @Test
    public void toolWearDoesNotRecheckButBrokenOrMissingWorkingToolDoes() {
        Item hoe = item("test:hoe").setMaxStackSize(1)
            .setMaxDamage(100);
        hoe.setHarvestLevel("hoe", 1);
        ItemStack[] main = inventory();
        main[1] = new ItemStack(hoe);
        InventoryPreparationTracker tracker = tracker(main);
        main[1].setItemDamage(50);
        assertFalse(tracker.needsPreparation(observe(main), 1));
        main[1].setTagCompound(new NBTTagCompound());
        NBTTagCompound tool = new NBTTagCompound();
        tool.setBoolean("Broken", true);
        main[1].getTagCompound()
            .setTag("InfiTool", tool);
        assertTrue(tracker.needsPreparation(observe(main), 2));
    }

    @Test
    public void tightInventoryBatchesPickupsInsteadOfRetryingEveryItem() {
        ItemStack[] main = inventory();
        for (int slot = 1; slot < 31; slot++) main[slot] = new ItemStack(cargo, 1);
        InventoryPreparationTracker tracker = tracker(main);
        main[31] = new ItemStack(cargo, 1);
        assertTrue(tracker.needsPreparation(observe(main), 1)); // Four free slots.
        tracker = tracker(main); // Full bag made no progress.
        main[31].stackSize = 2;
        assertFalse(tracker.needsPreparation(observe(main), 200));
        main[31].stackSize = 33;
        assertFalse(tracker.needsPreparation(observe(main), 99));
        assertTrue(tracker.needsPreparation(observe(main), 100));
    }

    @Test
    public void lastFreeSlotAndNewCarrierStillTriggerPreparation() {
        ItemStack[] main = inventory();
        for (int slot = 1; slot < 35; slot++) main[slot] = new ItemStack(cargo, 1);
        InventoryPreparationTracker tracker = tracker(main);
        main[35] = new ItemStack(cargo, 1);
        assertTrue(tracker.needsPreparation(observe(main), 1));
        main = inventory();
        tracker = tracker(main);
        main[1] = new ItemStack(bag);
        assertTrue(tracker.needsPreparation(observe(main), 1));
    }

    private ItemStack[] inventory() {
        ItemStack[] main = new ItemStack[36];
        main[0] = new ItemStack(bag);
        return main;
    }

    private InventoryPreparationTracker tracker(ItemStack[] main) {
        return new InventoryPreparationTracker(observe(main), 0);
    }

    private InventoryPreparationTracker.Snapshot observe(ItemStack[] main) {
        return InventoryPreparationTracker
            .observe(main, null, policy, fingerprints, stack -> stack.getItem() == bag ? "bag-id" : null);
    }

    private Item item(String id) {
        return register(new Item(), id);
    }

    private Item register(Item item, String id) {
        ids.put(item, id);
        return item;
    }
}
