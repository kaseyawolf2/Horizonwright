package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.forge.client.ClientBootstrap;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend;

/** Exact vanilla/CropsNH approach, non-destructive harvest, and immutable postcondition backend. */
public final class LiveVanillaFarmBackend implements FarmBackend {

    public interface NavigationSource {

        NavigationBackend getNavigationBackend();
    }

    private static final EnumSet<ActionCapability> BREAK_REPLANT = EnumSet.of(
        ActionCapability.MOVEMENT,
        ActionCapability.LOOK,
        ActionCapability.DIG,
        ActionCapability.PLACE,
        ActionCapability.HELD_USE);
    private static final EnumSet<ActionCapability> RIGHT_CLICK_HARVEST = EnumSet
        .of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.USE, ActionCapability.CONTAINER);
    private static final String CROPS_NH_SPADE = "com.gtnewhorizon.cropsnh.items.tools.ItemSpade";
    private static final String CROPS_NH_REINFORCED_SPADE = "com.gtnewhorizon.cropsnh.items.tools.ItemReinforcedSpade";
    private static final long ACTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30L);

    private final Minecraft minecraft;
    private final ActionSessionGuard guard;
    private final NavigationSource navigationSource;
    private final MinecraftVanillaFarmObserver observer;
    private final VanillaFarmMutationVerifier verifier = new VanillaFarmMutationVerifier();
    private LiveHandle active;

    public LiveVanillaFarmBackend(Minecraft minecraft, ActionSessionGuard guard, NavigationSource navigationSource,
        ProfileFarmConfiguration configuration) {
        if (minecraft == null || guard == null || navigationSource == null || configuration == null) {
            throw new IllegalArgumentException("complete live vanilla farm dependencies are required");
        }
        this.minecraft = minecraft;
        this.guard = guard;
        this.navigationSource = navigationSource;
        this.observer = new MinecraftVanillaFarmObserver(minecraft, configuration);
    }

    @Override
    public Availability availability() {
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        if (navigation == null) {
            DevelopmentTrace.event("farm-live", "availability", "available", false, "reason", "no-navigation-backend");
            return Availability.unavailable("No navigation backend is configured for farming");
        }
        BackendAvailability status = navigation.availability();
        Availability result = status.isAvailable()
            ? Availability.available("Exact vanilla and CropsNH farm actions ready through " + status.getDiagnostic())
            : Availability.unavailable("Farm navigation unavailable: " + status.getDiagnostic());
        DevelopmentTrace.event(
            "farm-live",
            "availability",
            "available",
            result.isAvailable(),
            "diagnostic",
            result.getDiagnostic());
        return result;
    }

    @Override
    public PassSnapshot scan(ScanRequest request) {
        requireClient(request);
        NamedArea plot = observer.resolvePlot(request.getPlotId());
        List<CropObservation> observations = observer.scan(plot);
        DevelopmentTrace.event(
            "farm-live",
            "scan",
            "task",
            request.getTaskId(),
            "plot",
            request.getPlotId(),
            "epoch",
            request.getActionEpoch(),
            "observations",
            observations.size(),
            "bounds",
            plot);
        for (int index = 0; index < observations.size(); index++) {
            traceCrop("scan-crop", request.getTaskId(), index, observations.get(index));
        }
        return new PassSnapshot(request.getTaskId(), request.getActionEpoch(), plot, observations);
    }

    @Override
    public TargetSnapshot observe(TargetRequest request) {
        requireClient(request);
        CropObservation crop = observer.observeRequired(request.getPosition());
        SeedReserveEvidence reserve = observer
            .reserve(request.getPassRevision(), crop.getRequiredSeedFingerprint(), request.getMinimumSeedReserve());
        traceCrop("target-observed", request.getTaskId(), request.getObservationIndex(), crop);
        DevelopmentTrace.event(
            "farm-live",
            "seed-reserve",
            "task",
            request.getTaskId(),
            "passRevision",
            request.getPassRevision(),
            "inventoryRevision",
            reserve.getInventoryRevision(),
            "available",
            reserve.getAvailableSeeds(),
            "minimum",
            reserve.getMinimumReserve(),
            "seed",
            reserve.getSeedFingerprint(),
            "inventory",
            reserve.getInventoryFingerprint());
        return new TargetSnapshot(
            request.getTaskId(),
            request.getPassRevision(),
            request.getActionEpoch(),
            request.getObservationIndex(),
            crop,
            reserve);
    }

    @Override
    public synchronized ActionHandle execute(ActionRequest request, ActionLease lease) {
        requireClient(request);
        DevelopmentTrace.event(
            "farm-live",
            "execute-request",
            "request",
            request.getRequestId(),
            "task",
            request.getTaskId(),
            "epoch",
            request.getActionEpoch(),
            "passRevision",
            request.getPassRevision(),
            "index",
            request.getObservationIndex(),
            "action",
            request.getDecision()
                .getAction(),
            "target",
            request.getDecision()
                .getTarget(),
            "plannedFingerprint",
            request.getDecision()
                .getObservationFingerprint(),
            "leaseValid",
            lease != null && lease.isValid(),
            "leaseCapabilities",
            lease == null ? "none" : lease.getCapabilities());
        FarmActionKind action = request.getDecision()
            .getAction();
        if (action != FarmActionKind.BREAK_AND_REPLANT && action != FarmActionKind.RIGHT_CLICK_HARVEST)
            throw new IllegalArgumentException("live vanilla backend supports only verified crop mutations");
        EnumSet<ActionCapability> requiredCapabilities = action == FarmActionKind.BREAK_AND_REPLANT ? BREAK_REPLANT
            : RIGHT_CLICK_HARVEST;
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || !lease.getCapabilities()
                .containsAll(requiredCapabilities)) {
            throw new IllegalArgumentException("matching farm action authority is required");
        }
        if (active != null && !active.isTerminal()) throw new IllegalStateException("another farm action is active");
        CropObservation current = observer.observeRequired(
            request.getDecision()
                .getTarget());
        if (!request.getDecision()
            .getObservationFingerprint()
            .equals(current.getObservationFingerprint())) {
            throw new IllegalStateException("farm target changed after planning");
        }
        SeedReserveEvidence reserve = observer.reserve(
            request.getPassRevision(),
            request.getDecision()
                .getRequiredSeedFingerprint(),
            request.getDecision()
                .getReserveEvidence()
                .getMinimumReserve());
        if (action == FarmActionKind.BREAK_AND_REPLANT && !request.getDecision()
            .getReserveEvidence()
            .isSameSnapshot(reserve)) {
            throw new IllegalStateException("seed inventory changed after planning");
        }
        int seedSlot = action == FarmActionKind.BREAK_AND_REPLANT ? observer.findHotbarSeed(
            request.getDecision()
                .getRequiredSeedFingerprint())
            : -1;
        if (action == FarmActionKind.BREAK_AND_REPLANT && seedSlot < 0)
            throw new IllegalStateException("an exact approved replant seed must be present in the hotbar");
        boolean cropsNhHarvest = action == FarmActionKind.RIGHT_CLICK_HARVEST
            && current.getFamily() == CropFamily.CROPS_NH;
        int spadeHotbarSlot = cropsNhHarvest ? findCropsNhSpade(0, 9) : -1;
        int stagedSpadeInventorySlot = cropsNhHarvest && spadeHotbarSlot < 0 ? findCropsNhSpade(9, 36) : -1;
        boolean requiresEmptyHand = action == FarmActionKind.RIGHT_CLICK_HARVEST && spadeHotbarSlot < 0
            && stagedSpadeInventorySlot < 0;
        int harvestHandSlot = spadeHotbarSlot >= 0 ? spadeHotbarSlot
            : stagedSpadeInventorySlot >= 0 ? chooseEvacuationHotbarSlot()
                : action == FarmActionKind.RIGHT_CLICK_HARVEST ? findEmptyHotbarSlot() : -1;
        int emptyInventorySlot = -1;
        if (requiresEmptyHand && harvestHandSlot < 0) {
            emptyInventorySlot = findEmptyMainInventorySlot();
            if (emptyInventorySlot < 0) throw new IllegalStateException(
                "right-click crop harvesting requires an empty hotbar slot or main-inventory space");
            harvestHandSlot = chooseEvacuationHotbarSlot();
        }
        verifier.requireCurrent(
            request.getDecision(),
            current,
            reserve,
            action == FarmActionKind.BREAK_AND_REPLANT ? request.getDecision()
                .getRequiredSeedFingerprint() : null);
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        Availability available = availability();
        if (navigation == null || !available.isAvailable()) throw new IllegalStateException(available.getDiagnostic());
        LiveHandle handle = new LiveHandle(
            request,
            lease,
            navigation,
            seedSlot,
            harvestHandSlot,
            emptyInventorySlot,
            stagedSpadeInventorySlot,
            requiresEmptyHand,
            current,
            System.nanoTime());
        DevelopmentTrace.event(
            "farm-live",
            "execute-validated",
            "request",
            request.getRequestId(),
            "seedSlot",
            seedSlot,
            "harvestHandSlot",
            harvestHandSlot,
            "emptyInventorySlot",
            emptyInventorySlot,
            "stagedSpadeInventorySlot",
            stagedSpadeInventorySlot,
            "requiresEmptyHand",
            requiresEmptyHand,
            "priorSlot",
            minecraft.thePlayer.inventory.currentItem,
            "playerX",
            minecraft.thePlayer.posX,
            "playerY",
            minecraft.thePlayer.posY,
            "playerZ",
            minecraft.thePlayer.posZ);
        active = handle;
        try {
            handle.start();
            return handle;
        } catch (RuntimeException failure) {
            active = null;
            handle.cancel();
            throw failure;
        }
    }

    private void requireClient(Object request) {
        if (request == null) throw new IllegalArgumentException("farm request is required");
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.playerController == null) {
            throw new IllegalStateException("live farm work requires a joined Minecraft client thread");
        }
    }

    private synchronized void clearActive(LiveHandle handle) {
        if (active == handle) active = null;
    }

    private int findEmptyHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (minecraft.thePlayer.inventory.mainInventory[slot] == null) return slot;
        }
        return -1;
    }

    private int findEmptyMainInventorySlot() {
        for (int slot = 9; slot < 36; slot++) {
            if (minecraft.thePlayer.inventory.mainInventory[slot] == null) return slot;
        }
        return -1;
    }

    private int chooseEvacuationHotbarSlot() {
        int selected = minecraft.thePlayer.inventory.currentItem;
        for (int slot = 8; slot >= 0; slot--) {
            if (slot != selected) return slot;
        }
        return selected;
    }

    private int findCropsNhSpade(int startInclusive, int endExclusive) {
        int reinforced = findExactItemClass(CROPS_NH_REINFORCED_SPADE, startInclusive, endExclusive);
        return reinforced >= 0 ? reinforced : findExactItemClass(CROPS_NH_SPADE, startInclusive, endExclusive);
    }

    private int findExactItemClass(String className, int startInclusive, int endExclusive) {
        for (int slot = startInclusive; slot < endExclusive; slot++) {
            ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
            if (stack != null && stack.getItem() != null
                && className.equals(
                    stack.getItem()
                        .getClass()
                        .getName()))
                return slot;
        }
        return -1;
    }

    private boolean isCropsNhSpadeAt(int slot) {
        return slot >= 0 && slot < 36 && isCropsNhSpade(minecraft.thePlayer.inventory.mainInventory[slot]);
    }

    static boolean isCropsNhSpade(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String className = stack.getItem()
            .getClass()
            .getName();
        return isCropsNhSpadeClassName(className);
    }

    static boolean isCropsNhSpadeClassName(String className) {
        return CROPS_NH_SPADE.equals(className) || CROPS_NH_REINFORCED_SPADE.equals(className);
    }

    private final class LiveHandle implements ActionHandle {

        private final ActionRequest request;
        private final ActionLease lease;
        private final NavigationBackend navigation;
        private final int seedSlot;
        private final int harvestHandSlot;
        private final int emptyInventorySlot;
        private final int stagedSpadeInventorySlot;
        private final boolean requiresEmptyHand;
        private final CropObservation plannedBefore;
        private final int priorHotbarSlot;
        private final long deadlineNanos;
        private NavigationHandle navigationHandle;
        private Phase phase = Phase.APPROACHING;
        private ActionState state = ActionState.SUBMITTED;
        private String detail = "Preparing exact crop approach";
        private CropObservation confirmedAfter;
        private boolean ownsActionSession;
        private boolean replantDispatched;
        private boolean harvestDispatched;
        private boolean emptyHandPrepared;
        private boolean spadeStaged;
        private boolean slotChanged;
        private int collectionSettleTicks;
        private volatile boolean cancellationRequested;

        private LiveHandle(ActionRequest request, ActionLease lease, NavigationBackend navigation, int seedSlot,
            int harvestHandSlot, int emptyInventorySlot, int stagedSpadeInventorySlot, boolean requiresEmptyHand,
            CropObservation plannedBefore, long startedAtNanos) {
            this.request = request;
            this.lease = lease;
            this.navigation = navigation;
            this.seedSlot = seedSlot;
            this.harvestHandSlot = harvestHandSlot;
            this.emptyInventorySlot = emptyInventorySlot;
            this.stagedSpadeInventorySlot = stagedSpadeInventorySlot;
            this.requiresEmptyHand = requiresEmptyHand;
            this.plannedBefore = plannedBefore;
            this.priorHotbarSlot = minecraft.thePlayer.inventory.currentItem;
            this.deadlineNanos = saturatingAdd(startedAtNanos, ACTION_TIMEOUT_NANOS);
        }

        private void start() {
            if (canReachCrop()) {
                phase = Phase.WAITING_FOR_ACTION_SESSION;
                state = ActionState.EXECUTING;
                detail = "Crop is within confirmed reach";
                trace("phase", "reach", true);
                return;
            }
            BasePosition target = request.getDecision()
                .getTarget();
            navigationHandle = navigation.submit(
                NavigationRequest.adjacentTo(
                    request.getRequestId() + "-approach",
                    request.getActionEpoch(),
                    target.getDimensionId(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    System.nanoTime(),
                    ACTION_TIMEOUT_NANOS),
                lease);
            state = ActionState.EXECUTING;
            detail = "Approaching exact farm target";
            trace("phase", "navigationRequest", navigationHandle.getRequestId());
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public synchronized ActionProgress progress() {
            requireClient(request);
            trace(
                "progress",
                "leaseValid",
                lease.isValid(),
                "guardActive",
                guard.isActiveLease(lease),
                "guardReady",
                guard.isReadyForSession(),
                "cancelRequested",
                cancellationRequested,
                "playerX",
                minecraft.thePlayer.posX,
                "playerY",
                minecraft.thePlayer.posY,
                "playerZ",
                minecraft.thePlayer.posZ);
            if (isTerminal()) return snapshot();
            if (cancellationRequested) {
                cancelOnClientThread();
                return snapshot();
            }
            if (!lease.isValid()) {
                stopProducers();
                fail("Farm action lease was revoked");
                return snapshot();
            }
            if (System.nanoTime() - deadlineNanos >= 0L) {
                stopProducers();
                fail("Farm action deadline exceeded");
                return snapshot();
            }
            if (phase == Phase.APPROACHING) pollApproach();
            else if (phase == Phase.WAITING_FOR_ACTION_SESSION) beginActionWhenReady();
            else if (phase == Phase.BREAKING) breakOneTick();
            else if (phase == Phase.PLANTING) plantOnce();
            else if (phase == Phase.DISPATCHING_REPLANT) awaitReplantDispatch();
            else if (phase == Phase.WAITING_FOR_EMPTY_HAND) awaitEmptyHandPreparation();
            else if (phase == Phase.DISPATCHING_HARVEST) awaitHarvestDispatch();
            else if (phase == Phase.CONFIRMING) confirmReplacement();
            else if (phase == Phase.WAITING_FOR_COLLECTION_SESSION) beginCollectionWhenReady();
            else if (phase == Phase.COLLECTING) pollCollection();
            else if (phase == Phase.SETTLING_COLLECTION) settleCollection();
            return snapshot();
        }

        @Override
        public void cancel() {
            cancellationRequested = true;
            NavigationHandle moving;
            synchronized (this) {
                moving = navigationHandle;
            }
            if (moving != null) moving.cancel();
            if (minecraft.func_152345_ab()) cancelOnClientThread();
            else minecraft.func_152344_a(this::cancelOnClientThread);
        }

        private void pollApproach() {
            NavigationProgress progress = navigationHandle.progress();
            trace("approach", "navigationState", progress.getState(), "navigationDetail", progress.getDetail());
            if (progress.getState() == NavigationState.COMPLETED) {
                navigationHandle = null;
                phase = Phase.WAITING_FOR_ACTION_SESSION;
                detail = "Approach complete; waiting for packet drain";
            } else if (progress.getState() == NavigationState.FAILED) {
                fail("Could not approach farm target: " + progress.getDetail());
            } else if (progress.getState() == NavigationState.CANCELLED) {
                state = ActionState.CANCELLED;
                detail = "Farm approach was cancelled";
                clearActive(this);
            } else {
                detail = "Approaching farm target: " + progress.getDetail();
            }
        }

        private void beginActionWhenReady() {
            if (!guard.isReadyForSession()) {
                detail = "Waiting for approach packets to drain";
                trace("packet-drain", "blockedActions", guard.getBlockedActionCount());
                return;
            }
            CropObservation current = observer.observeSupported(
                request.getDecision()
                    .getTarget());
            boolean sameCrop = samePlannedCrop(current);
            boolean mature = current != null && current.isMature();
            boolean reachable = canReachCrop();
            DevelopmentTrace.event(
                "farm-live",
                "post-approach-check",
                "request",
                request.getRequestId(),
                "sameCrop",
                sameCrop,
                "mature",
                mature,
                "reachable",
                reachable,
                "currentFingerprint",
                current == null ? "missing" : current.getObservationFingerprint(),
                "plannedFingerprint",
                request.getDecision()
                    .getObservationFingerprint());
            if (!sameCrop || !mature || !reachable) {
                fail("Farm target changed or lost reach after approach");
                return;
            }
            SeedReserveEvidence reserve = observer.reserve(
                request.getPassRevision(),
                request.getDecision()
                    .getRequiredSeedFingerprint(),
                request.getDecision()
                    .getReserveEvidence()
                    .getMinimumReserve());
            FarmActionKind action = request.getDecision()
                .getAction();
            if (action == FarmActionKind.BREAK_AND_REPLANT && observer.findHotbarSeed(
                request.getDecision()
                    .getRequiredSeedFingerprint())
                != seedSlot) {
                fail("Seed inventory changed after approach");
                return;
            }
            if (action == FarmActionKind.RIGHT_CLICK_HARVEST && requiresEmptyHand
                && emptyInventorySlot < 0
                && minecraft.thePlayer.inventory.mainInventory[harvestHandSlot] != null) {
                fail("The reserved empty hotbar slot was filled before crop harvesting");
                return;
            }
            if (action == FarmActionKind.RIGHT_CLICK_HARVEST && requiresEmptyHand
                && emptyInventorySlot >= 0
                && minecraft.thePlayer.inventory.mainInventory[emptyInventorySlot] != null) {
                fail("The reserved main-inventory slot was filled before crop harvesting");
                return;
            }
            if (stagedSpadeInventorySlot >= 0 && !isCropsNhSpadeAt(stagedSpadeInventorySlot)) {
                fail("The reserved CropsNH spade moved before crop harvesting");
                return;
            }
            try {
                verifier.requireCurrent(
                    request.getDecision(),
                    current,
                    reserve,
                    action == FarmActionKind.BREAK_AND_REPLANT ? request.getDecision()
                        .getRequiredSeedFingerprint() : null);
            } catch (RuntimeException changed) {
                fail(changed.getMessage());
                return;
            }
            guard.begin(lease);
            ownsActionSession = true;
            if (action == FarmActionKind.RIGHT_CLICK_HARVEST) {
                if (stagedSpadeInventorySlot >= 0) prepareSpadeHand();
                else if (!requiresEmptyHand || minecraft.thePlayer.inventory.mainInventory[harvestHandSlot] == null)
                    rightClickHarvest();
                else prepareEmptyHand();
                return;
            }
            ClientBootstrap.blockDamageShield()
                .acquire(request.getRequestId());
            phase = Phase.BREAKING;
            detail = "Breaking one exact mature crop";
            aimAt(
                request.getDecision()
                    .getTarget());
            BasePosition target = request.getDecision()
                .getTarget();
            minecraft.playerController.clickBlock(target.getX(), target.getY(), target.getZ(), targetSide());
            trace("break-start", "target", target);
        }

        private void prepareEmptyHand() {
            if (!guard.isActiveLease(lease)) {
                fail("Farm inventory session lost packet authority");
                return;
            }
            if (emptyInventorySlot < 9 || emptyInventorySlot >= 36
                || minecraft.thePlayer.inventory.mainInventory[emptyInventorySlot] != null) {
                fail("Reserved main-inventory space is no longer available");
                return;
            }
            if (minecraft.thePlayer.inventory.getItemStack() != null) {
                fail("Cannot prepare an empty hand while the player is carrying an inventory stack");
                return;
            }
            if (minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer) {
                fail("Cannot empty a hotbar slot while another container is open");
                return;
            }
            int windowId = minecraft.thePlayer.openContainer.windowId;
            int hotbarContainerSlot = 36 + harvestHandSlot;
            minecraft.playerController.windowClick(windowId, hotbarContainerSlot, 0, 0, minecraft.thePlayer);
            minecraft.playerController.windowClick(windowId, emptyInventorySlot, 0, 0, minecraft.thePlayer);
            if (minecraft.thePlayer.inventory.getItemStack() != null) {
                minecraft.playerController.windowClick(windowId, hotbarContainerSlot, 0, 0, minecraft.thePlayer);
                fail("Could not safely move a hotbar stack into main inventory");
                return;
            }
            phase = Phase.WAITING_FOR_EMPTY_HAND;
            detail = "Moving one hotbar stack into main inventory to empty the hand";
            trace(
                "empty-hand-move",
                "hotbarSlot",
                harvestHandSlot,
                "inventorySlot",
                emptyInventorySlot,
                "windowId",
                windowId);
            try {
                ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                    synchronized (LiveHandle.this) {
                        emptyHandPrepared = true;
                    }
                });
            } catch (RuntimeException failure) {
                fail("Could not synchronize the empty-hand inventory move: " + failure.getMessage());
            }
        }

        private void prepareSpadeHand() {
            if (!guard.isActiveLease(lease)) {
                fail("Farm inventory session lost packet authority");
                return;
            }
            if (!isCropsNhSpadeAt(stagedSpadeInventorySlot) || minecraft.thePlayer.inventory.getItemStack() != null
                || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer) {
                fail("Cannot safely stage the reserved CropsNH spade");
                return;
            }
            int windowId = minecraft.thePlayer.openContainer.windowId;
            minecraft.playerController
                .windowClick(windowId, stagedSpadeInventorySlot, harvestHandSlot, 2, minecraft.thePlayer);
            if (!isCropsNhSpadeAt(harvestHandSlot)) {
                fail("Could not move the CropsNH spade into the hotbar");
                return;
            }
            spadeStaged = true;
            phase = Phase.WAITING_FOR_EMPTY_HAND;
            detail = "Moving the CropsNH spade into the hotbar for seed-aware harvesting";
            trace(
                "spade-stage",
                "hotbarSlot",
                harvestHandSlot,
                "inventorySlot",
                stagedSpadeInventorySlot,
                "windowId",
                windowId);
            try {
                ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                    synchronized (LiveHandle.this) {
                        emptyHandPrepared = true;
                    }
                });
            } catch (RuntimeException failure) {
                fail("Could not synchronize the CropsNH spade move: " + failure.getMessage());
            }
        }

        private void awaitEmptyHandPreparation() {
            if (!emptyHandPrepared) {
                detail = "Waiting for the empty-hand inventory move to drain";
                return;
            }
            if (spadeStaged && !isCropsNhSpadeAt(harvestHandSlot)) {
                fail("CropsNH spade was not present after its inventory move");
                return;
            }
            if (!spadeStaged && requiresEmptyHand
                && minecraft.thePlayer.inventory.mainInventory[harvestHandSlot] != null) {
                fail("Hotbar slot remained occupied after the empty-hand inventory move");
                return;
            }
            rightClickHarvest();
        }

        private void rightClickHarvest() {
            BasePosition target = request.getDecision()
                .getTarget();
            minecraft.thePlayer.inventory.currentItem = harvestHandSlot;
            slotChanged = harvestHandSlot != priorHotbarSlot;
            minecraft.playerController.updateController();
            ItemStack held = MinecraftRuntimeAccess.heldItem(minecraft.thePlayer);
            if (requiresEmptyHand && held != null) {
                fail("Could not establish an empty hand for crop harvesting");
                return;
            }
            if (!requiresEmptyHand && !isCropsNhSpade(held)) {
                fail("Could not establish the CropsNH spade for crop harvesting");
                return;
            }
            aimAt(target);
            boolean accepted = minecraft.playerController.onPlayerRightClick(
                minecraft.thePlayer,
                minecraft.theWorld,
                held,
                target.getX(),
                target.getY(),
                target.getZ(),
                targetSide(),
                Vec3.createVectorHelper(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D));
            minecraft.thePlayer.swingItem();
            trace(
                "harvest-interaction",
                "accepted",
                accepted,
                "held",
                held == null ? "empty" : observer.hotbarMaterialIdentity(minecraft.thePlayer.inventory.currentItem));
            try {
                restoreSlot();
                returnStagedSpade();
            } catch (RuntimeException failure) {
                fail("Crop was harvested, but the CropsNH spade could not be returned: " + failure.getMessage());
                return;
            }
            phase = Phase.DISPATCHING_HARVEST;
            detail = accepted ? "Dispatching the non-destructive crop harvest"
                : "Crop right-click packet sent; waiting for its verified result";
            try {
                ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                    synchronized (LiveHandle.this) {
                        stopActionSession();
                        harvestDispatched = true;
                    }
                });
            } catch (RuntimeException failure) {
                stopActionSession();
                fail("Could not dispatch the mature-crop right-click: " + failure.getMessage());
            }
        }

        private void breakOneTick() {
            if (!guard.isActiveLease(lease)) {
                fail("Farm digging session lost packet authority");
                return;
            }
            BasePosition target = request.getDecision()
                .getTarget();
            CropObservation current = observer.observeSupported(target);
            traceCrop("break-observation", request.getTaskId(), request.getObservationIndex(), current);
            if (current == null) {
                stopBreakingInput();
                phase = Phase.PLANTING;
                detail = "Mature crop removed; preparing exact replant";
                return;
            }
            if (!samePlannedCrop(current) || !current.isMature() || !canReachCrop()) {
                fail("Farm target changed while breaking");
                return;
            }
            aimAt(target);
            minecraft.playerController.onPlayerDamageBlock(target.getX(), target.getY(), target.getZ(), targetSide());
            trace(
                "break-tick",
                "progressDriver",
                "horizonwright-exact-target",
                "screen",
                minecraft.currentScreen == null ? "none"
                    : minecraft.currentScreen.getClass()
                        .getSimpleName(),
                "inGameFocus",
                minecraft.inGameHasFocus);
            minecraft.thePlayer.swingItem();
        }

        private void plantOnce() {
            if (!guard.isActiveLease(lease) || !MinecraftRuntimeAccess.isAirBlock(
                minecraft.theWorld,
                request.getDecision()
                    .getTarget()
                    .getX(),
                request.getDecision()
                    .getTarget()
                    .getY(),
                request.getDecision()
                    .getTarget()
                    .getZ())) {
                fail("Exact farm target is not clear for replanting");
                return;
            }
            ItemStack seed = minecraft.thePlayer.inventory.mainInventory[seedSlot];
            if (seed == null || !request.getDecision()
                .getRequiredSeedFingerprint()
                .equals(observer.hotbarMaterialIdentity(seedSlot))) {
                fail("Approved hotbar seed disappeared before replanting");
                return;
            }
            minecraft.thePlayer.inventory.currentItem = seedSlot;
            slotChanged = seedSlot != priorHotbarSlot;
            minecraft.playerController.updateController();
            BasePosition target = request.getDecision()
                .getTarget();
            aimAt(target);
            boolean used = minecraft.playerController.onPlayerRightClick(
                minecraft.thePlayer,
                minecraft.theWorld,
                seed,
                target.getX(),
                target.getY() - 1,
                target.getZ(),
                1,
                Vec3.createVectorHelper(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D));
            restoreSlot();
            trace("replant-result", "accepted", used, "seedSlot", seedSlot, "target", target);
            if (!used) {
                stopActionSession();
                fail("Server interaction did not accept the exact replant attempt");
                return;
            }
            phase = Phase.DISPATCHING_REPLANT;
            detail = "Dispatching the exact replant interaction";
            try {
                ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                    synchronized (LiveHandle.this) {
                        stopActionSession();
                        replantDispatched = true;
                    }
                });
            } catch (RuntimeException failure) {
                stopActionSession();
                fail("Could not dispatch the exact replant interaction: " + failure.getMessage());
            }
        }

        private void awaitReplantDispatch() {
            if (!replantDispatched) {
                detail = "Waiting for the replant packet boundary";
                return;
            }
            phase = Phase.CONFIRMING;
            detail = "Waiting for an immature replacement crop";
        }

        private void awaitHarvestDispatch() {
            if (!harvestDispatched) {
                detail = "Waiting for the crop-harvest packet boundary";
                return;
            }
            phase = Phase.CONFIRMING;
            detail = "Waiting for the mature crop to reset to an immature state";
        }

        private void confirmReplacement() {
            CropObservation after = observer.observeSupported(
                request.getDecision()
                    .getTarget());
            if (after == null) {
                detail = "Waiting for replacement crop synchronization";
                trace("confirm-wait", "observation", "missing");
                return;
            }
            traceCrop("confirm-observation", request.getTaskId(), request.getObservationIndex(), after);
            if (verifier.isUnchanged(plannedBefore, after)) {
                detail = "Waiting for the server's harvested crop state";
                trace("confirm-wait", "observation", "unchanged-server-state");
                return;
            }
            try {
                verifier.requireReplacement(request.getDecision(), plannedBefore, after);
            } catch (RuntimeException mismatch) {
                fail(mismatch.getMessage());
                return;
            }
            confirmedAfter = after;
            stopActionSession();
            phase = Phase.WAITING_FOR_COLLECTION_SESSION;
            detail = request.getDecision()
                .getAction() == FarmActionKind.RIGHT_CLICK_HARVEST ? "Harvest reset confirmed; waiting to collect drops"
                    : "Replacement confirmed; waiting to collect harvest drops";
        }

        private void beginCollectionWhenReady() {
            if (!guard.isReadyForSession()) {
                detail = "Waiting for replant packets to drain before collecting drops";
                return;
            }
            BasePosition target = request.getDecision()
                .getTarget();
            // Baritone's player block position is the non-solid crop layer, not the
            // rendered/eye-height position reported by EntityPlayer.posY.
            int playerY = target.getY();
            long now = System.nanoTime();
            long remaining = deadlineNanos - now;
            if (remaining <= 0L) {
                fail("Farm drop collection deadline exceeded");
                return;
            }
            navigationHandle = navigation.submit(
                new NavigationRequest(
                    request.getRequestId() + "-collect",
                    request.getActionEpoch(),
                    target.getDimensionId(),
                    target.getX(),
                    playerY,
                    target.getZ(),
                    0,
                    now,
                    Math.min(remaining, NavigationRequest.MAX_RUNTIME_NANOS)),
                lease);
            phase = Phase.COLLECTING;
            detail = "Moving through the harvested crop to collect its drops";
            trace("collection-start", "navigationRequest", navigationHandle.getRequestId(), "playerGoalY", playerY);
        }

        private void pollCollection() {
            NavigationProgress progress = navigationHandle.progress();
            trace("collection", "navigationState", progress.getState(), "navigationDetail", progress.getDetail());
            if (progress.getState() == NavigationState.COMPLETED) {
                navigationHandle = null;
                collectionSettleTicks = 0;
                phase = Phase.SETTLING_COLLECTION;
                detail = "Harvest location reached; waiting for item pickup synchronization";
            } else if (progress.getState() == NavigationState.FAILED) {
                fail("Could not collect farm drops: " + progress.getDetail());
            } else if (progress.getState() == NavigationState.CANCELLED) {
                state = ActionState.CANCELLED;
                detail = "Farm drop collection was cancelled";
                clearActive(this);
            } else {
                detail = "Collecting farm drops: " + progress.getDetail();
            }
        }

        private void settleCollection() {
            collectionSettleTicks++;
            if (collectionSettleTicks < 5) {
                detail = "Waiting for harvest item pickup synchronization";
                return;
            }
            state = ActionState.CONFIRMED;
            detail = "Exact immature crop state is confirmed and its harvest location was collected";
            clearActive(this);
        }

        private boolean samePlannedCrop(CropObservation current) {
            return current != null && request.getDecision()
                .getObservationFingerprint()
                .equals(current.getObservationFingerprint())
                && request.getDecision()
                    .getRequiredSeedFingerprint()
                    .equals(current.getRequiredSeedFingerprint());
        }

        private boolean canReachCrop() {
            BasePosition target = request.getDecision()
                .getTarget();
            if (minecraft.thePlayer == null || minecraft.playerController == null
                || minecraft.theWorld == null
                || minecraft.theWorld.provider.dimensionId != target.getDimensionId()) {
                trace("reach", "reachable", false, "reason", "client-or-dimension-unavailable");
                return false;
            }
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = Vec3
                .createVectorHelper(player.posX, player.posY + MinecraftRuntimeAccess.eyeHeight(player), player.posZ);
            Vec3 center = Vec3.createVectorHelper(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            double reach = minecraft.playerController.getBlockReachDistance() + 0.5D;
            double distanceSquared = eyes.squareDistanceTo(center);
            if (distanceSquared > reach * reach) {
                trace(
                    "reach",
                    "reachable",
                    false,
                    "reason",
                    "distance",
                    "distanceSquared",
                    distanceSquared,
                    "reachSquared",
                    reach * reach,
                    "eyes",
                    eyes,
                    "center",
                    center);
                return false;
            }
            MovingObjectPosition hit = MinecraftRuntimeAccess.rayTraceBlocks(minecraft.theWorld, eyes, center, false);
            boolean hitTarget = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.blockX == target.getX()
                && hit.blockY == target.getY()
                && hit.blockZ == target.getZ();
            boolean reachable = FarmReachability.canInteract(distanceSquared, reach * reach, hit != null, hitTarget);
            trace(
                "reach",
                "reachable",
                reachable,
                "reason",
                hitTarget ? "target-hit" : hit == null && reachable ? "clear-ray-no-plant-hit" : "raytrace-obstructed",
                "distanceSquared",
                distanceSquared,
                "reachSquared",
                reach * reach,
                "hit",
                hit == null ? "none" : hit.typeOfHit + ":" + hit.blockX + "," + hit.blockY + "," + hit.blockZ);
            return reachable;
        }

        private void aimAt(BasePosition target) {
            EntityPlayer player = minecraft.thePlayer;
            double dx = target.getX() + 0.5D - player.posX;
            double dy = target.getY() + 0.5D - (player.posY + MinecraftRuntimeAccess.eyeHeight(player));
            double dz = target.getZ() + 0.5D - player.posZ;
            player.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            player.rotationPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI);
        }

        private int targetSide() {
            return 1;
        }

        private void restoreSlot() {
            if (!slotChanged) return;
            minecraft.thePlayer.inventory.currentItem = priorHotbarSlot;
            if (lease.isValid() && guard.isActiveLease(lease)) minecraft.playerController.updateController();
            slotChanged = false;
        }

        private void returnStagedSpade() {
            if (!spadeStaged) return;
            if (minecraft.thePlayer.inventory.getItemStack() != null
                || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer
                || !isCropsNhSpadeAt(harvestHandSlot)) {
                throw new IllegalStateException("Cannot safely return the staged CropsNH spade");
            }
            minecraft.playerController.windowClick(
                minecraft.thePlayer.openContainer.windowId,
                stagedSpadeInventorySlot,
                harvestHandSlot,
                2,
                minecraft.thePlayer);
            spadeStaged = false;
            trace("spade-return", "hotbarSlot", harvestHandSlot, "inventorySlot", stagedSpadeInventorySlot);
        }

        private void stopActionSession() {
            restoreSlot();
            if (spadeStaged) {
                try {
                    returnStagedSpade();
                } catch (RuntimeException failure) {
                    DevelopmentTrace.event(
                        "farm-live",
                        "spade-return-failed",
                        "request",
                        request.getRequestId(),
                        "failure",
                        failure.getMessage());
                }
            }
            stopBreakingInput();
            if (ownsActionSession) {
                guard.quarantine(lease);
                guard.end(lease);
                ownsActionSession = false;
            }
        }

        private void stopBreakingInput() {
            ClientBootstrap.blockDamageShield()
                .release(request.getRequestId());
            minecraft.playerController.resetBlockRemoving();
        }

        private void stopProducers() {
            if (navigationHandle != null) {
                navigationHandle.cancel();
                navigationHandle = null;
            }
            stopActionSession();
        }

        private synchronized void cancelOnClientThread() {
            if (isTerminal()) return;
            stopProducers();
            state = ActionState.CANCELLED;
            detail = "Farm action cancelled before checkpoint advancement";
            clearActive(this);
        }

        private void fail(String failure) {
            stopProducers();
            state = ActionState.FAILED;
            detail = failure;
            trace("failed", "failure", failure);
            clearActive(this);
        }

        private void trace(String event, Object... extraFields) {
            Object[] fields = new Object[10 + extraFields.length];
            fields[0] = "request";
            fields[1] = request.getRequestId();
            fields[2] = "phase";
            fields[3] = phase;
            fields[4] = "state";
            fields[5] = state;
            fields[6] = "detail";
            fields[7] = detail;
            fields[8] = "target";
            fields[9] = request.getDecision()
                .getTarget();
            System.arraycopy(extraFields, 0, fields, 10, extraFields.length);
            DevelopmentTrace.event("farm-live", event, fields);
        }

        private ActionProgress snapshot() {
            // The immature crop state is verified before the collection leg starts, but it is not
            // post-action evidence until that leg finishes and the whole action is confirmed.
            CropObservation reportableAfter = state == ActionState.CONFIRMED ? confirmedAfter : null;
            return new ActionProgress(request.getRequestId(), state, detail, reportableAfter);
        }

        private boolean isTerminal() {
            return state == ActionState.CONFIRMED || state == ActionState.CANCELLED || state == ActionState.FAILED;
        }
    }

    private static void traceCrop(String event, String taskId, int index, CropObservation crop) {
        DevelopmentTrace.event(
            "farm-live",
            event,
            "task",
            taskId,
            "index",
            index,
            "position",
            crop == null ? "missing" : crop.getPosition(),
            "family",
            crop == null ? "missing" : crop.getFamily(),
            "fingerprint",
            crop == null ? "missing" : crop.getObservationFingerprint(),
            "seed",
            crop == null ? "missing" : crop.getRequiredSeedFingerprint(),
            "maturityKnown",
            crop != null && crop.isMaturityKnown(),
            "mature",
            crop != null && crop.isMature(),
            "protected",
            crop != null && crop.isProtectedBlock());
    }

    private enum Phase {
        APPROACHING,
        WAITING_FOR_ACTION_SESSION,
        BREAKING,
        PLANTING,
        DISPATCHING_REPLANT,
        WAITING_FOR_EMPTY_HAND,
        DISPATCHING_HARVEST,
        CONFIRMING,
        WAITING_FOR_COLLECTION_SESSION,
        COLLECTING,
        SETTLING_COLLECTION
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }
}
