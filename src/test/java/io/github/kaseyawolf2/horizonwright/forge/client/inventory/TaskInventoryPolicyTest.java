package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.inventory.GeneralizedInventoryPlanner;
import io.github.kaseyawolf2.horizonwright.core.inventory.InventoryEndpoint;
import io.github.kaseyawolf2.horizonwright.core.inventory.InventorySlot;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageFilterMode;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemRule;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadTask;

public class TaskInventoryPolicyTest {

    private final Map<Item, String> ids = new IdentityHashMap<>();
    private final MinecraftContainerSnapshotter fingerprints = new MinecraftContainerSnapshotter(ids::get);

    @Test
    public void knownBagIsSkippedWhenItCannotSupplyOrAcceptAnythingNeeded() {
        ItemStack axe = tool("test:axe", "axe");
        TaskInventoryPolicy policy = policy(TreeTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);
        assertFalse(
            BagVisitPolicy.needsVisit(
                Collections.emptyList(),
                new ItemStack[] { axe },
                policy,
                false,
                stack -> false,
                stack -> true,
                fingerprints));
        assertFalse(
            BagVisitPolicy.needsVisit(
                Collections.singletonList(axe.copy()),
                new ItemStack[] { axe },
                policy,
                false,
                stack -> false,
                stack -> true,
                fingerprints));
    }

    @Test
    public void knownBagStillOpensForMissingToolAndCountedSurplus() {
        ItemStack axe = tool("test:axe", "axe");
        TaskInventoryPolicy policy = policy(TreeTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);
        assertTrue(
            BagVisitPolicy.needsVisit(
                Collections.singletonList(axe),
                new ItemStack[0],
                policy,
                false,
                stack -> false,
                stack -> true,
                fingerprints));
        assertTrue(
            BagVisitPolicy.needsVisit(
                Collections.emptyList(),
                new ItemStack[] { axe, axe.copy() },
                policy,
                false,
                stack -> false,
                stack -> true,
                fingerprints));
        assertFalse(
            BagVisitPolicy.needsVisit(
                Collections.emptyList(),
                new ItemStack[] { axe, axe.copy() },
                policy,
                false,
                stack -> false,
                stack -> false,
                fingerprints));
    }

    @Test
    public void unloadDecisionRespectsDestinationCargoPredicate() {
        ItemStack item = tool("test:axe", "axe");
        TaskInventoryPolicy policy = policy(TreeTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);
        assertTrue(
            BagVisitPolicy.needsVisit(
                Collections.singletonList(item),
                new ItemStack[0],
                policy,
                true,
                stack -> true,
                stack -> false,
                fingerprints));
        assertFalse(
            BagVisitPolicy.needsVisit(
                Collections.singletonList(item),
                new ItemStack[0],
                policy,
                true,
                stack -> false,
                stack -> true,
                fingerprints));
    }

    @Test
    public void activeAxeStaysAndIdenticalSpareAxeIsPacked() {
        ItemStack axe = tool("test:axe", "axe");
        ItemStack spare = axe.copy();
        TaskInventoryPolicy policy = policy(TreeTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);

        GeneralizedInventoryPlanner.Plan plan = pack(policy, axe, spare);

        assertTrue(plan.isReadyForTask());
        assertEquals(
            1,
            plan.getMoves()
                .size());
        assertEquals(
            1,
            plan.getMoves()
                .get(0)
                .getSource()
                .getSlot());
        assertEquals(
            1,
            plan.getMoves()
                .get(0)
                .getCount());
        assertFalse(policy.stow(axe));
    }

    @Test
    public void multitoolCoversMiningClassesWithoutKeepingEachSpare() {
        ItemStack pick = tool("test:pick", "pickaxe");
        ItemStack shovel = tool("test:shovel", "shovel");
        ItemStack multi = tool("test:multi", "pickaxe", "shovel", "axe");
        TaskInventoryPolicy policy = policy(ExcavationTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);

        GeneralizedInventoryPlanner.Plan plan = pack(policy, pick, shovel, multi);

        assertTrue(plan.isReadyForTask());
        assertEquals(
            2,
            plan.getMoves()
                .size());
        for (GeneralizedInventoryPlanner.Move move : plan.getMoves()) assertNotEquals(
            2,
            move.getSource()
                .getSlot());
    }

    @Test
    public void explicitNamedToolCountOverridesAutomaticDuplicateReduction() {
        ItemStack axe = tool("test:axe", "axe");
        LoadoutReservation reservation = new LoadoutReservation("two-axes", LoadoutRole.TOOL, "test:axe", -1, null, 2);
        TaskInventoryPolicy policy = policy(
            TreeTask.TYPE,
            Collections.emptyMap(),
            Collections.singletonList(reservation),
            null);

        assertTrue(
            pack(policy, axe, axe.copy()).getMoves()
                .isEmpty());
        assertEquals(2, policy.requiredCount(axe));
        assertFalse(policy.alreadyEquipped(axe, new ItemStack[] { axe }));
        assertTrue(policy.alreadyEquipped(axe, new ItemStack[] { axe, axe.copy() }));
    }

    @Test
    public void explicitRepairMaterialsRetainMinimumAndPackSurplus() {
        ItemStack material = stack("test:repair", 20, 0);
        LoadoutReservation reservation = new LoadoutReservation(
            "repair",
            LoadoutRole.REPAIR_MATERIAL,
            "test:repair",
            0,
            null,
            8);
        TaskInventoryPolicy policy = policy(
            TreeTask.TYPE,
            Collections.emptyMap(),
            Collections.singletonList(reservation),
            null);

        GeneralizedInventoryPlanner.Plan plan = pack(policy, material);

        assertEquals(
            1,
            plan.getMoves()
                .size());
        assertEquals(
            12,
            plan.getMoves()
                .get(0)
                .getCount());
        assertFalse(policy.stow(material));
        assertEquals(8, policy.requiredCount(material));
    }

    @Test
    public void unknownTasksAndSlotBoundRepairKeepTheirTools() {
        ItemStack axe = tool("test:axe", "axe");
        ItemStack pick = tool("test:pick", "pickaxe");
        for (String type : new String[] { "future-building-task", RepairTask.TYPE }) {
            TaskInventoryPolicy policy = policy(type, Collections.emptyMap(), Collections.emptyList(), null);
            assertTrue(
                pack(policy, axe, pick).getMoves()
                    .isEmpty());
        }
        TaskInventoryPolicy unknown = policy(
            "future-building-task",
            Collections.emptyMap(),
            Collections.emptyList(),
            null);
        assertTrue(
            pack(unknown, stack("test:building-material", 32, 0)).getMoves()
                .isEmpty());
    }

    @Test
    public void brokenToolCannotSuppressRetrievalOfWorkingReplacement() {
        ItemStack good = tool("test:axe", "axe");
        ItemStack broken = good.copy();
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound tool = new NBTTagCompound();
        tool.setBoolean("Broken", true);
        root.setTag("InfiTool", tool);
        broken.setTagCompound(root);
        TaskInventoryPolicy policy = policy(TreeTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);

        assertFalse(TaskInventoryPolicy.usableTool(broken));
        assertFalse(policy.alreadyEquipped(good, new ItemStack[] { broken }));
        assertEquals(0, policy.requiredCount(broken));
        assertEquals(1, policy.requiredCount(good));
        assertTrue(policy.stow(broken));
    }

    @Test
    public void farmRetrievesVanillaPlantingSuppliesWithExactCocoaMetadata() {
        TaskInventoryPolicy policy = policy(FarmTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);
        for (String id : new String[] { "minecraft:carrot", "minecraft:potato", "minecraft:nether_wart" })
            assertEquals(64, policy.requiredCount(stack(id, 1, 0)));
        assertEquals(64, policy.requiredCount(stack("minecraft:dye", 1, 3)));
        assertEquals(0, policy.requiredCount(stack("minecraft:dye", 1, 4)));
    }

    @Test
    public void quarryMaterialMatchesConfiguredBlockItemWithoutInventedMetadataSyntax() {
        Item material = new Item();
        ids.put(material, "test:item-name-differs-from-block");
        Map<String, String> parameters = Collections.singletonMap(ExcavationTask.RAMP_MATERIAL, "test:quarry_block");
        TaskInventoryPolicy policy = policy(
            ExcavationTask.TYPE,
            parameters,
            Collections.emptyList(),
            null,
            configured -> "test:quarry_block".equals(configured) ? material : null);

        assertEquals(64, policy.requiredCount(new ItemStack(material)));
        assertEquals(0, policy.requiredCount(stack("test:other", 1, 0)));
    }

    @Test
    public void unloadStagesHarvestFoodAndOnlyCargoAcceptedByFinalStorage() {
        StorageItemFilter filter = new StorageItemFilter(
            StorageFilterMode.ALLOW_MATCHES,
            Collections.singletonList(new StorageItemRule("test:ore", -1, null)));
        TaskInventoryPolicy policy = policy(
            UnloadTask.TYPE,
            Collections.singletonMap("storageId", "final"),
            Collections.emptyList(),
            filter);

        assertTrue(policy.unloadCargo(stack("test:ore", 32, 0)));
        assertFalse(policy.unloadCargo(stack("test:stone", 32, 0)));
        assertFalse(policy.unloadCargo(tool("test:ore", "pickaxe")));
        ItemFood food = new ItemFood(2, 0.5F, false);
        ids.put(food, "test:ore");
        assertTrue(policy.unloadCargo(new ItemStack(food, 5)));
        TaskInventoryPolicy explicitFood = policy(
            UnloadTask.TYPE,
            Collections.singletonMap("storageId", "final"),
            Collections.singletonList(new LoadoutReservation("food", LoadoutRole.FOOD, "test:ore", 0, null, 5)),
            filter);
        assertFalse(explicitFood.unloadCargo(new ItemStack(food, 5)));
        assertFalse(
            policy(UnloadTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null)
                .unloadCargo(stack("test:ore", 32, 0)));
    }

    @Test
    public void ordinaryFoodIsAllCargoUntilAutoProvisioning() {
        ItemFood food = new ItemFood(2, 0.5F, false);
        ids.put(food, "test:food");
        TaskInventoryPolicy policy = policy(TreeTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);

        GeneralizedInventoryPlanner.Plan plan = pack(policy, new ItemStack(food, 64));

        assertTrue(plan.isReadyForTask());
        assertEquals(
            1,
            plan.getMoves()
                .size());
        assertEquals(
            64,
            plan.getMoves()
                .get(0)
                .getCount());
    }

    @Test
    public void harvestedSeedSurplusPacksBeyondActiveFarmWorkingStock() {
        ItemSeeds seeds = new ItemSeeds(Blocks.wheat, Blocks.farmland);
        ids.put(seeds, "minecraft:wheat_seeds");
        TaskInventoryPolicy policy = policy(FarmTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);

        GeneralizedInventoryPlanner.Plan plan = pack(policy, new ItemStack(seeds, 64), new ItemStack(seeds, 64));

        assertTrue(plan.isReadyForTask());
        assertEquals(
            1,
            plan.getMoves()
                .size());
        assertEquals(
            64,
            plan.getMoves()
                .get(0)
                .getCount());
    }

    @Test
    public void harvestedCarrotSurplusPacksBeyondActiveFarmWorkingStock() {
        ItemFood carrot = new ItemFood(2, 0.5F, false);
        ids.put(carrot, "minecraft:carrot");
        TaskInventoryPolicy policy = policy(FarmTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);

        GeneralizedInventoryPlanner.Plan plan = pack(policy, new ItemStack(carrot, 64), new ItemStack(carrot, 64));

        assertTrue(plan.isReadyForTask());
        assertEquals(
            1,
            plan.getMoves()
                .size());
        assertEquals(
            64,
            plan.getMoves()
                .get(0)
                .getCount());
    }

    @Test
    public void networkDepositUnloadsFoodButPreservesExplicitTaskSupplies() {
        ItemFood food = new ItemFood(2, 0.5F, false);
        ids.put(food, "test:food");
        TaskInventoryPolicy policy = policy(TreeTask.TYPE, Collections.emptyMap(), Collections.emptyList(), null);
        assertTrue(policy.mayDepositWholeStack(new ItemStack[] { new ItemStack(food, 64) }, 0));
        assertTrue(
            policy.mayDepositWholeStack(new ItemStack[] { new ItemStack(food, 64), new ItemStack(food, 16) }, 0));
        ItemStack axe = tool("test:axe", "axe");
        assertFalse(policy.mayDepositWholeStack(new ItemStack[] { axe }, 0));
        assertTrue(policy.mayDepositWholeStack(new ItemStack[] { axe, axe.copy() }, 1));
        TaskInventoryPolicy explicit = policy(
            TreeTask.TYPE,
            Collections.emptyMap(),
            Collections.singletonList(new LoadoutReservation("reserve", LoadoutRole.FOOD, "test:food", 0, null, 80)),
            null);
        assertFalse(
            explicit.mayDepositWholeStack(new ItemStack[] { new ItemStack(food, 64), new ItemStack(food, 16) }, 0));
    }

    private GeneralizedInventoryPlanner.Plan pack(TaskInventoryPolicy policy, ItemStack... main) {
        List<InventorySlot> player = new ArrayList<>();
        List<InventorySlot> bag = new ArrayList<>();
        Map<ItemFingerprint, ItemStack> stacks = new HashMap<>();
        for (int index = 0; index < main.length; index++) {
            ItemFingerprint item = fingerprints.fingerprint(main[index]);
            stacks.put(item, main[index]);
            player.add(new InventorySlot(index, item, main[index].getMaxStackSize(), ignored -> true, 0, true));
            bag.add(
                new InventorySlot(
                    index,
                    null,
                    value -> stacks.get(value)
                        .getMaxStackSize(),
                    ignored -> true,
                    0,
                    true));
        }
        return new GeneralizedInventoryPlanner().plan(
            Arrays.asList(
                new InventoryEndpoint("player", InventoryEndpoint.Kind.PLAYER, true, true, true, null, player),
                new InventoryEndpoint("bag", InventoryEndpoint.Kind.PORTABLE, true, true, true, null, bag)),
            policy.workingReservations(main),
            value -> policy.protectedForPlanner(stacks.get(value)),
            value -> policy.stowForPlanner(stacks.get(value)));
    }

    private ItemStack tool(String id, String... classes) {
        Item item = new Item().setMaxStackSize(1);
        for (String toolClass : classes) item.setHarvestLevel(toolClass, 1);
        ids.put(item, id);
        return new ItemStack(item);
    }

    private ItemStack stack(String id, int count, int metadata) {
        Item item = new Item();
        ids.put(item, id);
        return new ItemStack(item, count, metadata);
    }

    private TaskInventoryPolicy policy(String type, Map<String, String> parameters,
        List<LoadoutReservation> reservations, StorageItemFilter filter) {
        return policy(type, parameters, reservations, filter, ignored -> null);
    }

    private TaskInventoryPolicy policy(String type, Map<String, String> parameters,
        List<LoadoutReservation> reservations, StorageItemFilter filter, Function<String, Item> blockItems) {
        ProfileEnvelope profile = new ProfileEnvelope(
            0,
            new WorldProfileIdentity("test", "Test", "local", "world", 0),
            Collections.emptyList(),
            Collections.singletonList(new NamedLocation("location", "Location", 0, 0, 64, 0)),
            Collections.emptyList(),
            Collections.singletonList(new NamedLoadout("explicit", "Explicit", reservations)),
            filter == null ? Collections.emptyList()
                : Collections.singletonList(new NamedStorageEndpoint("final", "Final", "location", filter)));
        return new TaskInventoryPolicy(
            new TaskSpec("task", type, "Task", TaskLane.CHORE, parameters),
            profile,
            fingerprints,
            blockItems);
    }
}
