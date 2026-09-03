package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;

public class TinkersRepairContainerAdapterTest {

    @Test
    public void recognizesOnlyTheTwoPinnedExactContainerClassesAndSlotCounts() {
        TinkersRepairContainerAdapter.Layout station = TinkersRepairContainerAdapter
            .layoutFor(TinkersRepairContainerAdapter.TOOL_STATION_CONTAINER);
        TinkersRepairContainerAdapter.Layout forge = TinkersRepairContainerAdapter
            .layoutFor(TinkersRepairContainerAdapter.TOOL_FORGE_CONTAINER);

        assertEquals(TinkersStationKind.TOOL_STATION, station.getKind());
        assertEquals(4, station.getStationSlotCount());
        assertEquals(TinkersStationKind.TOOL_FORGE, forge.getKind());
        assertEquals(5, forge.getStationSlotCount());
        TinkersRepairContainerAdapter.Layout table = TinkersRepairContainerAdapter
            .layoutFor("tconstruct.tools.inventory.CraftingStationContainer");
        assertEquals(TinkersStationKind.TINKER_TABLE, table.getKind());
        assertEquals(10, table.getStationSlotCount());
        assertEquals(5, table.getInputSlot());
        assertEquals(46, table.getChestSlotStart());
        assertNull(TinkersRepairContainerAdapter.layoutFor("addon.SubclassedToolStationContainer"));
    }

    @Test
    public void mapsThePinnedStationAndForgePlayerSlotOrderExactly() {
        assertEquals(4, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(4, 9));
        assertEquals(30, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(4, 35));
        assertEquals(31, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(4, 0));
        assertEquals(39, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(4, 8));

        assertEquals(5, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(5, 9));
        assertEquals(31, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(5, 35));
        assertEquals(32, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(5, 0));
        assertEquals(40, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(5, 8));
        assertEquals(10, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(10, 9));
        assertEquals(37, TinkersRepairContainerAdapter.containerSlotForPlayerInventory(10, 0));
    }

    @Test
    public void stableToolIdentityExcludesRepairStateAndRetainsConstructionNbt() {
        Item item = new FakeModifyableItem();
        ItemStack damagedStack = tool(item, 700, 1000, 3, "head-a");
        damagedStack.setItemDamage(85);
        damagedStack.getTagCompound()
            .getCompoundTag("InfiTool")
            .setInteger("RepairCount", 4);
        damagedStack.getTagCompound()
            .getCompoundTag("InfiTool")
            .setBoolean("Broken", true);
        ItemStack repairedStack = tool(item, 500, 1000, 3, "head-a");
        repairedStack.setItemDamage(1);
        repairedStack.getTagCompound()
            .getCompoundTag("InfiTool")
            .setInteger("RepairCount", 5);
        repairedStack.getTagCompound()
            .getCompoundTag("InfiTool")
            .setBoolean("Broken", false);
        repairedStack.getTagCompound()
            .getCompoundTag("InfiTool")
            .setIntArray("ToRemove", new int[] { 1 });
        RepairToolSnapshot damaged = TinkersRepairContainerAdapter.readTool(damagedStack, 2, "TConstruct:pickaxe");
        RepairToolSnapshot repaired = TinkersRepairContainerAdapter.readTool(repairedStack, 2, "TConstruct:pickaxe");
        RepairToolSnapshot changedPart = TinkersRepairContainerAdapter
            .readTool(tool(item, 500, 1000, 3, "head-b"), 2, "TConstruct:pickaxe");

        assertEquals(damaged.getStableToolIdentity(), repaired.getStableToolIdentity());
        assertTrue(
            !damaged.getStableToolIdentity()
                .equals(changedPart.getStableToolIdentity()));
        assertEquals(700, damaged.getDamage());
        assertEquals(1000, damaged.getMaximumDamage());
        assertEquals(2, damaged.getReservedInventorySlot());
    }

    @Test
    public void missingExactTinkersDurabilityEvidenceIsRejected() {
        ItemStack stack = new ItemStack(new FakeModifyableItem());
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("InfiTool", new NBTTagCompound());
        stack.setTagCompound(root);

        assertThrows(
            IllegalStateException.class,
            () -> TinkersRepairContainerAdapter.readTool(stack, 0, "TConstruct:pickaxe"));
    }

    @Test
    public void finalizesPinnedRepairPreviewAndCountsExactMaterialRemovals() {
        Item item = new FakeModifyableItem();
        ItemStack input = tool(item, 700, 1000, 3, "head-a");
        ItemStack preview = tool(item, 400, 1000, 3, "head-a");
        preview.getTagCompound()
            .getCompoundTag("InfiTool")
            .setIntArray("ToRemove", new int[] { 2, 1 });
        ItemStack firstMaterial = new ItemStack(new Item(), 5);
        ItemStack secondMaterial = new ItemStack(new Item(), 3);

        assertEquals(
            3,
            TinkersRepairContainerAdapter
                .predictedMaterialConsumed(preview, Arrays.asList(firstMaterial, secondMaterial)));
        ItemStack output = TinkersRepairContainerAdapter
            .finalizedOutput(preview, Arrays.asList(firstMaterial, secondMaterial));
        RepairToolSnapshot inputEvidence = TinkersRepairContainerAdapter.readTool(input, 4, "TConstruct:pickaxe");
        RepairToolSnapshot outputEvidence = TinkersRepairContainerAdapter.readTool(output, 4, "TConstruct:pickaxe");

        assertEquals(inputEvidence.getStableToolIdentity(), outputEvidence.getStableToolIdentity());
        assertEquals(400, outputEvidence.getDamage());
        assertTrue(
            preview.getTagCompound()
                .getCompoundTag("InfiTool")
                .hasKey("ToRemove"));
        assertTrue(
            !output.getTagCompound()
                .getCompoundTag("InfiTool")
                .hasKey("ToRemove"));
    }

    @Test
    public void invalidPreviewRemovalCountIsRejected() {
        ItemStack preview = tool(new FakeModifyableItem(), 400, 1000, 3, "head-a");
        preview.getTagCompound()
            .getCompoundTag("InfiTool")
            .setIntArray("ToRemove", new int[] { 4 });
        assertThrows(
            IllegalStateException.class,
            () -> TinkersRepairContainerAdapter
                .predictedMaterialConsumed(preview, Arrays.asList(new ItemStack(new Item(), 3))));
    }

    private static ItemStack tool(Item item, int damage, int totalDurability, int modifiers, String headIdentity) {
        ItemStack stack = new ItemStack(item, 1, 0);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound base = new NBTTagCompound();
        base.setInteger("Damage", damage);
        base.setInteger("TotalDurability", totalDurability);
        base.setInteger("Modifiers", modifiers);
        base.setString("HeadIdentity", headIdentity);
        root.setTag("InfiTool", base);
        stack.setTagCompound(root);
        return stack;
    }

    public static final class FakeModifyableItem extends Item {

        public String getBaseTagName() {
            return "InfiTool";
        }
    }
}
