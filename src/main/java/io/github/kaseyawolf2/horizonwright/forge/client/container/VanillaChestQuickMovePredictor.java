package io.github.kaseyawolf2.horizonwright.forge.client.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadClickPrediction;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlan;
import io.github.kaseyawolf2.horizonwright.core.logistics.UnloadPlanner;

/** Exact 1.7.10 {@link ContainerChest} player-to-chest quick-move model. */
final class VanillaChestQuickMovePredictor {

    private static final int PLAYER_SLOT_COUNT = 36;
    private final MinecraftContainerSnapshotter snapshots;

    VanillaChestQuickMovePredictor(MinecraftContainerSnapshotter snapshots) {
        if (snapshots == null) {
            throw new IllegalArgumentException("snapshots must not be null");
        }
        this.snapshots = snapshots;
    }

    Prediction predict(ContainerChest chest, ItemStack cursor, NamedLoadout loadout, StorageItemFilter filter,
        String clickIdPrefix) {
        requireExactLayout(chest, cursor);
        ContainerSnapshot initial = snapshots.capture(chest, cursor, 0L);
        int chestSlots = chest.getLowerChestInventory()
            .getSizeInventory();
        List<ItemStack> simulated = copyWindowStacks(chest);
        List<ItemFingerprint> playerSlots = playerFingerprints(simulated, chestSlots);
        UnloadPlan plan = UnloadPlanner.plan(loadout, playerSlots, filter);
        if (!plan.mayStartTransaction() || plan.getUnloadableSlots()
            .isEmpty()) {
            return new Prediction(playerSlots, Collections.<UnloadClickPrediction>emptyList());
        }

        List<UnloadClickPrediction> predictions = new ArrayList<>();
        long revision = 0L;
        for (Integer playerSlot : plan.getUnloadableSlots()) {
            int windowSlot = windowSlot(chestSlots, playerSlot);
            ContainerSnapshot before = snapshot(initial, simulated, revision++);
            if (!quickMoveIntoChest(simulated, windowSlot, chestSlots)) {
                throw new IllegalStateException("vanilla chest has no capacity for approved player slot " + playerSlot);
            }
            ContainerSnapshot after = snapshot(initial, simulated, revision);
            VerifiedContainerClick click = new VerifiedContainerClick(
                clickIdPrefix + "-slot-" + playerSlot,
                windowSlot,
                0,
                1,
                before,
                after);
            predictions.add(new UnloadClickPrediction(playerSlot, click));
        }
        return new Prediction(playerSlots, predictions);
    }

    private static void requireExactLayout(ContainerChest chest, ItemStack cursor) {
        if (chest == null || chest.getClass() != ContainerChest.class) {
            throw new IllegalArgumentException("only the exact vanilla ContainerChest layout is supported");
        }
        if (cursor != null) {
            throw new IllegalStateException("unloading requires an empty cursor");
        }
        int chestSlots = chest.getLowerChestInventory()
            .getSizeInventory();
        if (chestSlots <= 0 || chestSlots % 9 != 0
            || chestSlots > 54
            || chest.inventorySlots.size() != chestSlots + PLAYER_SLOT_COUNT) {
            throw new IllegalStateException("vanilla chest slot count is not recognized");
        }
        for (int index = 0; index < chest.inventorySlots.size(); index++) {
            Object value = chest.inventorySlots.get(index);
            if (!(value instanceof Slot) || ((Slot) value).slotNumber != index) {
                throw new IllegalStateException("vanilla chest slots are not contiguous");
            }
        }
    }

    private static List<ItemStack> copyWindowStacks(ContainerChest chest) {
        List<ItemStack> result = new ArrayList<>(chest.inventorySlots.size());
        for (Object value : chest.inventorySlots) {
            ItemStack stack = ((Slot) value).getStack();
            result.add(stack == null ? null : stack.copy());
        }
        return result;
    }

    private List<ItemFingerprint> playerFingerprints(List<ItemStack> window, int chestSlots) {
        List<ItemFingerprint> result = new ArrayList<>(Collections.nCopies(PLAYER_SLOT_COUNT, null));
        for (int playerSlot = 0; playerSlot < PLAYER_SLOT_COUNT; playerSlot++) {
            result.set(playerSlot, snapshots.fingerprint(window.get(windowSlot(chestSlots, playerSlot))));
        }
        return result;
    }

    private ContainerSnapshot snapshot(ContainerSnapshot identity, List<ItemStack> stacks, long revision) {
        List<ItemFingerprint> fingerprints = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            fingerprints.add(snapshots.fingerprint(stack));
        }
        return new ContainerSnapshot(
            identity.getWindowId(),
            identity.getContainerType(),
            identity.getSlotLayout(),
            revision,
            fingerprints,
            null);
    }

    private static boolean quickMoveIntoChest(List<ItemStack> slots, int sourceSlot, int chestSlots) {
        ItemStack source = slots.get(sourceSlot);
        if (source == null) {
            return false;
        }
        boolean changed = false;
        if (source.isStackable()) {
            for (int target = 0; target < chestSlots && source.stackSize > 0; target++) {
                ItemStack destination = slots.get(target);
                if (!canStack(source, destination)) {
                    continue;
                }
                int combined = destination.stackSize + source.stackSize;
                int maximum = source.getMaxStackSize();
                if (combined <= maximum) {
                    destination.stackSize = combined;
                    source.stackSize = 0;
                    changed = true;
                } else if (destination.stackSize < maximum) {
                    source.stackSize -= maximum - destination.stackSize;
                    destination.stackSize = maximum;
                    changed = true;
                }
            }
        }
        if (source.stackSize > 0) {
            for (int target = 0; target < chestSlots; target++) {
                if (slots.get(target) == null) {
                    slots.set(target, source.copy());
                    source.stackSize = 0;
                    changed = true;
                    break;
                }
            }
        }
        if (source.stackSize == 0) {
            slots.set(sourceSlot, null);
        }
        return changed;
    }

    private static boolean canStack(ItemStack source, ItemStack destination) {
        return destination != null && destination.getItem() == source.getItem()
            && (!source.getHasSubtypes() || source.getItemDamage() == destination.getItemDamage())
            && ItemStack.areItemStackTagsEqual(source, destination);
    }

    private static int windowSlot(int chestSlots, int playerSlot) {
        if (playerSlot < 0 || playerSlot >= PLAYER_SLOT_COUNT) {
            throw new IllegalArgumentException("player slot is outside the vanilla inventory");
        }
        return playerSlot < 9 ? chestSlots + 27 + playerSlot : chestSlots + playerSlot - 9;
    }

    static final class Prediction {

        private final List<ItemFingerprint> playerSlots;
        private final List<UnloadClickPrediction> predictions;

        private Prediction(List<ItemFingerprint> playerSlots, List<UnloadClickPrediction> predictions) {
            this.playerSlots = Collections.unmodifiableList(new ArrayList<>(playerSlots));
            this.predictions = Collections.unmodifiableList(new ArrayList<>(predictions));
        }

        List<ItemFingerprint> getPlayerSlots() {
            return playerSlots;
        }

        List<UnloadClickPrediction> getPredictions() {
            return predictions;
        }
    }
}
