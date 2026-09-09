package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C16PacketClientStatus;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerActionPacing;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.core.inventory.GeneralizedInventoryPlanner;
import io.github.kaseyawolf2.horizonwright.core.inventory.InventoryEndpoint;
import io.github.kaseyawolf2.horizonwright.core.inventory.InventoryLocation;
import io.github.kaseyawolf2.horizonwright.core.inventory.InventorySlot;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.StepResult;
import io.github.kaseyawolf2.horizonwright.core.task.TaskInterruption;
import io.github.kaseyawolf2.horizonwright.core.task.TaskStepContext;
import io.github.kaseyawolf2.horizonwright.forge.client.container.LiveContainerTransactionExecutor;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ContainerTransactionPacketCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.task.InventoryService;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadTask;

/** Session-owned, checkpoint-journaled preparation shared by every runtime task. */
public final class LiveExtendedInventoryService implements InventoryService {

    private enum Phase {
        NEXT,
        OPEN_PLAYER,
        STAGE,
        CLOSE_PLAYER,
        SELECT,
        OPEN,
        WAIT_OPEN,
        TRANSFER,
        CLOSE,
        RETURN,
        RESTORE,
        CLOSE_RETURN_PLAYER,
        FINISH
    }

    private final Minecraft mc;
    private final ActionSessionGuard guard;
    private final Supplier<ProfileEnvelope> profile;
    private final PortableInventoryAdapters adapters = new PortableInventoryAdapters();
    private final MinecraftContainerSnapshotter raw = new MinecraftContainerSnapshotter();
    private final MinecraftContainerSnapshotter snapshots;
    private final LiveContainerTransactionExecutor executor;
    private final ContainerTransactionPacketCoordinator packets;
    private final GeneralizedInventoryPlanner planner = new GeneralizedInventoryPlanner();
    private final ContainerActionPacing pacing = new ContainerActionPacing(5);
    private final Map<String, InventoryPreparationTracker> prepared = new HashMap<>();
    private final Map<String, String> drained = new HashMap<>();
    private Session active;
    private long sequence;
    private String diagnostic = "Extended inventory ready";

    public LiveExtendedInventoryService(Minecraft mc, ActionSessionGuard guard,
        ContainerTransactionPacketCoordinator packets, Supplier<ProfileEnvelope> profile) {
        this.mc = mc;
        this.guard = guard;
        this.profile = profile;
        this.packets = packets;
        snapshots = new MinecraftContainerSnapshotter(item -> {
            Object name = Item.itemRegistry.getNameForObject(item);
            return name == null ? null : name.toString();
        }, this::projectCarrier, adapters::transportStack);
        executor = new LiveContainerTransactionExecutor(
            mc,
            guard,
            packets,
            snapshots,
            pacing,
            this::requireInteractionScreen);
    }

    public void tick() {
        executor.tick();
    }

    public String diagnostic() {
        return diagnostic;
    }

    public synchronized List<String> describeInventories() {
        requireClient();
        List<String> lines = new ArrayList<>();
        int occupied = 0;
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) if (stack != null) occupied++;
        lines.add("Player inventory: " + occupied + "/36 slots occupied. " + diagnostic);
        for (Carrier carrier : carriers()) {
            ItemStack stack = carrier.stack(mc);
            PortableInventoryAdapter adapter = adapters.find(stack);
            String contents = adapter.contentsKnown(stack) ? adapter.readContents(stack)
                .size() + " stored stacks"
                : adapter instanceof Ae2TerminalAdapter ? "contents verified after opening a powered connection"
                    : "contents verified when opened";
            lines.add(
                (carrier.slot < 0 ? "Worn" : "Slot " + (carrier.slot + 1)) + ": "
                    + stack.getDisplayName()
                    + " ("
                    + adapter.id()
                    + ") - "
                    + contents);
        }
        return lines;
    }

    @Override
    public synchronized boolean needsPreparation(TaskStepContext context, boolean completingUnload) {
        requireClient();
        if (active != null) return true;
        if (!completingUnload && UnloadTask.TYPE.equals(
            context.getSpec()
                .getType()))
            return false;
        String task = context.getSpec()
            .getId();
        if (carriers(!completingUnload).isEmpty()) {
            prepared.remove(task);
            drained.remove(task);
            return false;
        }
        if (completingUnload) return !signature().equals(drained.get(task));
        InventoryPreparationTracker tracker = prepared.get(task);
        return tracker == null || tracker.needsPreparation(
            preparationSnapshot(new TaskInventoryPolicy(context.getSpec(), profile.get())),
            mc.thePlayer.ticksExisted);
    }

    @Override
    public synchronized StepResult prepare(TaskStepContext context, boolean completingUnload) {
        requireClient();
        try {
            if (context.isSuspensionRequested()) {
                stop();
                return StepResult.safeSuspension(
                    context.getActionEpoch(),
                    context.getCheckpoint(),
                    "Inventory preparation suspended; outstanding transfers require fresh observation");
            }
            if (!context.getActions()
                .isAuthoritative()) throw new IllegalStateException("Inventory authority changed");
            if (active == null) {
                String startWait = preparationStartWait(
                    mc.currentScreen != null,
                    mc.thePlayer.openContainer == mc.thePlayer.inventoryContainer,
                    mc.thePlayer.inventory.getItemStack() == null,
                    pacing);
                if (startWait != null) return waiting(context, startWait);
                if (!guard.isReadyForSession())
                    return waiting(context, "Waiting for preceding action packets to drain");
                Optional<ActionLease> lease = context.getActions()
                    .tryAcquire(
                        EnumSet.of(ActionCapability.USE, ActionCapability.HELD_USE, ActionCapability.CONTAINER));
                if (!lease.isPresent()) return waiting(context, "Waiting for inventory capabilities");
                try {
                    active = new Session(context, completingUnload, lease.get());
                } catch (RuntimeException | LinkageError failure) {
                    lease.get()
                        .close();
                    throw failure;
                }
                guard.begin(active.lease);
            }
            Session s = active;
            if (mc.theWorld != s.world || mc.thePlayer != s.player)
                throw new IllegalStateException("Player or world changed during inventory preparation");
            if (!s.task.equals(
                context.getSpec()
                    .getId())
                || s.epoch != context.getActionEpoch()
                || !s.released && !s.lease.isValid())
                throw new IllegalStateException("Inventory preparation no longer belongs to the current task");
            if (System.nanoTime() - s.started > 180_000_000_000L) throw new IllegalStateException(
                "Inventory preparation timed out; inspect the last transfer before resuming");
            if (s.transaction != null) {
                ContainerTransactionState state = s.transaction.getState();
                if (state == ContainerTransactionState.ABORTED)
                    throw new IllegalStateException(s.transaction.getAbortReason());
                if (state != ContainerTransactionState.COMPLETED)
                    return waiting(context, "Waiting for server-confirmed inventory transfer");
                s.transaction = null;
                pacing.actionPerformed();
                return waiting(context, "Inventory transfer confirmed");
            }
            if (s.networkItem != null) return observeNetwork(context, s);
            if (!pacing.isReady()) return waiting(context, "Pausing between extended inventory actions (5 ticks)");
            switch (s.phase) {
                case NEXT:
                    if (s.index >= s.carriers.size()) {
                        s.phase = Phase.FINISH;
                        break;
                    }
                    s.carrier = s.carriers.get(s.index++);
                    s.adapter = adapters.find(s.carrier.stack(mc));
                    if (s.adapter == null) throw new IllegalStateException("An inventory carrier moved or disappeared");
                    s.originalSlot = s.carrier.slot;
                    s.heldSlot = s.originalSlot < 0 ? -1 : s.originalSlot < 9 ? s.originalSlot : s.previousHotbar;
                    s.carrierBefore = raw.fingerprint(s.adapter.stableCarrier(s.carrier.stack(mc)));
                    s.carrierOriginal = s.carrier.stack(mc)
                        .copy();
                    s.phase = s.originalSlot >= 9 ? Phase.OPEN_PLAYER : Phase.SELECT;
                    break;
                case OPEN_PLAYER:
                    openPlayerInventory(s);
                    s.phase = Phase.STAGE;
                    break;
                case STAGE:
                    requirePlayerInventoryScreen(s);
                    swapCarrier(s, s.originalSlot, s.heldSlot);
                    s.phase = Phase.CLOSE_PLAYER;
                    break;
                case CLOSE_PLAYER:
                    closePlayerInventory(s);
                    s.phase = Phase.SELECT;
                    break;
                case SELECT:
                    requireCarrier(s, false);
                    if (s.heldSlot >= 0) {
                        mc.thePlayer.inventory.currentItem = s.heldSlot;
                        mc.playerController.updateController();
                    }
                    s.phase = Phase.OPEN;
                    break;
                case OPEN:
                    if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer)
                        throw new IllegalStateException("A different inventory opened during preparation");
                    if (mc.currentScreen != null)
                        throw new IllegalStateException("Close the current screen before opening the carrier");
                    requireCarrier(s, false);
                    s.openSyncRevision = packets.inventorySyncRevision();
                    if (s.heldSlot >= 0) {
                        if (mc.thePlayer.inventory.currentItem != s.heldSlot)
                            throw new IllegalStateException("Selected inventory carrier changed before opening");
                        s.adapter.openHeld(mc, currentCarrier(s));
                    } else PortableInventoryAdapters.openWornAdventureBackpack(mc);
                    s.openedAt = mc.thePlayer.ticksExisted;
                    s.phase = Phase.WAIT_OPEN;
                    break;
                case WAIT_OPEN:
                    if (mc.thePlayer.openContainer == mc.thePlayer.inventoryContainer) {
                        if (mc.thePlayer.ticksExisted - s.openedAt > 100) throw new IllegalStateException(
                            "The server did not open " + s.adapter.id() + "; check access, power and range");
                        break;
                    }
                    if (packets.inventorySyncRevision(mc.thePlayer.openContainer.windowId) <= s.openSyncRevision
                        || mc.thePlayer.ticksExisted - s.openedAt < 2
                        || s.adapter instanceof Ae2TerminalAdapter
                            && !((Ae2TerminalAdapter) s.adapter).powered(mc.currentScreen)) {
                        if (mc.thePlayer.ticksExisted - s.openedAt > 100) throw new IllegalStateException(
                            "Inventory access did not synchronize; check power, range and permissions");
                        break;
                    }
                    ItemStack openedCarrier = currentCarrier(s);
                    if (!s.adapter.matches(mc.thePlayer.openContainer)
                        || !s.adapter.matchesCarrier(mc.thePlayer.openContainer, openedCarrier)
                        || !s.adapter.initializedCarrierMatches(s.carrierOriginal, openedCarrier))
                        throw new IllegalStateException("Opened inventory does not match the expected carrier");
                    s.carrierBefore = raw.fingerprint(s.adapter.stableCarrier(openedCarrier));
                    requireCarrier(s, true);
                    s.opened = mc.thePlayer.openContainer;
                    s.phase = Phase.TRANSFER;
                    break;
                case TRANSFER:
                    requireOpen(s);
                    if (s.adapter instanceof Ae2TerminalAdapter) {
                        if (!transferNetwork(s)) s.phase = Phase.CLOSE;
                    } else if (!transferBag(s)) s.phase = Phase.CLOSE;
                    break;
                case CLOSE:
                    requireOpen(s);
                    if (mc.thePlayer.inventory.getItemStack() != null)
                        throw new IllegalStateException("Inventory cursor is not empty");
                    mc.thePlayer.closeScreen();
                    s.opened = null;
                    s.phase = Phase.RETURN;
                    break;
                case RETURN:
                    if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer)
                        throw new IllegalStateException("Inventory did not close");
                    requireCarrier(s, false);
                    if (s.originalSlot >= 9) {
                        openPlayerInventory(s);
                        s.phase = Phase.RESTORE;
                    } else s.phase = Phase.NEXT;
                    break;
                case RESTORE:
                    requirePlayerInventoryScreen(s);
                    swapCarrier(s, s.originalSlot, s.heldSlot);
                    s.phase = Phase.CLOSE_RETURN_PLAYER;
                    break;
                case CLOSE_RETURN_PLAYER:
                    closePlayerInventory(s);
                    s.phase = Phase.NEXT;
                    break;
                case FINISH:
                    if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
                        || mc.thePlayer.inventory.getItemStack() != null)
                        throw new IllegalStateException("Inventory cleanup is incomplete");
                    if (!s.released) {
                        mc.thePlayer.inventory.currentItem = s.previousHotbar;
                        mc.playerController.updateController();
                        s.released = true;
                        ActionPacketDispatch.afterPendingWrites(mc, () -> release(s));
                        break;
                    }
                    if (!guard.isReadyForSession()) break;
                    if (s.unloading && !s.moved && s.cargoRemaining) throw new IllegalStateException(
                        "Bag cargo remains but cannot fit in player inventory; free space or check final storage capacity");
                    if (!s.unloading) {
                        if (prepared.size() >= 256) {
                            prepared.clear();
                        }
                        prepared.put(
                            s.task,
                            new InventoryPreparationTracker(preparationSnapshot(s.policy), mc.thePlayer.ticksExisted));
                    } else if (!s.moved) {
                        if (drained.size() >= 256) drained.clear();
                        drained.put(s.task, signature());
                    }
                    active = null;
                    diagnostic = s.unloading ? "Bag cargo staged for final storage"
                        : "Inventory checked; available supplies staged and surplus packed";
                    return null;
                default:
                    throw new IllegalStateException("Unknown inventory phase");
            }
            pacing.actionPerformed();
            return waiting(context, "Preparing " + (s.adapter == null ? "extended inventory" : s.adapter.id()));
        } catch (RuntimeException | LinkageError failure) {
            String detail = failure.getMessage() == null ? failure.getClass()
                .getSimpleName() : failure.getMessage();
            stop();
            diagnostic = detail;
            return StepResult.blocked(
                context.getActionEpoch(),
                context.getCheckpoint(),
                BlockedReason.missingRequirement(
                    "Extended inventory: " + detail,
                    context.getSpec()
                        .getId(),
                    "confirmed inventory access",
                    "Check the open inventory and cursor, close it, then resume the task."));
        }
    }

    private boolean transferBag(Session s) {
        List<Slot> storage = s.adapter.storageSlots(s.opened);
        List<Slot> player = playerSlots(s.opened);
        Map<String, ItemStack> items = new HashMap<>();
        for (Slot slot : all(player, storage)) if (slot.getStack() != null) items.put(
            key(raw.fingerprint(slot.getStack())),
            slot.getStack()
                .copy());
        List<InventorySlot> playerModel = model(player, s, items, false);
        List<InventorySlot> bagModel = model(storage, s, items, true);
        List<LoadoutReservation> required = s.unloading ? new ArrayList<>()
            : s.policy.workingReservations(mc.thePlayer.inventory.mainInventory);
        if (!s.unloading) {
            for (Slot slot : storage) {
                ItemStack stack = slot.getStack();
                int desired = s.policy.requiredCount(stack);
                if (desired > 0 && !s.policy.alreadyEquipped(stack, mc.thePlayer.inventory.mainInventory)) {
                    ItemFingerprint item = raw.fingerprint(stack);
                    required.add(
                        new LoadoutReservation(
                            "supply-" + slot.slotNumber,
                            LoadoutRole.OTHER_RESERVED,
                            item.getItemId(),
                            item.getMetadata(),
                            item.getDataHash(),
                            desired));
                }
            }
        } else {
            for (Slot slot : storage) if (cargo(s, slot.getStack())) {
                ItemFingerprint item = raw.fingerprint(slot.getStack());
                int existing = count(mc.thePlayer.inventory.mainInventory, item);
                required.add(
                    new LoadoutReservation(
                        "cargo-" + slot.slotNumber,
                        LoadoutRole.OTHER_RESERVED,
                        item.getItemId(),
                        item.getMetadata(),
                        item.getDataHash(),
                        Math.addExact(existing, item.getCount())));
            }
        }
        GeneralizedInventoryPlanner.Plan plan = planner.plan(
            Arrays.asList(
                new InventoryEndpoint("player", InventoryEndpoint.Kind.PLAYER, true, true, true, null, playerModel),
                new InventoryEndpoint(
                    "bag",
                    InventoryEndpoint.Kind.PORTABLE,
                    true,
                    true,
                    true,
                    s.heldSlot < 0 ? null
                        : new InventoryLocation("player", playerSlot(s.opened, s.heldSlot).slotNumber),
                    bagModel)),
            required,
            item -> s.unloading || s.policy.protectedForPlanner(items.get(key(item))),
            item -> !s.unloading && s.policy.stowForPlanner(items.get(key(item))));
        if (plan.getMoves()
            .isEmpty()) {
            if (s.unloading && !required.isEmpty()) s.cargoRemaining = true;
            return false;
        }
        GeneralizedInventoryPlanner.Move move = plan.getMoves()
            .get(0);
        Slot source = (Slot) s.opened.inventorySlots.get(
            move.getSource()
                .getSlot());
        Slot target = (Slot) s.opened.inventorySlots.get(
            move.getDestination()
                .getSlot());
        ItemStack stack = source.getStack();
        if (!source.canTakeStack(mc.thePlayer) || !target.isItemValid(stack))
            throw new IllegalStateException("The inventory filter changed before transfer");
        ContainerSnapshot before = snapshots.captureCurrent(mc, 0);
        s.transaction = new ContainerTransaction(
            "inventory-" + ++sequence,
            s.epoch,
            InventoryTransferClicks.move(
                "inventory-" + sequence,
                before,
                source.slotNumber,
                target.slotNumber,
                Math.min(stack.getMaxStackSize(), target.getSlotStackLimit()),
                move.getCount()));
        executor.begin(s.transaction);
        s.moved = true;
        return true;
    }

    private List<InventorySlot> model(List<Slot> slots, Session s, Map<String, ItemStack> items, boolean bag) {
        List<InventorySlot> result = new ArrayList<>();
        for (Slot slot : slots) {
            boolean fixed = slot.inventory == mc.thePlayer.inventory
                && (slot.getSlotIndex() == s.heldSlot || slot.getSlotIndex() == s.originalSlot
                    || adapters.find(slot.getStack()) != null);
            result.add(
                new InventorySlot(
                    slot.slotNumber,
                    raw.fingerprint(slot.getStack()),
                    item -> Math.min(
                        slot.getSlotStackLimit(),
                        items.get(key(item))
                            .getMaxStackSize()),
                    item -> !fixed && items.containsKey(key(item))
                        && slot.isItemValid(items.get(key(item)))
                        && (!bag || s.adapter.canStore(currentCarrier(s), items.get(key(item)))),
                    bag && "forestry-backpack".equals(s.adapter.id()) ? 100 : 0,
                    !fixed && slot.canTakeStack(mc.thePlayer)));
        }
        return result;
    }

    private boolean transferNetwork(Session s) {
        Ae2TerminalAdapter terminal = (Ae2TerminalAdapter) s.adapter;
        if (!terminal.powered(mc.currentScreen))
            throw new IllegalStateException("AE2 terminal is unpowered or out of range");
        // Network storage is final storage; never drain its entire contents as though it were a carried bag.
        if (s.unloading) return false;
        for (ItemStack stack : terminal.readContents(mc.currentScreen)) {
            if (s.policy.requiredCount(stack) <= count(mc.thePlayer.inventory.mainInventory, raw.fingerprint(stack))
                || s.policy.alreadyEquipped(stack, mc.thePlayer.inventory.mainInventory)) continue;
            int capacity = capacityFor(stack, s);
            int expected = Math.min(Math.min(stack.stackSize, stack.getMaxStackSize()), capacity);
            if (expected <= 0) continue;
            beginNetwork(s, stack, expected, false);
            if (!terminal.requestStack(mc, stack))
                throw new IllegalStateException("AE2 did not accept the item request");
            return true;
        }
        // Packing into AE2 uses only ordinary player slots; no virtual row is treated as a slot.
        for (Slot slot : playerSlots(s.opened)) {
            ItemStack stack = slot.getStack();
            if (slot.getSlotIndex() == s.heldSlot || slot.getSlotIndex() == s.originalSlot
                || adapters.find(stack) != null
                || !s.policy.mayDepositWholeStack(mc.thePlayer.inventory.mainInventory, slot.getSlotIndex())
                || !terminal.canDeposit(s.opened, slot)) continue;
            ContainerSnapshot before = snapshots.captureCurrent(mc, 0);
            List<ItemFingerprint> slots = new ArrayList<>(before.getSlots());
            slots.set(slot.slotNumber, null);
            ContainerSnapshot after = new ContainerSnapshot(
                before.getWindowId(),
                before.getContainerType(),
                before.getSlotLayout(),
                1,
                slots,
                null);
            s.transaction = new ContainerTransaction(
                "network-deposit-" + ++sequence,
                s.epoch,
                Collections.singletonList(
                    new VerifiedContainerClick("network-deposit-" + sequence, slot.slotNumber, 0, 1, before, after)));
            executor.begin(s.transaction);
            s.moved = true;
            return true;
        }
        return false;
    }

    private void beginNetwork(Session s, ItemStack item, int count, boolean deposit) {
        s.networkItem = raw.fingerprint(item);
        s.networkStack = item.copy();
        s.networkAvailableBefore = ((Ae2TerminalAdapter) s.adapter).availableCount(mc.currentScreen, item);
        if (s.networkAvailableBefore < count)
            throw new IllegalStateException("AE2 source contents changed before the request");
        s.networkBefore = mainSnapshot();
        s.networkCount = count;
        s.networkDeposit = deposit;
        s.networkTick = mc.thePlayer.ticksExisted;
        s.networkSyncRevision = packets.inventorySyncRevision(s.opened.windowId);
    }

    private StepResult observeNetwork(TaskStepContext context, Session s) {
        requireOpen(s);
        if (!((Ae2TerminalAdapter) s.adapter).powered(mc.currentScreen))
            throw new IllegalStateException("AE2 access disappeared during transfer");
        if (mc.thePlayer.inventory.getItemStack() != null)
            throw new IllegalStateException("AE2 left an unexpected cursor stack");
        Map<String, Integer> expected = new HashMap<>(s.networkBefore);
        String key = key(s.networkItem);
        int after = expected.getOrDefault(key, 0) + (s.networkDeposit ? -s.networkCount : s.networkCount);
        if (after == 0) expected.remove(key);
        else expected.put(key, after);
        Map<String, Integer> current = mainSnapshot();
        long available = ((Ae2TerminalAdapter) s.adapter).availableCount(mc.currentScreen, s.networkStack);
        if (expected.equals(current) && available == s.networkAvailableBefore - s.networkCount
            && packets.inventorySyncRevision(s.opened.windowId) > s.networkSyncRevision
            && mc.thePlayer.ticksExisted - s.networkTick >= 3) {
            s.networkItem = null;
            s.moved = true;
            pacing.actionPerformed();
            return waiting(context, "AE2 transfer observed in the player inventory");
        }
        if (mc.thePlayer.ticksExisted - s.networkTick > 100) throw new IllegalStateException(
            "AE2 transfer was not confirmed; permissions, capacity or range may have changed");
        return waiting(context, "Waiting for AE2 inventory synchronization");
    }

    private int capacityFor(ItemStack stack, Session s) {
        int capacity = 0;
        for (int i = 0; i < 36; i++) {
            if (i == s.heldSlot) continue;
            ItemStack current = mc.thePlayer.inventory.mainInventory[i];
            if (current == null) capacity += stack.getMaxStackSize();
            else if (current.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(current, stack))
                capacity += Math.max(0, stack.getMaxStackSize() - current.stackSize);
        }
        return capacity;
    }

    private void swapCarrier(Session s, int inventorySlot, int hotbar) {
        requirePlayerInventoryScreen(s);
        Container c = mc.thePlayer.inventoryContainer;
        Slot source = playerSlot(c, inventorySlot);
        Slot destination = playerSlot(c, hotbar);
        ContainerSnapshot before = snapshots.captureCurrent(mc, 0);
        ItemStack from = source.getStack();
        ItemStack to = destination.getStack();
        if (from != null && (!source.canTakeStack(mc.thePlayer) || !destination.isItemValid(from))
            || to != null && (!destination.canTakeStack(mc.thePlayer) || !source.isItemValid(to)))
            throw new IllegalStateException("The player slots do not allow staging this carrier");
        int sourceLimit = Math.min(source.getSlotStackLimit(), to == null ? 64 : to.getMaxStackSize());
        int destinationLimit = Math.min(destination.getSlotStackLimit(), from == null ? 64 : from.getMaxStackSize());
        s.transaction = new ContainerTransaction(
            "carrier-" + ++sequence,
            s.epoch,
            InventoryTransferClicks.swap(
                "carrier-" + sequence,
                before,
                source.slotNumber,
                destination.slotNumber,
                sourceLimit,
                destinationLimit));
        // Staging preserves identity and contents, allowing only equivalent empty Forestry encodings.
        executor.begin(s.transaction);
    }

    static String preparationStartWait(boolean screenOpen, boolean playerInventory, boolean cursorEmpty,
        ContainerActionPacing pacing) {
        // Start/resume can come from a dashboard that stays open. Wait before acquiring a lease or
        // recording carrier locations; the operator can still be moving inventory items here.
        if (screenOpen || !playerInventory || !cursorEmpty) {
            pacing.actionPerformed();
            return !cursorEmpty ? "Waiting for the cursor item to be placed before preparing supplies"
                : "Close the current screen to continue; inventory preparation is waiting";
        }
        return pacing.isReady() ? null : "Waiting five clear ticks before preparing supplies";
    }

    private void openPlayerInventory(Session s) {
        if (mc.currentScreen != null || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
            || mc.thePlayer.inventory.getItemStack() != null)
            throw new IllegalStateException("Close the current screen and clear the cursor before staging a carrier");
        if (mc.playerController.isInCreativeMode())
            throw new IllegalStateException("Carrier staging requires the survival player inventory screen");
        // Follow Minecraft's inventory-key path, including the server notification used by integrations.
        mc.getNetHandler()
            .addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
        s.playerScreen = mc.currentScreen;
        requirePlayerInventoryScreen(s);
    }

    private void requirePlayerInventoryScreen(Session s) {
        if (s.playerScreen == null || mc.currentScreen != s.playerScreen
            || !(mc.currentScreen instanceof GuiContainer)
            || ((GuiContainer) mc.currentScreen).inventorySlots != mc.thePlayer.inventoryContainer
            || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer)
            throw new IllegalStateException("Player inventory screen closed or changed during carrier staging");
    }

    private void closePlayerInventory(Session s) {
        requirePlayerInventoryScreen(s);
        if (mc.thePlayer.inventory.getItemStack() != null)
            throw new IllegalStateException("Cannot close player inventory while a carrier transfer holds the cursor");
        mc.thePlayer.closeScreen();
        s.playerScreen = null;
    }

    private void requireInteractionScreen() {
        Session s = active;
        if (s == null) throw new IllegalStateException("Inventory session is unavailable");
        if (s.opened == null) requirePlayerInventoryScreen(s);
        else if (!(mc.currentScreen instanceof GuiContainer)
            || ((GuiContainer) mc.currentScreen).inventorySlots != s.opened
            || mc.thePlayer.openContainer != s.opened)
            throw new IllegalStateException("Bag inventory screen closed or changed during transfer");
    }

    private ItemStack projectCarrier(Slot slot, ItemStack stack) {
        Session s = active;
        return s != null && s.opened != null
            && s.adapter != null
            && s.heldSlot >= 0
            && slot.inventory == mc.thePlayer.inventory
            && slot.getSlotIndex() == s.heldSlot ? s.adapter.stableCarrier(stack) : stack;
    }

    private void requireOpen(Session s) {
        if (s.opened == null || mc.thePlayer.openContainer != s.opened)
            throw new IllegalStateException("Inventory window changed");
        requireInteractionScreen();
        requireCarrier(s, true);
    }

    private void requireCarrier(Session s, boolean open) {
        ItemStack carrier = currentCarrier(s);
        if (!s.adapter.supports(carrier) || !s.carrierBefore.equals(raw.fingerprint(s.adapter.stableCarrier(carrier))))
            throw new IllegalStateException("Inventory carrier identity changed");
        if (open && (!s.adapter.matches(mc.thePlayer.openContainer)
            || !s.adapter.matchesCarrier(mc.thePlayer.openContainer, carrier)))
            throw new IllegalStateException("The server opened an inventory belonging to another carrier");
    }

    private ItemStack currentCarrier(Session s) {
        return s.heldSlot < 0 ? PortableInventoryAdapters.wornAdventureBackpack(mc)
            : mc.thePlayer.inventory.mainInventory[s.heldSlot];
    }

    private boolean cargo(Session s, ItemStack stack) {
        return adapters.find(stack) == null && s.policy.unloadCargo(stack);
    }

    private List<Carrier> carriers() {
        return carriers(true);
    }

    private List<Carrier> carriers(boolean includeNetworks) {
        List<Carrier> result = new ArrayList<>();
        for (int i = 0; i < 36; i++)
            if (adapters.find(mc.thePlayer.inventory.mainInventory[i]) != null) result.add(new Carrier(i));
        if (PortableInventoryAdapters.wornAdventureBackpack(mc) != null) result.add(new Carrier(-1));
        if (!includeNetworks)
            result.removeIf(carrier -> adapters.find(carrier.stack(mc)) instanceof Ae2TerminalAdapter);
        result.sort(Comparator.comparingInt(c -> {
            PortableInventoryAdapter adapter = adapters.find(c.stack(mc));
            return adapter.id()
                .contains("forestry") ? 0 : adapter instanceof Ae2TerminalAdapter ? 2 : 1;
        }));
        return result;
    }

    private InventoryPreparationTracker.Snapshot preparationSnapshot(TaskInventoryPolicy policy) {
        return InventoryPreparationTracker.observe(
            mc.thePlayer.inventory.mainInventory,
            PortableInventoryAdapters.wornAdventureBackpack(mc),
            policy,
            raw,
            stack -> {
                PortableInventoryAdapter adapter = adapters.find(stack);
                if (adapter == null) return null;
                ItemFingerprint item = raw.fingerprint(adapter.stableCarrier(stack));
                // Bag auto-pickup changes nested NBT without creating any need to open it.
                // Exact carrier/configuration binding is still enforced when an action actually runs.
                return adapter instanceof Ae2TerminalAdapter ? item.toString()
                    : adapter.id() + ":" + item.getItemId() + ":" + item.getMetadata() + ":" + adapter.identity(stack);
            });
    }

    private String signature() {
        StringBuilder signature = new StringBuilder();
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack == null) {
                signature.append("empty;");
                continue;
            }
            ItemFingerprint item = raw.fingerprint(stack);
            signature.append(item.getItemId())
                .append(':');
            PortableInventoryAdapter adapter = adapters.find(stack);
            if (adapter != null) signature
                .append(raw.fingerprint(adapter instanceof Ae2TerminalAdapter ? adapter.stableCarrier(stack) : stack));
            // Ordinary tool wear does not justify reopening bags each block; broken tools do.
            if (TaskInventoryPolicy.isTool(stack)) signature.append(TaskInventoryPolicy.usableTool(stack));
            if (!TaskInventoryPolicy.isTool(stack)) signature.append(item.getMetadata());
            signature.append('/')
                .append(stack.stackSize / 16)
                .append(';');
        }
        ItemStack worn = PortableInventoryAdapters.wornAdventureBackpack(mc);
        if (worn != null) signature.append("worn:")
            .append(raw.fingerprint(worn));
        return signature.toString();
    }

    private List<Slot> playerSlots(Container container) {
        List<Slot> result = new ArrayList<>();
        for (Object value : container.inventorySlots) {
            Slot slot = (Slot) value;
            if (slot.inventory == mc.thePlayer.inventory && slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 36)
                result.add(slot);
        }
        if (result.size() != 36) throw new IllegalStateException("Container does not expose the 36 player slots");
        return result;
    }

    private Slot playerSlot(Container container, int index) {
        for (Slot slot : playerSlots(container)) if (slot.getSlotIndex() == index) return slot;
        throw new IllegalStateException("Missing player inventory slot " + index);
    }

    private static List<Slot> all(List<Slot> first, List<Slot> second) {
        List<Slot> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private Map<String, Integer> mainSnapshot() {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack == null || adapters.find(stack) != null) continue;
            String key = key(raw.fingerprint(stack));
            counts.put(key, Math.addExact(counts.getOrDefault(key, 0), stack.stackSize));
        }
        return counts;
    }

    private int count(ItemStack[] inventory, ItemFingerprint item) {
        int count = 0;
        for (ItemStack stack : inventory) if (stack != null && item.hasSameIdentity(raw.fingerprint(stack)))
            count = Math.addExact(count, stack.stackSize);
        return count;
    }

    private static String key(ItemFingerprint item) {
        return item.getItemId() + ':' + item.getMetadata() + '#' + item.getDataHash();
    }

    private StepResult waiting(TaskStepContext context, String message) {
        diagnostic = message;
        return StepResult.waitFor(context.getActionEpoch(), context.getCheckpoint(), 0, message);
    }

    private void requireClient() {
        if (!mc.func_152345_ab() || mc.thePlayer == null || mc.theWorld == null)
            throw new IllegalStateException("A joined client thread is required for extended inventories");
    }

    private synchronized void release(Session session) {
        guard.quarantine(session.lease);
        guard.end(session.lease);
        session.lease.close();
    }

    private void stop() {
        Session s = active;
        if (s == null) return;
        executor.cancel("Extended inventory preparation interrupted; never replay an uncertain click");
        release(s);
        active = null;
    }

    public synchronized void close() {
        stop();
        prepared.clear();
        drained.clear();
    }

    @Override
    public synchronized void interrupt(String taskId, TaskInterruption interruption) {
        prepared.remove(taskId);
        drained.remove(taskId);
        if (active != null && active.task.equals(taskId)) stop();
    }

    @Override
    public synchronized StepResult reconcileInterruptedPreparation(TaskStepContext context, boolean completingUnload) {
        requireClient();
        if (active == null && !executor.isActive()
            && guard.isReadyForSession()
            && mc.thePlayer.openContainer == mc.thePlayer.inventoryContainer
            && mc.thePlayer.inventory.getItemStack() == null) {
            // Re-observe current state; neither an old opening request nor an old click chain is retained.
            signature();
            return null;
        }
        return InventoryService.super.reconcileInterruptedPreparation(context, completingUnload);
    }

    private final class Session {

        final Object world = mc.theWorld, player = mc.thePlayer;

        final String task;
        final long epoch, started = System.nanoTime();
        long networkSyncRevision, openSyncRevision, networkAvailableBefore;
        final boolean unloading;
        final ActionLease lease;
        final List<Carrier> carriers;
        final TaskInventoryPolicy policy;
        final int previousHotbar;
        int index, originalSlot, heldSlot, openedAt, networkCount, networkTick;
        boolean moved, released, networkDeposit, cargoRemaining;
        ItemStack carrierOriginal, networkStack;
        Phase phase = Phase.NEXT;
        Carrier carrier;
        PortableInventoryAdapter adapter;
        ItemFingerprint carrierBefore, networkItem;
        Container opened;
        GuiScreen playerScreen;
        ContainerTransaction transaction;
        Map<String, Integer> networkBefore;

        Session(TaskStepContext context, boolean unloading, ActionLease lease) {
            this.task = context.getSpec()
                .getId();
            this.epoch = context.getActionEpoch();
            this.unloading = unloading;
            this.lease = lease;
            carriers = carriers(!unloading);
            policy = new TaskInventoryPolicy(context.getSpec(), profile.get());
            previousHotbar = mc.thePlayer.inventory.currentItem;
        }
    }

    private static final class Carrier {

        final int slot;

        Carrier(int slot) {
            this.slot = slot;
        }

        ItemStack stack(Minecraft mc) {
            return slot < 0 ? PortableInventoryAdapters.wornAdventureBackpack(mc)
                : mc.thePlayer.inventory.mainInventory[slot];
        }
    }
}
