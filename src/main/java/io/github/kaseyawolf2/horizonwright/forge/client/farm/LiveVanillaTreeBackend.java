package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.navigation.ScaffoldCleanup;
import io.github.kaseyawolf2.horizonwright.forge.client.ClientBootstrap;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.PillarMaterialStaging;
import io.github.kaseyawolf2.horizonwright.forge.client.ToolCapabilities;
import io.github.kaseyawolf2.horizonwright.forge.client.VerticalMiningStability;
import io.github.kaseyawolf2.horizonwright.forge.client.container.ConfirmedContainerTransactionExecutor;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
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
        ActionCapability.HELD_USE,
        ActionCapability.CONTAINER);
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
    private final ConfirmedContainerTransactionExecutor transactions;
    private final MinecraftContainerSnapshotter inventorySnapshots = new MinecraftContainerSnapshotter();
    private LiveHandle active;

    public LiveVanillaTreeBackend(Minecraft minecraft, ActionSessionGuard guard, NavigationSource navigationSource,
        ProfileFarmConfiguration configuration) {
        this(minecraft, guard, navigationSource, configuration, null);
    }

    public LiveVanillaTreeBackend(Minecraft minecraft, ActionSessionGuard guard, NavigationSource navigationSource,
        ProfileFarmConfiguration configuration, ConfirmedContainerTransactionExecutor transactions) {
        if (minecraft == null || guard == null || navigationSource == null || configuration == null) {
            throw new IllegalArgumentException("complete live vanilla-tree dependencies are required");
        }
        this.minecraft = minecraft;
        this.guard = guard;
        this.navigationSource = navigationSource;
        this.observer = new MinecraftVanillaTreeObserver(minecraft, configuration);
        this.transactions = transactions;
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
    public CollectionHandle collectDrops(String taskId, NamedArea area, ActionLease lease) {
        requireClient(area);
        if (active != null && !active.isTerminal()) throw new IllegalStateException("Tree action is still active");
        return new TreeDropCollector(minecraft, guard, navigationSource.getNavigationBackend(), taskId, area, lease);
    }

    @Override
    public PassSnapshot plantingGrid(ScanRequest request) {
        requireClient(request);
        NamedArea area = observer.resolveArea(request.getAreaId());
        return new PassSnapshot(
            request.getTaskId(),
            request.getActionEpoch(),
            area,
            observer.plantingGrid(area, request.getPlantSpecies(), request.getPlantSpacing()));
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
        private int placementTick;
        private int placementRetries;
        private boolean inventoryStaged;
        private int verifiedSide = 1;
        private int saplingSourceSlot = -1;
        private int saplingHotbarSlot = -1;
        private ItemFingerprint saplingDisplaced;
        private int stagedToolSource = -1;
        private int stagedToolHotbar = -1;
        private Item stagedToolItem;
        private ItemFingerprint stagedToolDisplaced;
        private ContainerTransaction toolTransaction;
        private long inventoryRevision;
        private String pendingApproachReason;
        private int[][] standBackPositions;
        private PillarMaterialStaging pillarStaging;
        private final java.util.List<BlockPosition> scaffoldExclusions = new java.util.ArrayList<>();
        private int returnX, returnY, returnZ;
        private boolean returningToGround;
        private ScaffoldCleanup scaffoldCleanup;

        private TreeObservation confirmedAfter;
        private volatile boolean cancellationRequested;

        private LiveHandle(ActionRequest request, TreeWorkCheckpoint work, ActionLease lease,
            NavigationBackend navigation) {
            this.request = request;
            this.work = work;
            this.lease = lease;
            this.navigation = navigation;
            this.priorHotbarSlot = minecraft.thePlayer.inventory.currentItem;
            returnX = (int) Math.floor(minecraft.thePlayer.posX);
            returnY = (int) Math.floor(minecraft.thePlayer.boundingBox.minY);
            returnZ = (int) Math.floor(minecraft.thePlayer.posZ);
            for (BasePosition log : work.getCapturedBlocks())
                scaffoldExclusions.add(new BlockPosition(log.getX(), log.getY(), log.getZ()));
        }

        private void start() {
            if (!navigation.remainingScaffolds()
                .isEmpty()) {
                scaffoldCleanup = new ScaffoldCleanup(
                    navigation,
                    lease,
                    request.getRequestId(),
                    minecraft.theWorld.provider.dimensionId);
                phase = Phase.REMOVING_OLD_SCAFFOLDS;
                state = ActionState.EXECUTING;
                deadlineNanos = add(System.nanoTime(), APPROACH_TIMEOUT_NANOS);
                detail = "Removing remaining pillar blocks before resuming tree work";
                return;
            }
            returnX = (int) Math.floor(minecraft.thePlayer.posX);
            returnY = (int) Math.floor(minecraft.thePlayer.boundingBox.minY);
            returnZ = (int) Math.floor(minecraft.thePlayer.posZ);
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
            if (phase == Phase.REMOVING_OLD_SCAFFOLDS) {
                if (scaffoldCleanup.poll()) {
                    scaffoldCleanup.close();
                    scaffoldCleanup = null;
                    start();
                }
            } else if (phase == Phase.APPROACHING) pollApproach();
            else if (phase == Phase.WAITING_FOR_NAVIGATION) startPendingApproach();
            else if (phase == Phase.WAITING_FOR_PILLAR_STACK) {
                if (pillarStaging.poll()) {
                    pillarStaging = null;
                    phase = Phase.WAITING_FOR_NAVIGATION;
                }
            } else if (phase == Phase.RETURNING_TO_GROUND) returnToGround();
            else if (phase == Phase.WAITING_FOR_SESSION) beginActionWhenReady();
            else if (phase == Phase.WAITING_FOR_TOOL || phase == Phase.RETURNING_TOOL) pollToolTransfer();
            else if (phase == Phase.DIGGING) digOneTick();
            else if (phase == Phase.WAITING_FOR_DRAIN) continueAfterDrain();
            else if (phase == Phase.CONFIRMING) confirmMutation();
            else if (phase == Phase.FINAL_DRAIN && guard.isReadyForSession()) {
                state = ActionState.CONFIRMED;
                detail = request.getDecision()
                    .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS
                        ? "Captured tree logs and tool inventory are confirmed"
                        : "Exact replacement sapling is confirmed";
                clearActive(this);
            }
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
                standBackPositions = null;
                approachOrAct("Approaching the next bottom-up tree log");
                return;
            }
            target = null;
            phase = Phase.CONFIRMING;
            deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
            if (!navigation.remainingScaffolds()
                .isEmpty() || minecraft.thePlayer.boundingBox.minY > returnY + 1.0) {
                phase = Phase.RETURNING_TO_GROUND;
                deadlineNanos = add(System.nanoTime(), APPROACH_TIMEOUT_NANOS);
                detail = "Descending from tree scaffolding before collecting drops";
                return;
            }
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
            standBackPositions = null;
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
            if (approachAttempt >= 5) {
                fail("Tree target remained inaccessible after adjacent and four stand-back views");
                return;
            }
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
            if (request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS) {
                pillarStaging = PillarMaterialStaging
                    .prepare(minecraft, guard, lease, stagedToolSource, stagedToolHotbar);
                if (pillarStaging != null) {
                    phase = Phase.WAITING_FOR_PILLAR_STACK;
                    detail = "Staging logs or building blocks for tree pillaring";
                    return;
                }
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
            // A second adjacent goal selects the same obstructed spot. Try distinct views instead.
            if (approachAttempt > 1) {
                if (standBackPositions == null) {
                    standBackPositions = TreeInteractionGeometry.standBackPositions(
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        minecraft.thePlayer.posX,
                        minecraft.thePlayer.posZ);
                }
                int[] point = standBackPositions[approachAttempt - 2];
                navigationRequest = NavigationRequest.nearAllowingPlacementAndBreaking(
                    request.getRequestId() + "-view-" + approachAttempt,
                    request.getActionEpoch(),
                    target.getDimensionId(),
                    point[0],
                    point[1],
                    point[2],
                    0,
                    request.getDecision()
                        .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS ? LEAVES : Collections.emptyList(),
                    now,
                    APPROACH_TIMEOUT_NANOS);
                trace("stand-back-goal", "x", point[0], "y", point[1], "z", point[2]);
            }
            if (request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS)
                navigationRequest = navigationRequest.withScaffolding(scaffoldExclusions);
            navigationHandle = navigation.submit(navigationRequest, lease);
            phase = Phase.APPROACHING;
            deadlineNanos = add(now, APPROACH_TIMEOUT_NANOS);
            state = ActionState.EXECUTING;
            detail = pendingApproachReason;
            trace("approach-start", "attempt", approachAttempt, "reason", pendingApproachReason);
        }

        private void returnToGround() {
            if (scaffoldCleanup == null) scaffoldCleanup = new ScaffoldCleanup(
                navigation,
                lease,
                request.getRequestId(),
                minecraft.theWorld.provider.dimensionId);
            if (!returningToGround && !scaffoldCleanup.poll()) {
                detail = "Removing all temporary tree pillar blocks";
                return;
            }
            if (navigationHandle == null) {
                if (!guard.isReadyForSession()) return;
                if (returningToGround) {
                    phase = Phase.CONFIRMING;
                    detail = "Returned from scaffolding; confirming the clear root";
                    return;
                }
                navigationHandle = navigation.submit(
                    new NavigationRequest(
                        request.getRequestId() + "-descend",
                        lease.getEpoch(),
                        minecraft.theWorld.provider.dimensionId,
                        returnX,
                        returnY,
                        returnZ,
                        1,
                        System.nanoTime(),
                        APPROACH_TIMEOUT_NANOS),
                    lease);
                returningToGround = true;
                return;
            }
            NavigationProgress progress = navigationHandle.progress();
            if (progress.getState() == NavigationState.COMPLETED) {
                navigationHandle = null;
            } else
                if (progress.getState() == NavigationState.FAILED || progress.getState() == NavigationState.CANCELLED) {
                    fail("Could not descend from tree scaffold: " + progress.getDetail());
                }
        }

        private void pollApproach() {
            if (request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS
                && minecraft.theWorld.isAirBlock(target.getX(), target.getY(), target.getZ())) {
                navigationHandle.cancel();
                navigationHandle = null;
                phase = Phase.WAITING_FOR_SESSION;
                detail = "Lumber axe cleared this log during approach; reconciling after packet drain";
                return;
            }
            boolean reached = request.getDecision()
                .getAction() == TreeActionKind.FELL_CAPTURED_BLOCKS ? canReachBlock(target) : canReachSupport(target);
            if (reached && VerticalMiningStability.isStable(minecraft.thePlayer)) {
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
                if (approachAttempt < 5) submitApproach("Repositioning for exact tree reach");
                else fail("Tree navigation completed outside exact interaction reach");
            } else if (progress.getState() == NavigationState.FAILED) {
                navigationHandle = null;
                if (approachAttempt < 5) submitApproach("Retrying tree approach after " + progress.getDetail());
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
            if (!VerticalMiningStability.isStable(minecraft.thePlayer)) {
                detail = "Waiting for supported, vertically stationary footing before cutting logs";
                return;
            }
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
            if (selectBestTool(target)) return;
            startDiggingWithSelectedTool();
        }

        private void startDiggingWithSelectedTool() {
            if (!VerticalMiningStability.isStable(minecraft.thePlayer)) {
                stopSession();
                phase = Phase.WAITING_FOR_SESSION;
                detail = "Waiting for vertical movement to stop after tool selection";
                return;
            }
            if (minecraft.theWorld.isAirBlock(target.getX(), target.getY(), target.getZ())) {
                stopSession();
                nextLog++;
                phase = Phase.WAITING_FOR_DRAIN;
                return;
            }
            if (!observer.hasExpectedLog(target, work.getRequiredSaplingFingerprint())) {
                fail("Tree log changed while staging its tool");
                return;
            }
            if (!canReachBlock(target)) {
                stopSession();
                submitApproach("Reapproaching after tree tool staging");
                return;
            }
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
            if (!VerticalMiningStability.isStable(minecraft.thePlayer)) {
                stopSession();
                phase = Phase.WAITING_FOR_SESSION;
                detail = "Tree mining paused until vertical movement stops";
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
                saplingDisplaced = inventorySnapshots
                    .fingerprint(minecraft.thePlayer.inventory.mainInventory[saplingHotbarSlot]);
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
            placementTick = minecraft.thePlayer.ticksExisted;
            deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
            detail = "Dispatching exact sapling placement";
            ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                synchronized (LiveHandle.this) {
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
            if (request.getDecision()
                .getAction() == TreeActionKind.PLANT_SAPLING) {
                // Local placement is optimistic: keep the staged slot/session intact briefly
                // before observing the result or advancing to another cell of a 2x2 pattern.
                int remaining = TreePlantingRetry
                    .settleTicksRemaining(minecraft.thePlayer.ticksExisted - placementTick);
                if (remaining > 0) {
                    detail = "Allowing sapling placement to settle: " + remaining + " ticks";
                    trace(
                        "plant-placement-settling",
                        "remainingTicks",
                        remaining,
                        "heldSlot",
                        minecraft.thePlayer.inventory.currentItem);
                    return;
                }
                phase = Phase.CONFIRMING;
                detail = "Checking placed sapling while keeping its hotbar slot staged";
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
                    if (!missing.equals(target)) {
                        stopSession();
                        placementRetries = 0;
                        preparePlant();
                    } else {
                        int elapsed = minecraft.thePlayer.ticksExisted - placementTick;
                        detail = "Sapling not observed at " + target
                            + "; placement retry "
                            + placementRetries
                            + "/3 in "
                            + Math.max(0, 40 - elapsed)
                            + " ticks";
                        trace(
                            "plant-confirmation-missing",
                            "elapsedTicks",
                            elapsed,
                            "retry",
                            placementRetries,
                            "block",
                            Block.blockRegistry.getNameForObject(
                                MinecraftRuntimeAccess
                                    .block(minecraft.theWorld, target.getX(), target.getY(), target.getZ())),
                            "heldSlot",
                            minecraft.thePlayer.inventory.currentItem,
                            "heldItem",
                            minecraft.thePlayer.getHeldItem());
                        if (TreePlantingRetry.ready(elapsed)) {
                            stopSession();
                            if (TreePlantingRetry.exhausted(placementRetries++)) {
                                fail(
                                    "Sapling placement was not confirmed after three retries at " + target
                                        + "; check soil, light, clearance and server placement restrictions");
                            } else preparePlant();
                        }
                    }
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
                trace("postcondition-confirmed", "expected", expected);
                confirmedAfter = after;
                if (expected == TreeObservationState.FELLED_CLEAR && stagedToolSource >= 9) {
                    beginToolReturn();
                    return;
                }
                if (expected == TreeObservationState.SAPLING_PLANTED) {
                    stopSession();
                    phase = Phase.FINAL_DRAIN;
                    detail = "Sapling confirmed; finishing inventory and action cleanup";
                    return;
                }
                state = ActionState.CONFIRMED;
                detail = expected == TreeObservationState.FELLED_CLEAR ? "Captured tree logs are confirmed clear"
                    : "Exact replacement sapling is confirmed";
                clearActive(this);
            } catch (RuntimeException waiting) {
                detail = "Waiting for tree postcondition: " + waiting.getMessage();
            }
        }

        private boolean selectBestTool(BasePosition position) {
            if (stagedToolSource >= 9) {
                ItemStack staged = minecraft.thePlayer.inventory.mainInventory[stagedToolHotbar];
                if (staged == null || staged.getItem() != stagedToolItem || broken(staged))
                    throw new IllegalStateException("The staged tree tool changed or became unusable");
                selectHotbar(stagedToolHotbar);
                return false;
            }
            Block targetBlock = MinecraftRuntimeAccess
                .block(minecraft.theWorld, position.getX(), position.getY(), position.getZ());
            int previous = minecraft.thePlayer.inventory.currentItem;
            ItemStack previousStack = minecraft.thePlayer.inventory.mainInventory[previous];
            int bestSlot = -1;
            double bestCost = Double.POSITIVE_INFINITY;
            int logs = 0, cubeLogs = 0, highLogs = 0;
            for (BasePosition log : work.getCapturedBlocks()) {
                if (!observer.hasExpectedLog(log, work.getRequiredSaplingFingerprint())) continue;
                logs++;
                if (Math.abs(log.getX() - position.getX()) <= 1 && Math.abs(log.getY() - position.getY()) <= 1
                    && Math.abs(log.getZ() - position.getZ()) <= 1) cubeLogs++;
                if (log.getY() > minecraft.thePlayer.boundingBox.minY + 3D) highLogs++;
            }
            try {
                for (int slot = 0; slot < (transactions == null ? 9 : 36); slot++) {
                    ItemStack candidate = minecraft.thePlayer.inventory.mainInventory[slot];
                    if (!ToolCapabilities.usable(candidate) || !ToolCapabilities.classes(candidate)
                        .contains("axe")) continue;
                    minecraft.thePlayer.inventory.currentItem = slot < 9 ? slot : previous;
                    if (slot >= 9) minecraft.thePlayer.inventory.mainInventory[previous] = candidate;
                    try {
                        float progress = targetBlock.getPlayerRelativeBlockHardness(
                            minecraft.thePlayer,
                            minecraft.theWorld,
                            position.getX(),
                            position.getY(),
                            position.getZ());
                        net.minecraft.item.ItemStack stack = minecraft.thePlayer.getHeldItem();
                        if (stack != null && stack.hasTagCompound()
                            && stack.getTagCompound()
                                .getCompoundTag("InfiTool")
                                .getBoolean("Broken"))
                            continue;
                        boolean lumber = stack != null && !minecraft.thePlayer.isSneaking()
                            && stack.getItem()
                                .getClass()
                                .getName()
                                .equals("tconstruct.items.tools.LumberAxe");
                        boolean wholeTree = false;
                        if (lumber) {
                            try {
                                wholeTree = (Boolean) stack.getItem()
                                    .getClass()
                                    .getMethod(
                                        "detectTree",
                                        net.minecraft.world.World.class,
                                        int.class,
                                        int.class,
                                        int.class)
                                    .invoke(
                                        null,
                                        minecraft.theWorld,
                                        position.getX(),
                                        position.getY(),
                                        position.getZ());
                            } catch (ReflectiveOperationException unavailable) {
                                trace("lumber-detection-unavailable", "reason", unavailable.toString());
                            }
                        }
                        double cost = TreeToolCost
                            .estimate(progress, Math.max(1, logs), cubeLogs, highLogs, lumber, wholeTree);
                        trace(
                            "tool-cost",
                            "slot",
                            slot,
                            "progress",
                            progress,
                            "logs",
                            logs,
                            "cubeLogs",
                            cubeLogs,
                            "highLogs",
                            highLogs,
                            "lumber",
                            lumber,
                            "wholeTree",
                            wholeTree,
                            "estimatedTicks",
                            cost);
                        if (cost < bestCost) {
                            bestCost = cost;
                            bestSlot = slot;
                        }
                    } finally {
                        minecraft.thePlayer.inventory.mainInventory[previous] = previousStack;
                    }
                }
            } finally {
                minecraft.thePlayer.inventory.currentItem = previous;
                minecraft.thePlayer.inventory.mainInventory[previous] = previousStack;
            }
            if (bestSlot < 0) throw new IllegalStateException(
                "No usable axe or hatchet in the 36 player slots; check bag access and tool durability");
            if (bestSlot >= 9) {
                requireToolInventory();
                stagedToolSource = bestSlot;
                stagedToolHotbar = chooseStagingHotbarSlot();
                stagedToolItem = minecraft.thePlayer.inventory.mainInventory[bestSlot].getItem();
                stagedToolDisplaced = inventorySnapshots
                    .fingerprint(minecraft.thePlayer.inventory.mainInventory[stagedToolHotbar]);
                toolTransaction = toolSwap("stage");
                transactions.begin(toolTransaction);
                phase = Phase.WAITING_FOR_TOOL;
                detail = "Waiting for the server to confirm the tree tool in the hotbar";
                return true;
            }
            selectHotbar(bestSlot);
            return false;
        }

        private void selectHotbar(int slot) {
            if (minecraft.thePlayer.inventory.currentItem == slot) return;
            minecraft.thePlayer.inventory.currentItem = slot;
            minecraft.playerController.updateController();
            toolSlotChanged = true;
        }

        private void requireToolInventory() {
            if (transactions == null || !lease.isValid()
                || !guard.isActiveLease(lease)
                || !lease.getCapabilities()
                    .contains(ActionCapability.CONTAINER)
                || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer
                || minecraft.thePlayer.inventory.getItemStack() != null)
                throw new IllegalStateException("Tree tool staging requires an owned player inventory session");
        }

        private ContainerTransaction toolSwap(String action) {
            requireToolInventory();
            ContainerSnapshot before = inventorySnapshots.captureCurrent(minecraft, inventoryRevision++);
            return PlayerInventoryHotbarSwap.plan(
                request.getRequestId() + "-tool-" + action + "-" + inventoryRevision,
                lease.getEpoch(),
                before,
                stagedToolSource,
                stagedToolHotbar);
        }

        private void pollToolTransfer() {
            if (toolTransaction == null || !guard.isActiveLease(lease)) {
                fail("Tree tool transaction lost its inventory session");
                return;
            }
            if (toolTransaction.getState() == ContainerTransactionState.ABORTED) {
                fail("Tree tool transfer was not confirmed: " + toolTransaction.getAbortReason());
                return;
            }
            if (toolTransaction.getState() != ContainerTransactionState.COMPLETED) return;
            toolTransaction = null;
            if (phase == Phase.RETURNING_TOOL) {
                stagedToolSource = -1;
                stopSession();
                phase = Phase.FINAL_DRAIN;
                detail = "Tree tool returned; waiting for inventory cleanup";
            } else {
                selectHotbar(stagedToolHotbar);
                startDiggingWithSelectedTool();
            }
        }

        private void beginToolReturn() {
            if (!guard.isReadyForSession()) {
                detail = "Waiting to return the staged tree tool";
                return;
            }
            ItemStack staged = minecraft.thePlayer.inventory.mainInventory[stagedToolHotbar];
            if (!Objects.equals(
                stagedToolDisplaced,
                inventorySnapshots.fingerprint(minecraft.thePlayer.inventory.mainInventory[stagedToolSource]))
                || staged != null && staged.getItem() != stagedToolItem) {
                // Drops may have filled the vacated source slot. Keep both synchronized stacks where they are.
                stagedToolSource = -1;
                phase = Phase.FINAL_DRAIN;
                detail = "Tree is clear; retained the staged tool because inventory contents changed";
                return;
            }
            guard.begin(lease);
            ownsSession = true;
            toolTransaction = toolSwap("return");
            transactions.begin(toolTransaction);
            phase = Phase.RETURNING_TOOL;
            detail = "Tree is clear; waiting for the server to confirm tool storage";
        }

        private boolean broken(ItemStack stack) {
            return stack.hasTagCompound() && stack.getTagCompound()
                .getCompoundTag("InfiTool")
                .getBoolean("Broken");
        }

        private int chooseStagingHotbarSlot() {
            for (int slot = 0; slot < 9; slot++)
                if (minecraft.thePlayer.inventory.mainInventory[slot] == null) return slot;
            return priorHotbarSlot;
        }

        private void returnStagedSapling() {
            if (!inventoryStaged) return;
            if (!lease.isValid() || !guard.isActiveLease(lease)
                || minecraft.thePlayer.inventory.getItemStack() != null
                || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer) return;
            if (!Objects.equals(
                saplingDisplaced,
                inventorySnapshots.fingerprint(minecraft.thePlayer.inventory.mainInventory[saplingSourceSlot]))) return;
            ItemStack remaining = minecraft.thePlayer.inventory.mainInventory[saplingHotbarSlot];
            if (remaining != null && !work.getRequiredSaplingFingerprint()
                .equals(MinecraftVanillaFarmObserver.materialIdentity(inventorySnapshots.fingerprint(remaining))))
                return;
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
            ExcavationTargetOverlay.show(new BlockPosition(target.getX(), target.getY(), target.getZ()));
        }

        private void restoreSlot() {
            if (!toolSlotChanged && minecraft.thePlayer.inventory.currentItem == priorHotbarSlot) return;
            minecraft.thePlayer.inventory.currentItem = priorHotbarSlot;
            if (lease.isValid() && guard.isActiveLease(lease)) minecraft.playerController.updateController();
            toolSlotChanged = false;
        }

        private void stopSession() {
            if (scaffoldCleanup != null) {
                scaffoldCleanup.close();
                scaffoldCleanup = null;
            }
            if (pillarStaging != null) {
                pillarStaging.close();
                pillarStaging = null;
            }
            if (toolTransaction != null) {
                transactions.cancel(toolTransaction, "tree tool action ended");
                toolTransaction = null;
            }
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
        REMOVING_OLD_SCAFFOLDS,
        WAITING_FOR_PILLAR_STACK,
        RETURNING_TO_GROUND,
        WAITING_FOR_TOOL,
        RETURNING_TOOL,
        WAITING_FOR_NAVIGATION,
        APPROACHING,
        WAITING_FOR_SESSION,
        DIGGING,
        WAITING_FOR_DRAIN,
        FINAL_DRAIN,
        CONFIRMING
    }
}
