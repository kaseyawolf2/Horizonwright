package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SaplingReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.base.TreeActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservation;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservationState;
import io.github.kaseyawolf2.horizonwright.core.base.TreeWorkCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.forge.client.ClientBootstrap;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.ExcavationTargetOverlay;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeBackend;

/** Live, bounded vanilla-tree felling and exact-sapling replant backend. */
public final class LiveVanillaTreeBackend implements TreeBackend {

    public interface NavigationSource {

        NavigationBackend getNavigationBackend();
    }

    private static final EnumSet<ActionCapability> FELL = EnumSet.of(
        ActionCapability.MOVEMENT,
        ActionCapability.LOOK,
        ActionCapability.DIG,
        ActionCapability.PLACE,
        ActionCapability.HELD_USE);
    private static final EnumSet<ActionCapability> PLANT = EnumSet.of(
        ActionCapability.MOVEMENT,
        ActionCapability.LOOK,
        ActionCapability.PLACE,
        ActionCapability.HELD_USE,
        ActionCapability.CONTAINER);
    private static final List<String> LEAVES = Collections
        .unmodifiableList(Arrays.asList("minecraft:leaves", "minecraft:leaves2"));
    private static final long APPROACH_TIMEOUT_NANOS = NavigationRequest.MAX_RUNTIME_NANOS;
    private static final long ACTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(45L);

    private final Minecraft minecraft;
    private final ActionSessionGuard guard;
    private final NavigationSource navigationSource;
    private final MinecraftVanillaTreeObserver observer;
    private LiveHandle active;

    public LiveVanillaTreeBackend(Minecraft minecraft, ActionSessionGuard guard, NavigationSource navigationSource,
        ProfileFarmConfiguration configuration) {
        if (minecraft == null || guard == null || navigationSource == null || configuration == null) {
            throw new IllegalArgumentException("complete live vanilla-tree dependencies are required");
        }
        this.minecraft = minecraft;
        this.guard = guard;
        this.navigationSource = navigationSource;
        this.observer = new MinecraftVanillaTreeObserver(minecraft, configuration);
    }

    @Override
    public FarmBackend.Availability availability() {
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        if (navigation == null)
            return FarmBackend.Availability.unavailable("No navigation backend is configured for trees");
        BackendAvailability status = navigation.availability();
        return status.isAvailable()
            ? FarmBackend.Availability.available("Bounded vanilla-tree actions ready through " + status.getDiagnostic())
            : FarmBackend.Availability.unavailable("Tree navigation unavailable: " + status.getDiagnostic());
    }

    @Override
    public PassSnapshot scan(ScanRequest request) {
        requireClient(request);
        NamedArea area = observer.resolveArea(request.getAreaId());
        List<TreeObservation> trees = observer.scan(area, request.getPlantSpecies(), request.getPlantSpacing());
        DevelopmentTrace.event(
            "tree-live",
            "scan",
            "task",
            request.getTaskId(),
            "area",
            request.getAreaId(),
            "trees",
            trees.size(),
            "bounds",
            area);
        return new PassSnapshot(request.getTaskId(), request.getActionEpoch(), area, trees);
    }

    @Override
    public TargetSnapshot observe(TargetRequest request) {
        requireClient(request);
        TreeObservation tree = observer.observe(request.getWork());
        SaplingReserveEvidence reserve = observer.reserve(
            request.getPassRevision(),
            request.getWork()
                .getRequiredSaplingFingerprint(),
            request.getMinimumSaplingReserve());
        traceObservation("observe", request.getTaskId(), tree);
        return new TargetSnapshot(
            request.getTaskId(),
            request.getPassRevision(),
            request.getActionEpoch(),
            request.getIndex(),
            tree,
            reserve);
    }

    @Override
    public synchronized ActionHandle execute(ActionRequest request, ActionLease lease) {
        requireClient(request);
        TreeActionKind action = request.getDecision()
            .getAction();
        EnumSet<ActionCapability> required = action == TreeActionKind.FELL_CAPTURED_BLOCKS ? FELL
            : action == TreeActionKind.PLANT_SAPLING ? PLANT : null;
        if (required == null)
            throw new IllegalArgumentException("live tree backend supports only fell and plant actions");
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || !lease.getCapabilities()
                .containsAll(required))
            throw new IllegalArgumentException("matching tree action authority is required");
        if (active != null && !active.isTerminal()) throw new IllegalStateException("another tree action is active");
        TreeWorkCheckpoint work = checkpoint(request);
        TreeObservation before = observer.observe(work);
        if (!request.getDecision()
            .isCurrentFor(
                work,
                before,
                observer.reserve(
                    work.getWorkRevision(),
                    work.getRequiredSaplingFingerprint(),
                    request.getDecision()
                        .getReserveEvidence()
                        .getMinimumReserve()))) {
            throw new IllegalStateException("tree action evidence changed after planning");
        }
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        FarmBackend.Availability status = availability();
        if (navigation == null || !status.isAvailable()) throw new IllegalStateException(status.getDiagnostic());
        LiveHandle handle = new LiveHandle(request, work, lease, navigation);
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

    private static TreeWorkCheckpoint checkpoint(ActionRequest request) {
        return TreeWorkCheckpoint.restore(
            request.getDecision()
                .getTreeFarm(),
            request.getDecision()
                .getWorkRevision(),
            request.getDecision()
                .getTreeId(),
            request.getDecision()
                .getRequiredSaplingFingerprint(),
            request.getDecision()
                .getReplantPosition(),
            request.getDecision()
                .getCapturedBlocks(),
            request.getDecision()
                .getObservationRevision(),
            request.getDecision()
                .getObservationFingerprint(),
            request.getDecision()
                .getWorkStage());
    }

    private void requireClient(Object request) {
        if (request == null) throw new IllegalArgumentException("tree request is required");
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.playerController == null)
            throw new IllegalStateException("live tree work requires a joined client thread");
    }

    private synchronized void clearActive(LiveHandle handle) {
        if (active == handle) active = null;
        ExcavationTargetOverlay.clear();
    }

    private final class LiveHandle implements ActionHandle {

        private final ActionRequest request;
        private final TreeWorkCheckpoint work;
        private final ActionLease lease;
        private final NavigationBackend navigation;
        private final int priorHotbarSlot;
        private ActionState state = ActionState.SUBMITTED;
        private Phase phase;
        private String detail = "Preparing bounded tree action";
        private NavigationHandle navigationHandle;
        private BasePosition target;
        private int nextLog;
        private int approachAttempt;
        private long deadlineNanos;
        private boolean ownsSession;
        private boolean toolSlotChanged;
        private boolean placementDispatched;
        private boolean inventoryStaged;
        private int verifiedSide = 1;
        private int saplingSourceSlot = -1;
        private int saplingHotbarSlot = -1;
        private String pendingApproachReason;
        private TreeObservation confirmedAfter;
        private volatile boolean cancellationRequested;

        private LiveHandle(ActionRequest request, TreeWorkCheckpoint work, ActionLease lease,
            NavigationBackend navigation) {
            this.request = request;
            this.work = work;
            this.lease = lease;
            this.navigation = navigation;
            this.priorHotbarSlot = minecraft.thePlayer.inventory.currentItem;
        }

        private void start() {
            if (request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS) prepareNextLog();
            else preparePlant();
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public synchronized ActionProgress progress() {
            requireClient(request);
            trace("progress", "leaseValid", lease.isValid(), "guardReady", guard.isReadyForSession());
            if (isTerminal()) return snapshot();
            if (cancellationRequested) {
                cancelOnClientThread();
                return snapshot();
            }
            if (!lease.isValid()) {
                fail("Tree action lease was revoked");
                return snapshot();
            }
            if (System.nanoTime() - deadlineNanos >= 0L) {
                fail("Tree action deadline exceeded during " + phase);
                return snapshot();
            }
            if (phase == Phase.APPROACHING) pollApproach();
            else if (phase == Phase.WAITING_FOR_NAVIGATION) startPendingApproach();
            else if (phase == Phase.WAITING_FOR_SESSION) beginActionWhenReady();
            else if (phase == Phase.DIGGING) digOneTick();
            else if (phase == Phase.WAITING_FOR_DRAIN) continueAfterDrain();
            else if (phase == Phase.CONFIRMING) confirmMutation();
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

        private void prepareNextLog() {
            while (nextLog < work.getCapturedBlocks()
                .size()) {
                BasePosition candidate = work.getCapturedBlocks()
                    .get(nextLog);
                if (minecraft.theWorld.isAirBlock(candidate.getX(), candidate.getY(), candidate.getZ())) {
                    nextLog++;
                    continue;
                }
                if (!observer.hasExpectedLog(candidate, work.getRequiredSaplingFingerprint())) {
                    fail("Captured tree changed at " + candidate);
                    return;
                }
                target = candidate;
                approachAttempt = 0;
                approachOrAct("Approaching the next bottom-up tree log");
                return;
            }
            target = null;
            phase = Phase.CONFIRMING;
            deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
            detail = "All captured logs are gone; confirming the clear root";
        }

        private void preparePlant() {
            target = observer.nextMissingSapling(work);
            if (target == null) {
                phase = Phase.CONFIRMING;
                deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
                return;
            }
            approachAttempt = 0;
            approachOrAct("Approaching the exact replant position");
        }

        private void approachOrAct(String reason) {
            showTarget();
            if (request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS ? canReachBlock(target) : canReachSupport(target)) {
                phase = Phase.WAITING_FOR_SESSION;
                deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
                state = ActionState.EXECUTING;
                detail = "Exact tree target is within confirmed reach";
                return;
            }
            submitApproach(reason);
        }

        private void submitApproach(String reason) {
            pendingApproachReason = reason;
            phase = Phase.WAITING_FOR_NAVIGATION;
            state = ActionState.EXECUTING;
            deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
            startPendingApproach();
        }

        private void startPendingApproach() {
            if (!guard.isReadyForSession()) {
                detail = "Waiting for previous tree navigation/action cleanup before approach";
                trace(
                    "approach-drain-wait",
                    "nextAttempt",
                    approachAttempt + 1,
                    "readiness",
                    guard.readinessDiagnostic());
                return;
            }
            approachAttempt++;
            long now = System.nanoTime();
            NavigationRequest navigationRequest = request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS
                    ? NavigationRequest.adjacentToAllowingPlacementAndBreaking(
                        request.getRequestId() + "-approach-" + approachAttempt,
                        request.getActionEpoch(),
                        target.getDimensionId(),
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        LEAVES,
                        now,
                        APPROACH_TIMEOUT_NANOS)
                    : NavigationRequest.adjacentToAllowingPlacement(
                        request.getRequestId() + "-approach-" + approachAttempt,
                        request.getActionEpoch(),
                        target.getDimensionId(),
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        now,
                        APPROACH_TIMEOUT_NANOS);
            navigationHandle = navigation.submit(navigationRequest, lease);
            phase = Phase.APPROACHING;
            deadlineNanos = add(now, APPROACH_TIMEOUT_NANOS);
            state = ActionState.EXECUTING;
            detail = pendingApproachReason;
            trace("approach-start", "attempt", approachAttempt, "reason", pendingApproachReason);
        }

        private void pollApproach() {
            boolean reached = request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS ? canReachBlock(target) : canReachSupport(target);
            if (reached) {
                navigationHandle.cancel();
                navigationHandle = null;
                phase = Phase.WAITING_FOR_SESSION;
                deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
                detail = "Exact tree target became reachable";
                return;
            }
            NavigationProgress progress = navigationHandle.progress();
            if (progress.getState() == NavigationState.COMPLETED) {
                navigationHandle = null;
                if (approachAttempt < 2) submitApproach("Repositioning for exact tree reach");
                else fail("Tree navigation completed outside exact interaction reach");
            } else if (progress.getState() == NavigationState.FAILED) {
                navigationHandle = null;
                if (approachAttempt < 2) submitApproach("Retrying tree approach after " + progress.getDetail());
                else fail("Could not approach tree target: " + progress.getDetail());
            } else if (progress.getState() == NavigationState.CANCELLED) {
                state = ActionState.CANCELLED;
                detail = "Tree approach was cancelled";
                clearActive(this);
            } else detail = "Approaching tree target: " + progress.getDetail();
        }

        private void beginActionWhenReady() {
            if (!guard.isReadyForSession()) {
                detail = "Waiting for navigation packets to drain";
                return;
            }
            if (request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS) beginDig();
            else beginPlant();
        }

        private void beginDig() {
            if (minecraft.theWorld.isAirBlock(target.getX(), target.getY(), target.getZ())) {
                nextLog++;
                prepareNextLog();
                return;
            }
            if (!observer.hasExpectedLog(target, work.getRequiredSaplingFingerprint())) {
                fail("Tree log changed after approach");
                return;
            }
            if (!canReachBlock(target)) {
                submitApproach("Reapproaching a tree log that left reach");
                return;
            }
            guard.begin(lease);
            ownsSession = true;
            ClientBootstrap.blockDamageShield()
                .acquire(request.getRequestId());
            selectBestTool(target);
            aimAt(target);
            minecraft.playerController.clickBlock(target.getX(), target.getY(), target.getZ(), verifiedSide);
            phase = Phase.DIGGING;
            deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
            detail = "Digging a bounded bottom-up tree log";
        }

        private void digOneTick() {
            if (!guard.isActiveLease(lease)) {
                fail("Tree digging session lost authority");
                return;
            }
            if (minecraft.theWorld.isAirBlock(target.getX(), target.getY(), target.getZ())) {
                stopSession();
                nextLog++;
                phase = Phase.WAITING_FOR_DRAIN;
                deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
                detail = "Tree log is air; waiting for final dig packets to drain";
                return;
            }
            if (!observer.hasExpectedLog(target, work.getRequiredSaplingFingerprint())) {
                fail("Tree log changed while digging");
                return;
            }
            if (!canReachBlock(target)) {
                stopSession();
                submitApproach("Reapproaching after leaving tree-log reach");
                return;
            }
            aimAt(target);
            minecraft.playerController.onPlayerDamageBlock(target.getX(), target.getY(), target.getZ(), verifiedSide);
            ClientBootstrap.blockDamageShield()
                .checkpoint();
            minecraft.thePlayer.swingItem();
        }

        private void beginPlant() {
            if (!observer.reserve(
                work.getWorkRevision(),
                work.getRequiredSaplingFingerprint(),
                request.getDecision()
                    .getReserveEvidence()
                    .getMinimumReserve())
                .canReplantAndPreserveReserve()) {
                fail("Not enough matching saplings to finish the planting pattern while preserving reserve");
                return;
            }
            TreeObservation clear;
            try {
                clear = observer.observe(work);
            } catch (RuntimeException failure) {
                fail(failure.getMessage());
                return;
            }
            if (clear.getState() != TreeObservationState.FELLED_CLEAR) {
                fail("Tree root is no longer clear for replanting");
                return;
            }
            if (!canReachSupport(target)) {
                submitApproach("Reapproaching the exact sapling support block");
                return;
            }
            saplingHotbarSlot = observer.findSaplingSlot(work.getRequiredSaplingFingerprint(), 0, 9);
            saplingSourceSlot = saplingHotbarSlot < 0
                ? observer.findSaplingSlot(work.getRequiredSaplingFingerprint(), 9, 36)
                : -1;
            if (saplingHotbarSlot < 0 && saplingSourceSlot < 0) {
                fail("Required sapling is no longer present");
                return;
            }
            guard.begin(lease);
            ownsSession = true;
            if (saplingHotbarSlot < 0) {
                if (minecraft.thePlayer.inventory.getItemStack() != null
                    || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer) {
                    fail("Cannot safely stage a sapling while another container or cursor item is active");
                    return;
                }
                saplingHotbarSlot = chooseStagingHotbarSlot();
                minecraft.playerController.windowClick(
                    minecraft.thePlayer.openContainer.windowId,
                    saplingSourceSlot,
                    saplingHotbarSlot,
                    2,
                    minecraft.thePlayer);
                inventoryStaged = true;
            }
            minecraft.thePlayer.inventory.currentItem = saplingHotbarSlot;
            minecraft.playerController.updateController();
            aimAtSupport(target);
            BasePosition support = new BasePosition(
                target.getDimensionId(),
                target.getX(),
                target.getY() - 1,
                target.getZ());
            boolean accepted = minecraft.playerController.onPlayerRightClick(
                minecraft.thePlayer,
                minecraft.theWorld,
                minecraft.thePlayer.getHeldItem(),
                support.getX(),
                support.getY(),
                support.getZ(),
                1,
                Vec3.createVectorHelper(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D));
            if (!accepted) {
                fail("Minecraft rejected the exact sapling placement");
                return;
            }
            phase = Phase.WAITING_FOR_DRAIN;
            placementDispatched = false;
            deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
            detail = "Dispatching exact sapling placement";
            ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                synchronized (LiveHandle.this) {
                    returnStagedSapling();
                    stopSession();
                    placementDispatched = true;
                }
            });
        }

        private void continueAfterDrain() {
            if (request.getDecision()
                .getAction() == TreeActionKind.PLANT_SAPLING && !placementDispatched) {
                detail = "Waiting for the sapling packet boundary";
                return;
            }
            if (!guard.isReadyForSession()) {
                detail = "Waiting for tree action packets to drain";
                return;
            }
            if (request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS) prepareNextLog();
            else {
                phase = Phase.CONFIRMING;
                detail = "Waiting for server-confirmed sapling state";
            }
        }

        private void confirmMutation() {
            try {
                if (request.getDecision()
                    .getAction() == TreeActionKind.PLANT_SAPLING && observer.nextMissingSapling(work) != null) {
                    // Do not move on until the sapling just placed is actually observed.
                    BasePosition missing = observer.nextMissingSapling(work);
                    if (!missing.equals(target)) preparePlant();
                    return;
                }
                TreeObservation after = observer.observeAfterMutation(work);
                TreeObservationState expected = request.getDecision()
                    .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS ? TreeObservationState.FELLED_CLEAR
                        : TreeObservationState.SAPLING_PLANTED;
                if (after.getState() != expected) {
                    detail = "Waiting for " + expected;
                    return;
                }
                confirmedAfter = after;
                state = ActionState.CONFIRMED;
                detail = expected == TreeObservationState.FELLED_CLEAR ? "Captured tree logs are confirmed clear"
                    : "Exact replacement sapling is confirmed";
                clearActive(this);
            } catch (RuntimeException waiting) {
                detail = "Waiting for tree postcondition: " + waiting.getMessage();
            }
        }

        private void selectBestTool(BasePosition position) {
            Block targetBlock = MinecraftRuntimeAccess
                .block(minecraft.theWorld, position.getX(), position.getY(), position.getZ());
            int previous = minecraft.thePlayer.inventory.currentItem;
            int bestSlot = previous;
            float bestProgress = -1.0F;
            try {
                for (int slot = 0; slot < 9; slot++) {
                    minecraft.thePlayer.inventory.currentItem = slot;
                    float progress = targetBlock.getPlayerRelativeBlockHardness(
                        minecraft.thePlayer,
                        minecraft.theWorld,
                        position.getX(),
                        position.getY(),
                        position.getZ());
                    if (!Float.isNaN(progress) && progress > bestProgress) {
                        bestProgress = progress;
                        bestSlot = slot;
                    }
                }
            } finally {
                minecraft.thePlayer.inventory.currentItem = previous;
            }
            if (bestSlot != previous) {
                minecraft.thePlayer.inventory.currentItem = bestSlot;
                minecraft.playerController.updateController();
                toolSlotChanged = true;
            }
        }

        private int chooseStagingHotbarSlot() {
            for (int slot = 0; slot < 9; slot++)
                if (minecraft.thePlayer.inventory.mainInventory[slot] == null) return slot;
            return priorHotbarSlot;
        }

        private void returnStagedSapling() {
            if (!inventoryStaged) return;
            if (minecraft.thePlayer.inventory.getItemStack() != null
                || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer) return;
            minecraft.playerController.windowClick(
                minecraft.thePlayer.openContainer.windowId,
                saplingSourceSlot,
                saplingHotbarSlot,
                2,
                minecraft.thePlayer);
            inventoryStaged = false;
        }

        private boolean canReachBlock(BasePosition position) {
            return exactRay(position, position.getY() + 0.5D);
        }

        private boolean canReachSupport(BasePosition root) {
            if (!minecraft.theWorld.isAirBlock(root.getX(), root.getY(), root.getZ())) return false;
            BasePosition support = new BasePosition(root.getDimensionId(), root.getX(), root.getY() - 1, root.getZ());
            return exactRay(support, TreeInteractionGeometry.supportProbeY(root.getY()));
        }

        private boolean exactRay(BasePosition position, double targetY) {
            EntityPlayer player = minecraft.thePlayer;
            if (player == null || minecraft.theWorld.provider.dimensionId != position.getDimensionId()) return false;
            Vec3 eyes = MinecraftRuntimeAccess.playerInteractionOrigin(player);
            Vec3 targetPoint = Vec3.createVectorHelper(position.getX() + 0.5D, targetY, position.getZ() + 0.5D);
            double reach = minecraft.playerController.getBlockReachDistance();
            if (eyes.squareDistanceTo(targetPoint) > reach * reach) return false;
            MovingObjectPosition hit = MinecraftRuntimeAccess
                .rayTraceBlocks(minecraft.theWorld, eyes, targetPoint, false);
            boolean exact = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.blockX == position.getX()
                && hit.blockY == position.getY()
                && hit.blockZ == position.getZ();
            if (exact) verifiedSide = hit.sideHit;
            trace(
                "interaction-ray",
                "exact",
                exact,
                "eyes",
                eyes,
                "point",
                targetPoint,
                "hit",
                hit == null ? "none" : hit.blockX + "," + hit.blockY + "," + hit.blockZ);
            return exact;
        }

        private void aimAt(BasePosition position) {
            aim(position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D);
        }

        private void aimAtSupport(BasePosition root) {
            aim(root.getX() + 0.5D, root.getY(), root.getZ() + 0.5D);
        }

        private void aim(double x, double y, double z) {
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = MinecraftRuntimeAccess.playerInteractionOrigin(player);
            double dx = x - eyes.xCoord;
            double dy = y - eyes.yCoord;
            double dz = z - eyes.zCoord;
            player.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            player.rotationPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI);
        }

        private void showTarget() {
            if (target == null) return;
            ExcavationTargetOverlay.show(
                new io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition(
                    target.getX(),
                    target.getY(),
                    target.getZ()));
        }

        private void restoreSlot() {
            if (!toolSlotChanged && minecraft.thePlayer.inventory.currentItem == priorHotbarSlot) return;
            minecraft.thePlayer.inventory.currentItem = priorHotbarSlot;
            if (lease.isValid() && guard.isActiveLease(lease)) minecraft.playerController.updateController();
            toolSlotChanged = false;
        }

        private void stopSession() {
            ClientBootstrap.blockDamageShield()
                .release(request.getRequestId());
            minecraft.playerController.resetBlockRemoving();
            returnStagedSapling();
            restoreSlot();
            if (ownsSession) {
                guard.quarantine(lease);
                guard.end(lease);
                ownsSession = false;
            }
        }

        private synchronized void cancelOnClientThread() {
            if (isTerminal()) return;
            if (navigationHandle != null) {
                navigationHandle.cancel();
                navigationHandle = null;
            }
            stopSession();
            state = ActionState.CANCELLED;
            detail = "Tree action cancelled before checkpoint advancement";
            clearActive(this);
        }

        private void fail(String failure) {
            if (navigationHandle != null) {
                navigationHandle.cancel();
                navigationHandle = null;
            }
            stopSession();
            state = ActionState.FAILED;
            detail = failure == null ? "Tree action failed" : failure;
            trace("failed", "failure", detail);
            clearActive(this);
        }

        private ActionProgress snapshot() {
            return new ActionProgress(
                request.getRequestId(),
                state,
                detail,
                state == ActionState.CONFIRMED ? confirmedAfter : null);
        }

        private boolean isTerminal() {
            return state == ActionState.CONFIRMED || state == ActionState.CANCELLED || state == ActionState.FAILED;
        }

        private void trace(String event, Object... fields) {
            Object[] base = new Object[8 + fields.length];
            base[0] = "request";
            base[1] = request.getRequestId();
            base[2] = "phase";
            base[3] = phase;
            base[4] = "state";
            base[5] = state;
            base[6] = "target";
            base[7] = target;
            System.arraycopy(fields, 0, base, 8, fields.length);
            DevelopmentTrace.event("tree-live", event, base);
        }
    }

    private static void traceObservation(String event, String taskId, TreeObservation tree) {
        DevelopmentTrace.event(
            "tree-live",
            event,
            "task",
            taskId,
            "tree",
            tree.getTreeId(),
            "state",
            tree.getState(),
            "root",
            tree.getReplantPosition(),
            "blocks",
            tree.getTreeBlocks()
                .size(),
            "fingerprint",
            tree.getObservationFingerprint());
    }

    private static long add(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }

    private enum Phase {
        WAITING_FOR_NAVIGATION,
        APPROACHING,
        WAITING_FOR_SESSION,
        DIGGING,
        WAITING_FOR_DRAIN,
        CONFIRMING
    }
}
