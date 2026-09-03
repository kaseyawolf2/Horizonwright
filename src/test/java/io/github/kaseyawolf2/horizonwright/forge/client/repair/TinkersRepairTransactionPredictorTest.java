package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

public class TinkersRepairTransactionPredictorTest {

    private static final Item TOOL = new TinkersRepairContainerAdapterTest.FakeModifyableItem();
    private static final Item MATERIAL = new Item();
    private static final MinecraftContainerSnapshotter SNAPSHOTS = new MinecraftContainerSnapshotter(item -> {
        if (item == TOOL) return "TConstruct:pickaxe";
        if (item == MATERIAL) return "TConstruct:materials";
        return null;
    });

    @Test
    public void predictsExactTakeConsumeAndReservedSlotReturnChain() {
        InventoryPlayer player = new InventoryPlayer(null);
        InventoryBasic station = new InventoryBasic("station", false, 4);
        ItemStack input = tool(700, false);
        ItemStack preview = tool(400, true);
        ItemStack material = new ItemStack(MATERIAL, 5, 3);
        station.setInventorySlotContents(0, preview);
        station.setInventorySlotContents(1, input);
        station.setInventorySlotContents(2, material);
        TestContainer container = new TestContainer(station, player);
        container.windowId = 9;
        RepairToolSnapshot inputEvidence = TinkersRepairContainerAdapter.readTool(input, 0, "TConstruct:pickaxe");
        RepairToolSnapshot outputEvidence = TinkersRepairContainerAdapter.readTool(
            TinkersRepairContainerAdapter.finalizedOutput(preview, Arrays.asList(material, null)),
            0,
            "TConstruct:pickaxe");
        TinkersRepairContainerEvidence evidence = new TinkersRepairContainerEvidence(
            TinkersStationKind.TOOL_STATION,
            9,
            4,
            31,
            inputEvidence,
            outputEvidence,
            2,
            Arrays.asList(SNAPSHOTS.fingerprint(material), null));
        NamedLoadout loadout = loadout(true);

        TinkersRepairTransactionPredictor.Prediction prediction = new TinkersRepairTransactionPredictor(
            new TinkersRepairContainerAdapter(),
            SNAPSHOTS).predictRecognized(container, player, 0, loadout, "repair", 41L, evidence);

        assertEquals(Collections.singletonList(2), prediction.getApprovedMaterialSlots());
        ContainerTransaction transaction = prediction.getTransaction();
        assertEquals(
            2,
            transaction.getClicks()
                .size());
        assertEquals(
            0,
            transaction.getClicks()
                .get(0)
                .getSlot());
        assertEquals(
            0,
            transaction.getClicks()
                .get(0)
                .getClickMode());
        assertNull(
            transaction.getClicks()
                .get(0)
                .getExpectedAfter()
                .getSlots()
                .get(0));
        assertNull(
            transaction.getClicks()
                .get(0)
                .getExpectedAfter()
                .getSlots()
                .get(1));
        assertEquals(
            3,
            transaction.getClicks()
                .get(0)
                .getExpectedAfter()
                .getSlots()
                .get(2)
                .getCount());
        assertEquals(
            400,
            prediction.getEvidence()
                .getPredictedOutput()
                .getDamage());
        assertEquals(
            31,
            transaction.getClicks()
                .get(1)
                .getSlot());
        assertEquals(
            transaction.getClicks()
                .get(0)
                .getExpectedAfter(),
            transaction.getClicks()
                .get(1)
                .getExpectedBefore());
        assertNull(
            transaction.getClicks()
                .get(1)
                .getExpectedAfter()
                .getCursor());
        assertEquals(
            "TConstruct:pickaxe",
            transaction.getClicks()
                .get(1)
                .getExpectedAfter()
                .getSlots()
                .get(31)
                .getItemId());
    }

    @Test
    public void refusesUnapprovedRepairMaterialBeforeBuildingClicks() {
        InventoryPlayer player = new InventoryPlayer(null);
        InventoryBasic station = new InventoryBasic("station", false, 4);
        ItemStack input = tool(700, false);
        ItemStack preview = tool(400, true);
        ItemStack material = new ItemStack(MATERIAL, 5, 3);
        station.setInventorySlotContents(0, preview);
        station.setInventorySlotContents(1, input);
        station.setInventorySlotContents(2, material);
        TestContainer container = new TestContainer(station, player);
        RepairToolSnapshot inputEvidence = TinkersRepairContainerAdapter.readTool(input, 0, "TConstruct:pickaxe");
        RepairToolSnapshot outputEvidence = TinkersRepairContainerAdapter.readTool(
            TinkersRepairContainerAdapter.finalizedOutput(preview, Arrays.asList(material, null)),
            0,
            "TConstruct:pickaxe");
        TinkersRepairContainerEvidence evidence = new TinkersRepairContainerEvidence(
            TinkersStationKind.TOOL_STATION,
            0,
            4,
            31,
            inputEvidence,
            outputEvidence,
            2,
            Arrays.asList(SNAPSHOTS.fingerprint(material), null));

        assertThrows(
            IllegalStateException.class,
            () -> new TinkersRepairTransactionPredictor(new TinkersRepairContainerAdapter(), SNAPSHOTS)
                .predictRecognized(container, player, 0, loadout(false), "repair", 41L, evidence));
    }

    @Test
    public void returnsUnusedTinkerTableMaterialToAttachedInventory() {
        InventoryPlayer player = new InventoryPlayer(null);
        InventoryBasic station = new InventoryBasic("station", false, 10);
        InventoryBasic chest = new InventoryBasic("chest", false, 9);
        ItemStack input = tool(700, false);
        ItemStack preview = tool(400, true);
        ItemStack material = new ItemStack(MATERIAL, 5, 3);
        station.setInventorySlotContents(0, preview);
        station.setInventorySlotContents(5, input);
        station.setInventorySlotContents(1, material);
        CraftingContainer container = new CraftingContainer(station, player, chest);
        container.windowId = 11;
        RepairToolSnapshot inputEvidence = TinkersRepairContainerAdapter.readTool(input, 0, "TConstruct:pickaxe");
        java.util.List<ItemStack> materials = Arrays.asList(material, null, null, null, null, null, null, null);
        RepairToolSnapshot outputEvidence = TinkersRepairContainerAdapter
            .readTool(TinkersRepairContainerAdapter.finalizedOutput(preview, materials), 0, "TConstruct:pickaxe");
        TinkersRepairContainerEvidence evidence = new TinkersRepairContainerEvidence(
            TinkersStationKind.TINKER_TABLE,
            11,
            10,
            38,
            inputEvidence,
            outputEvidence,
            2,
            Arrays.asList(SNAPSHOTS.fingerprint(material), null, null, null, null, null, null, null));

        TinkersRepairTransactionPredictor.Prediction prediction = new TinkersRepairTransactionPredictor(
            new TinkersRepairContainerAdapter(),
            SNAPSHOTS).predictRecognized(container, player, 0, loadout(true), "repair-table", 41L, evidence);
        ContainerTransaction transaction = prediction.getTransaction();

        assertEquals(
            4,
            transaction.getClicks()
                .size());
        assertEquals(
            1,
            transaction.getClicks()
                .get(2)
                .getSlot());
        assertEquals(
            46,
            transaction.getClicks()
                .get(3)
                .getSlot());
        assertNull(
            transaction.getClicks()
                .get(3)
                .getExpectedAfter()
                .getSlots()
                .get(1));
        assertEquals(
            3,
            transaction.getClicks()
                .get(3)
                .getExpectedAfter()
                .getSlots()
                .get(46)
                .getCount());
        assertEquals(Arrays.asList(1, 46), prediction.getApprovedMaterialSlots());
    }

    private static NamedLoadout loadout(boolean includeMaterial) {
        LoadoutReservation tool = new LoadoutReservation("pick", LoadoutRole.TOOL, "TConstruct:pickaxe", 0, null, 1);
        if (!includeMaterial) return new NamedLoadout("mining", "Mining", Collections.singletonList(tool));
        return new NamedLoadout(
            "mining",
            "Mining",
            Arrays.asList(
                tool,
                new LoadoutReservation("repair", LoadoutRole.REPAIR_MATERIAL, "TConstruct:materials", 3, null, 2)));
    }

    private static ItemStack tool(int damage, boolean preview) {
        ItemStack stack = new ItemStack(TOOL, 1, 0);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound base = new NBTTagCompound();
        base.setInteger("Damage", damage);
        base.setInteger("TotalDurability", 1000);
        base.setString("HeadIdentity", "head-a");
        if (preview) base.setIntArray("ToRemove", new int[] { 2 });
        root.setTag("InfiTool", base);
        stack.setTagCompound(root);
        return stack;
    }

    private static final class TestContainer extends Container {

        private TestContainer(InventoryBasic station, InventoryPlayer player) {
            for (int slot = 0; slot < 4; slot++) addSlotToContainer(new Slot(station, slot, 0, 0));
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlotToContainer(new Slot(player, column + row * 9 + 9, 0, 0));
                }
            }
            for (int slot = 0; slot < 9; slot++) addSlotToContainer(new Slot(player, slot, 0, 0));
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static final class CraftingContainer extends Container {

        private CraftingContainer(InventoryBasic station, InventoryPlayer player, InventoryBasic chest) {
            for (int slot = 0; slot < 10; slot++) addSlotToContainer(new Slot(station, slot, 0, 0));
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlotToContainer(new Slot(player, column + row * 9 + 9, 0, 0));
                }
            }
            for (int slot = 0; slot < 9; slot++) addSlotToContainer(new Slot(player, slot, 0, 0));
            for (int slot = 0; slot < 9; slot++) addSlotToContainer(new Slot(chest, slot, 0, 0));
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}
