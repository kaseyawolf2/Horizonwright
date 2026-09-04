package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.ForgeHooks;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationIntentKind;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetOutcome;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetResult;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairPolicy;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.ClientBootstrap;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
import io.github.kaseyawolf2.horizonwright.forge.client.repair.TinkersInventoryToolReader;
import io.github.kaseyawolf2.horizonwright.runtime.task.ConfirmedExcavationTargetResult;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationActionRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationActionState;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationBackend;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationBackendAvailability;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationObservationRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationObservationResult;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationServiceRequirements;

/** Moves within reach, digs one fingerprint-bound ordinary block, then confirms the exact target is air. */
public final class LiveExcavationBackend implements ExcavationBackend {

    public interface NavigationSource {

        NavigationBackend getNavigationBackend();
    }

    private static final EnumSet<ActionCapability> REQUIRED = EnumSet
        .of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.DIG, ActionCapability.HELD_USE);
    private static final long APPROACH_TIMEOUT_NANOS = NavigationRequest.MAX_RUNTIME_NANOS;
    private static final long LOCAL_ACTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(20L);
    private static final double TARGET_SAMPLE_INSET = 0.001D;

    private final Minecraft minecraft;
    private final ActionSessionGuard guard;
    private final NavigationSource navigationSource;
    private final MinecraftExcavationObserver observer;
    private final ExcavationServiceTriggerEvaluator serviceTriggers = new ExcavationServiceTriggerEvaluator(
        RepairPolicy.planDefaults());
    private final TinkersInventoryToolReader toolReader = new TinkersInventoryToolReader();
    private LiveHandle active;

    public LiveExcavationBackend(Minecraft minecraft, ActionSessionGuard guard, NavigationSource navigationSource) {
        if (minecraft == null || guard == null || navigationSource == null) {
            throw new IllegalArgumentException("minecraft, guard, and navigationSource are required");
        }
        this.minecraft = minecraft;
        this.guard = guard;
        this.navigationSource = navigationSource;
        this.observer = new MinecraftExcavationObserver(minecraft);
    }

    @Override
    public synchronized ExcavationBackendAvailability availability() {
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        if (navigation == null) {
            DevelopmentTrace
                .event("excavation-live", "availability", "available", false, "reason", "no-navigation-backend");
            return ExcavationBackendAvailability.unavailable("No navigation backend is configured for excavation");
        }
        BackendAvailability available = navigation.availability();
        ExcavationBackendAvailability result = available.isAvailable()
            ? ExcavationBackendAvailability
                .available("Exact vanilla block excavation ready through " + available.getDiagnostic())
            : ExcavationBackendAvailability
                .unavailable("Excavation navigation unavailable: " + available.getDiagnostic());
        DevelopmentTrace.event(
            "excavation-live",
            "availability",
            "available",
            result.isAvailable(),
            "diagnostic",
            result.getDiagnostic());
        return result;
    }

    @Override
    public ExcavationObservationResult observe(ExcavationObservationRequest request) {
        ExcavationObservation observation = observer.observe(request);
        ExcavationServiceRequirements requirements = request.getServiceRequirements();
        List<RepairToolSnapshot> tools = requirements.isRepairConfigured()
            && observation.getClassification() == ExcavationBlockClassification.BREAKABLE ? repairableTools()
                : java.util.Collections.<RepairToolSnapshot>emptyList();
        Optional<RepairToolSnapshot> repairTool = serviceTriggers.repairRequiredTool(requirements, tools);
        io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason suspension = serviceTriggers
            .evaluateAll(observation.getClassification(), requirements, emptyMainInventorySlots(), tools);
        ExcavationObservationResult result = new ExcavationObservationResult(
            request.getTaskRevision(),
            request.getActionEpoch(),
            request.getGeometryKey(),
            request.getStartFrontier(),
            observation,
            suspension,
            suspension == io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationSuspensionReason.REPAIR_REQUIRED
                ? repairTool.get()
                    .getReservedInventorySlot()
                : -1);
        DevelopmentTrace.event(
            "excavation-live",
            "observed",
            "taskRevision",
            request.getTaskRevision(),
            "epoch",
            request.getActionEpoch(),
            "geometry",
            request.getGeometryKey(),
            "frontier",
            request.getStartFrontier(),
            "position",
            observation.getPosition(),
            "classification",
            observation.getClassification(),
            "fingerprint",
            observation.getBlockFingerprint(),
            "suspension",
            result.getSuspensionReason(),
            "emptySlots",
            emptyMainInventorySlots(),
            "tinkersTools",
            tools.size(),
            "repairToolSlot",
            repairTool.isPresent() ? repairTool.get()
                .getReservedInventorySlot() : "none");
        return result;
    }

    private List<RepairToolSnapshot> repairableTools() {
        List<RepairToolSnapshot> tools = new ArrayList<>();
        for (int slot = 0; slot < minecraft.thePlayer.inventory.mainInventory.length; slot++) {
            Optional<RepairToolSnapshot> tool = toolReader
                .tryRead(minecraft.thePlayer.inventory.mainInventory[slot], slot);
            if (tool.isPresent()) tools.add(tool.get());
        }
        return tools;
    }

    private int emptyMainInventorySlots() {
        int empty = 0;
        for (int slot = 0; slot < minecraft.thePlayer.inventory.mainInventory.length; slot++) {
            if (minecraft.thePlayer.inventory.mainInventory[slot] == null) empty++;
        }
        return empty;
    }

    @Override
    public synchronized ExcavationActionHandle execute(ExcavationActionRequest request, ActionLease lease) {
        DevelopmentTrace.event(
            "excavation-live",
            "execute-request",
            "request",
            request == null ? "null" : request.getRequestId(),
            "task",
            request == null ? "null" : request.getTaskId(),
            "epoch",
            request == null ? -1L : request.getActionEpoch(),
            "intent",
            request == null ? "null"
                : request.getIntent()
                    .getKind(),
            "position",
            request == null ? "null"
                : request.getIntent()
                    .getPosition(),
            "preferredToolSlot",
            request == null ? -1 : request.getPreferredToolSlot(),
            "leaseValid",
            lease != null && lease.isValid(),
            "leaseCapabilities",
            lease == null ? "none" : lease.getCapabilities());
        if (request == null || lease == null) throw new IllegalArgumentException("request and lease are required");
        if (active != null && !active.isTerminal()) throw new IllegalStateException("an excavation action is active");
        if (!lease.isValid() || lease.getEpoch() != request.getActionEpoch()
            || !lease.getCapabilities()
                .containsAll(REQUIRED)) {
            throw new IllegalArgumentException("a matching MOVEMENT, LOOK, and DIG lease is required");
        }
        requireClientThread();
        ExcavationObservation current = observer.observePosition(
            request.getDimensionId(),
            request.getIntent()
                .getPosition());
        if (!request.getIntent()
            .getObservedFingerprint()
            .equals(current.getBlockFingerprint())) {
            throw new IllegalStateException("the excavation target changed after planning");
        }
        ExcavationTargetOutcome immediate = immediateOutcome(
            request.getIntent()
                .getKind());
        if (immediate != null) {
            DevelopmentTrace
                .event("excavation-live", "immediate", "request", request.getRequestId(), "outcome", immediate);
            return new ImmediateHandle(request, immediate);
        }
        if (request.getIntent()
            .getKind() != ExcavationIntentKind.BREAK_BLOCK) {
            throw new IllegalArgumentException(
                "unsupported live excavation intent " + request.getIntent()
                    .getKind());
        }
        if (current.getClassification() != ExcavationBlockClassification.BREAKABLE) {
            throw new IllegalStateException("the exact target is no longer an ordinary breakable block");
        }
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        ExcavationBackendAvailability available = availability();
        if (navigation == null || !available.isAvailable()) throw new IllegalStateException(available.getDiagnostic());
        LiveHandle handle = new LiveHandle(request, lease, navigation, System.nanoTime());
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

    private synchronized void clearActive(LiveHandle handle) {
        if (active == handle) active = null;
    }

    private void requireClientThread() {
        if (!minecraft.func_152345_ab())
            throw new IllegalStateException("excavation action requires the client thread");
    }

    private static ExcavationTargetOutcome immediateOutcome(ExcavationIntentKind kind) {
        switch (kind) {
            case ALREADY_CLEAR:
                return ExcavationTargetOutcome.COMPLETED;
            case PROTECT_GRAVE:
            case PROTECT_INFRASTRUCTURE:
                return ExcavationTargetOutcome.PROTECTED;
            case MARK_UNREACHABLE:
                return ExcavationTargetOutcome.UNREACHABLE;
            case MARK_FAILED:
                return ExcavationTargetOutcome.FAILED;
            default:
                return null;
        }
    }

    private static ConfirmedExcavationTargetResult confirmation(ExcavationActionRequest request,
        ExcavationTargetOutcome outcome) {
        return new ConfirmedExcavationTargetResult(
            request.getTaskRevision(),
            request.getActionEpoch(),
            request.getGeometryKey(),
            request.getStartFrontier(),
            request.getIntent()
                .getObservedFingerprint(),
            new ExcavationTargetResult(
                request.getIntent()
                    .getPosition(),
                outcome));
    }

    private final class LiveHandle implements ExcavationActionHandle {

        private final ExcavationActionRequest request;
        private final ActionLease lease;
        private final NavigationBackend navigation;
        private long deadlineNanos;
        private NavigationHandle navigationHandle;
        private Phase phase = Phase.APPROACHING;
        private ExcavationActionState state = ExcavationActionState.SUBMITTED;
        private String detail = "Preparing exact-target approach";
        private ConfirmedExcavationTargetResult confirmed;
        private boolean ownsDigSession;
        private int priorHotbarSlot = -1;
        private boolean toolSlotChanged;
        private TargetAim verifiedTargetAim;
        private volatile boolean cancellationRequested;

        private LiveHandle(ExcavationActionRequest request, ActionLease lease, NavigationBackend navigation,
            long startedAtNanos) {
            this.request = request;
            this.lease = lease;
            this.navigation = navigation;
            this.deadlineNanos = saturatingAdd(startedAtNanos, APPROACH_TIMEOUT_NANOS);
        }

        private void start() {
            if (canReachTarget()) {
                phase = Phase.WAITING_FOR_DIG_SESSION;
                deadlineNanos = saturatingAdd(System.nanoTime(), LOCAL_ACTION_TIMEOUT_NANOS);
                state = ExcavationActionState.EXECUTING;
                detail = "Target is within confirmed reach";
                trace("phase", "reach", true);
                return;
            }
            BlockPosition position = request.getIntent()
                .getPosition();
            NavigationRequest approach = NavigationRequest.adjacentTo(
                request.getRequestId() + "-approach",
                request.getActionEpoch(),
                request.getDimensionId(),
                position.getX(),
                position.getY(),
                position.getZ(),
                System.nanoTime(),
                APPROACH_TIMEOUT_NANOS);
            navigationHandle = navigation.submit(approach, lease);
            state = ExcavationActionState.EXECUTING;
            detail = "Approaching exact excavation target";
            trace("phase", "navigationRequest", navigationHandle.getRequestId());
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public synchronized ExcavationActionProgress progress() {
            requireClientThread();
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
                minecraft.thePlayer == null ? "missing" : minecraft.thePlayer.posX,
                "playerY",
                minecraft.thePlayer == null ? "missing" : minecraft.thePlayer.posY,
                "playerZ",
                minecraft.thePlayer == null ? "missing" : minecraft.thePlayer.posZ,
                "selectedSlot",
                minecraft.thePlayer == null ? "missing" : minecraft.thePlayer.inventory.currentItem);
            if (state == ExcavationActionState.CONFIRMED || state == ExcavationActionState.FAILED
                || state == ExcavationActionState.CANCELLED) return snapshot();
            if (cancellationRequested) {
                cancelOnClientThread();
                return snapshot();
            }
            if (!lease.isValid()) {
                stopProducers();
                fail("Excavation action lease was revoked");
                return snapshot();
            }
            if (System.nanoTime() - deadlineNanos >= 0L) {
                stopProducers();
                fail("Excavation action deadline exceeded");
                return snapshot();
            }
            if (phase == Phase.APPROACHING) pollApproach();
            else if (phase == Phase.WAITING_FOR_DIG_SESSION) beginDigWhenReady();
            else if (phase == Phase.DIGGING) digOneTick();
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

        private synchronized void pollApproach() {
            if (canReachTarget()) {
                navigationHandle.cancel();
                navigationHandle = null;
                phase = Phase.WAITING_FOR_DIG_SESSION;
                deadlineNanos = saturatingAdd(System.nanoTime(), LOCAL_ACTION_TIMEOUT_NANOS);
                detail = "Exact target became reachable before the navigation goal completed";
                trace("approach-reach", "navigationCancelled", true);
                return;
            }
            NavigationProgress progress = navigationHandle.progress();
            trace("approach", "navigationState", progress.getState(), "navigationDetail", progress.getDetail());
            if (progress.getState() == NavigationState.COMPLETED) {
                navigationHandle = null;
                phase = Phase.WAITING_FOR_DIG_SESSION;
                deadlineNanos = saturatingAdd(System.nanoTime(), LOCAL_ACTION_TIMEOUT_NANOS);
                detail = "Approach complete; waiting for the packet drain boundary";
            } else if (progress.getState() == NavigationState.FAILED) {
                fail("Could not approach excavation target: " + progress.getDetail());
            } else if (progress.getState() == NavigationState.CANCELLED) {
                state = ExcavationActionState.CANCELLED;
                detail = "Excavation approach was cancelled";
                clearActive(this);
            } else {
                detail = "Approaching target: " + progress.getDetail();
            }
        }

        private synchronized void beginDigWhenReady() {
            if (!guard.isReadyForSession()) {
                detail = "Waiting for approach packets to drain";
                trace("packet-drain", "blockedActions", guard.getBlockedActionCount());
                return;
            }
            ExcavationObservation current = currentObservation();
            traceObservation("post-approach", current);
            if (isAir(current)) {
                confirm(ExcavationTargetOutcome.COMPLETED, "Target became air before digging");
                return;
            }
            if (!sameFingerprint(current)) {
                fail("Excavation target changed after approach");
                return;
            }
            if (current.getClassification() != ExcavationBlockClassification.BREAKABLE) {
                fail("The approached target is no longer an ordinary breakable block");
                return;
            }
            if (!canReachTarget()) {
                confirm(ExcavationTargetOutcome.UNREACHABLE, "Approach ended without exact target reach");
                return;
            }
            guard.begin(lease);
            ownsDigSession = true;
            ClientBootstrap.blockDamageShield()
                .acquire(request.getRequestId());
            selectBestHotbarTool();
            phase = Phase.DIGGING;
            detail = "Digging one fingerprint-bound block";
            aimAtTarget();
            BlockPosition position = request.getIntent()
                .getPosition();
            minecraft.playerController.clickBlock(position.getX(), position.getY(), position.getZ(), targetSide());
            trace("dig-start", "selectedSlot", minecraft.thePlayer.inventory.currentItem, "side", targetSide());
        }

        private synchronized void digOneTick() {
            if (!guard.isActiveLease(lease)) {
                stopDigSession();
                fail("Exact digging session lost packet authority");
                return;
            }
            ExcavationObservation current = currentObservation();
            traceObservation("dig-observation", current);
            if (isAir(current)) {
                finishConfirmedDig();
                return;
            }
            if (!sameFingerprint(current)) {
                stopDigSession();
                fail("Excavation target changed while digging");
                return;
            }
            if (current.getClassification() != ExcavationBlockClassification.BREAKABLE) {
                stopDigSession();
                fail("The target lost its ordinary breakable classification while digging");
                return;
            }
            if (!canReachTarget()) {
                stopDigSession();
                fail("Player moved out of exact digging reach");
                return;
            }
            aimAtTarget();
            BlockPosition position = request.getIntent()
                .getPosition();
            minecraft.playerController
                .onPlayerDamageBlock(position.getX(), position.getY(), position.getZ(), targetSide());
            ClientBootstrap.blockDamageShield()
                .checkpoint();
            trace(
                "dig-tick",
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

        private synchronized void cancelOnClientThread() {
            if (isTerminal()) return;
            if (navigationHandle != null) {
                navigationHandle.cancel();
                navigationHandle = null;
            }
            stopDigSession();
            state = ExcavationActionState.CANCELLED;
            detail = "Excavation action cancelled before checkpoint advancement";
            clearActive(this);
        }

        private void stopDigSession() {
            if (!ownsDigSession) return;
            ClientBootstrap.blockDamageShield()
                .release(request.getRequestId());
            minecraft.playerController.resetBlockRemoving();
            restoreHotbarSlot();
            guard.quarantine(lease);
            guard.end(lease);
            ownsDigSession = false;
        }

        private void finishConfirmedDig() {
            ClientBootstrap.blockDamageShield()
                .release(request.getRequestId());
            minecraft.playerController.resetBlockRemoving();
            restoreHotbarSlot();
            phase = Phase.FINISHING;
            detail = "Exact target is air; dispatching the final tool state";
            try {
                ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                    synchronized (LiveHandle.this) {
                        if (ownsDigSession) {
                            guard.quarantine(lease);
                            guard.end(lease);
                            ownsDigSession = false;
                        }
                        confirm(ExcavationTargetOutcome.COMPLETED, "Exact target is confirmed air");
                    }
                });
            } catch (RuntimeException failure) {
                stopDigSession();
                fail("Could not finish the excavation packet boundary: " + failure.getMessage());
            }
        }

        private void selectBestHotbarTool() {
            BlockPosition position = request.getIntent()
                .getPosition();
            Block target = MinecraftRuntimeAccess
                .block(minecraft.theWorld, position.getX(), position.getY(), position.getZ());
            int metadata = MinecraftRuntimeAccess
                .blockMetadata(minecraft.theWorld, position.getX(), position.getY(), position.getZ());
            int preferred = request.getPreferredToolSlot();
            int previous = minecraft.thePlayer.inventory.currentItem;
            ExcavationToolCandidateScore best = null;
            try {
                for (int slot = 0; slot < 9; slot++) {
                    minecraft.thePlayer.inventory.currentItem = slot;
                    ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
                    ExcavationToolCandidateScore candidate = scoreTool(
                        slot,
                        stack,
                        target,
                        metadata,
                        position,
                        slot == preferred);
                    traceToolCandidate(candidate, stack, target, metadata);
                    if (candidate.isBetterThan(best)) best = candidate;
                }
            } finally {
                minecraft.thePlayer.inventory.currentItem = previous;
            }
            if (best == null) throw new IllegalStateException("hotbar tool evaluation returned no candidates");
            int selected = best.getSlot();
            priorHotbarSlot = previous;
            if (selected != priorHotbarSlot) {
                minecraft.thePlayer.inventory.currentItem = selected;
                minecraft.playerController.updateController();
                toolSlotChanged = true;
            }
            trace(
                "tool-selected",
                "selected",
                selected,
                "previous",
                priorHotbarSlot,
                "preferred",
                preferred,
                "canHarvest",
                best.canHarvest(),
                "effectiveToolClass",
                best.isEffectiveToolClass(),
                "progressPerTick",
                best.getProgressPerTick(),
                "remainingFraction",
                best.getRemainingFraction(),
                "changed",
                toolSlotChanged);
        }

        private ExcavationToolCandidateScore scoreTool(int slot, ItemStack stack, Block target, int metadata,
            BlockPosition position, boolean preferred) {
            boolean canHarvest = ForgeHooks.canHarvestBlock(target, minecraft.thePlayer, metadata);
            float progress;
            try {
                progress = target.getPlayerRelativeBlockHardness(
                    minecraft.thePlayer,
                    minecraft.theWorld,
                    position.getX(),
                    position.getY(),
                    position.getZ());
            } catch (RuntimeException failure) {
                DevelopmentTrace.event(
                    "excavation-live",
                    "tool-progress-failed",
                    "request",
                    request.getRequestId(),
                    "slot",
                    slot,
                    "failure",
                    DevelopmentTrace.error(failure));
                progress = 0.0F;
            }
            if (Float.isNaN(progress) || progress < 0.0F) progress = 0.0F;
            double remaining = remainingFraction(stack, slot);
            return new ExcavationToolCandidateScore(
                slot,
                stack == null || remaining > 0.0D,
                canHarvest,
                progress,
                hasEffectiveToolClass(stack, target, metadata),
                remaining,
                preferred);
        }

        private boolean hasEffectiveToolClass(ItemStack stack, Block target, int metadata) {
            if (stack == null) return false;
            try {
                if (ForgeHooks.isToolEffective(stack, target, metadata)) return true;
                String expected = expectedToolClass(target, metadata);
                if (expected == null) return false;
                Set<String> classes = stack.getItem()
                    .getToolClasses(stack);
                return classes.contains(expected) || stack.getItem()
                    .getHarvestLevel(stack, expected) >= 0;
            } catch (RuntimeException failure) {
                return false;
            }
        }

        private String expectedToolClass(Block target, int metadata) {
            String declared = target.getHarvestTool(metadata);
            if (declared != null) return declared;
            Material material = target.getMaterial();
            if (material == Material.rock || material == Material.iron
                || material == Material.anvil
                || material == Material.glass
                || material == Material.ice
                || material == Material.packedIce) {
                return "pickaxe";
            }
            if (material == Material.wood) return "axe";
            if (material == Material.ground || material == Material.grass
                || material == Material.sand
                || material == Material.clay
                || material == Material.snow
                || material == Material.craftedSnow) {
                return "shovel";
            }
            return null;
        }

        private double remainingFraction(ItemStack stack, int slot) {
            if (stack == null) return 1.0D;
            try {
                return toolReader.read(stack, slot)
                    .getRemainingFraction();
            } catch (RuntimeException notTinkersTool) {
                if (!stack.isItemStackDamageable()) return 1.0D;
                int maximum = stack.getMaxDamage();
                return maximum <= 0 ? 1.0D
                    : Math.max(0.0D, Math.min(1.0D, (double) (maximum - stack.getItemDamage()) / maximum));
            }
        }

        private void traceToolCandidate(ExcavationToolCandidateScore candidate, ItemStack stack, Block target,
            int metadata) {
            float metadataAwareDigSpeed;
            try {
                metadataAwareDigSpeed = stack == null ? 1.0F
                    : stack.getItem()
                        .getDigSpeed(stack, target, metadata);
            } catch (RuntimeException failure) {
                metadataAwareDigSpeed = 0.0F;
            }
            DevelopmentTrace.event(
                "excavation-live",
                "tool-candidate",
                "request",
                request.getRequestId(),
                "slot",
                candidate.getSlot(),
                "item",
                describeStack(stack),
                "block",
                Block.blockRegistry.getNameForObject(target),
                "metadata",
                metadata,
                "usable",
                candidate.isUsable(),
                "canHarvest",
                candidate.canHarvest(),
                "effectiveToolClass",
                candidate.isEffectiveToolClass(),
                "progressPerTick",
                candidate.getProgressPerTick(),
                "metadataAwareDigSpeed",
                metadataAwareDigSpeed,
                "remainingFraction",
                candidate.getRemainingFraction(),
                "preferred",
                candidate.isPreferred());
        }

        private void restoreHotbarSlot() {
            if (!toolSlotChanged || priorHotbarSlot < 0) return;
            minecraft.thePlayer.inventory.currentItem = priorHotbarSlot;
            minecraft.playerController.updateController();
            toolSlotChanged = false;
        }

        private void stopProducers() {
            if (navigationHandle != null) {
                navigationHandle.cancel();
                navigationHandle = null;
            }
            stopDigSession();
        }

        private void confirm(ExcavationTargetOutcome outcome, String confirmationDetail) {
            confirmed = confirmation(request, outcome);
            state = ExcavationActionState.CONFIRMED;
            detail = confirmationDetail;
            trace("confirmed", "outcome", outcome);
            clearActive(this);
        }

        private void fail(String failureDetail) {
            state = ExcavationActionState.FAILED;
            detail = failureDetail;
            trace("failed", "failure", failureDetail);
            clearActive(this);
        }

        private ExcavationObservation currentObservation() {
            return observer.observePosition(
                request.getDimensionId(),
                request.getIntent()
                    .getPosition());
        }

        private boolean sameFingerprint(ExcavationObservation observation) {
            return request.getIntent()
                .getObservedFingerprint()
                .equals(observation.getBlockFingerprint());
        }

        private boolean isAir(ExcavationObservation observation) {
            return observation.getClassification() == ExcavationBlockClassification.AIR;
        }

        private boolean canReachTarget() {
            verifiedTargetAim = reachableTargetAim();
            return verifiedTargetAim != null;
        }

        private TargetAim reachableTargetAim() {
            if (minecraft.thePlayer == null || minecraft.theWorld == null
                || minecraft.playerController == null
                || minecraft.theWorld.provider == null
                || minecraft.theWorld.provider.dimensionId != request.getDimensionId()) {
                trace("reach", "reachable", false, "reason", "client-or-dimension-unavailable");
                return null;
            }
            BlockPosition position = request.getIntent()
                .getPosition();
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = Vec3
                .createVectorHelper(player.posX, player.posY + MinecraftRuntimeAccess.eyeHeight(player), player.posZ);
            double reach = minecraft.playerController.getBlockReachDistance();
            Block block = minecraft.theWorld.getBlock(position.getX(), position.getY(), position.getZ());
            block.setBlockBoundsBasedOnState(minecraft.theWorld, position.getX(), position.getY(), position.getZ());
            AxisAlignedBB bounds = block
                .getSelectedBoundingBoxFromPool(minecraft.theWorld, position.getX(), position.getY(), position.getZ());
            if (bounds == null) {
                trace("reach", "reachable", false, "reason", "no-selection-bounds");
                return null;
            }
            double[] xs = sampleAxis(bounds.minX, bounds.maxX);
            double[] ys = sampleAxis(bounds.minY, bounds.maxY);
            double[] zs = sampleAxis(bounds.minZ, bounds.maxZ);
            TargetAim best = null;
            MovingObjectPosition nearestMismatch = null;
            for (double x : xs) {
                for (double y : ys) {
                    for (double z : zs) {
                        Vec3 sample = Vec3.createVectorHelper(x, y, z);
                        double sampleDistanceSquared = eyes.squareDistanceTo(sample);
                        if (sampleDistanceSquared > reach * reach) continue;
                        MovingObjectPosition hit = MinecraftRuntimeAccess
                            .rayTraceBlocks(minecraft.theWorld, eyes, sample, false);
                        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
                        if (hit.blockX != position.getX() || hit.blockY != position.getY()
                            || hit.blockZ != position.getZ()) {
                            nearestMismatch = hit;
                            continue;
                        }
                        if (best == null || sampleDistanceSquared < best.distanceSquared) {
                            best = new TargetAim(sample, hit.sideHit, sampleDistanceSquared);
                        }
                    }
                }
            }
            trace(
                "reach",
                "reachable",
                best != null,
                "reason",
                best != null ? "visible-target-shape" : "no-visible-point-within-reach",
                "distanceSquared",
                best == null ? "none" : best.distanceSquared,
                "reachSquared",
                reach * reach,
                "sample",
                best == null ? "none" : best.point,
                "hit",
                nearestMismatch == null ? "none"
                    : nearestMismatch.typeOfHit + ":"
                        + nearestMismatch.blockX
                        + ","
                        + nearestMismatch.blockY
                        + ","
                        + nearestMismatch.blockZ);
            return best;
        }

        private double[] sampleAxis(double minimum, double maximum) {
            double inset = Math.min(TARGET_SAMPLE_INSET, Math.max(0.0D, (maximum - minimum) / 4.0D));
            return new double[] { minimum + inset, (minimum + maximum) / 2.0D, maximum - inset };
        }

        private void traceObservation(String event, ExcavationObservation observation) {
            DevelopmentTrace.event(
                "excavation-live",
                event,
                "request",
                request.getRequestId(),
                "phase",
                phase,
                "position",
                observation.getPosition(),
                "classification",
                observation.getClassification(),
                "fingerprint",
                observation.getBlockFingerprint(),
                "plannedFingerprint",
                request.getIntent()
                    .getObservedFingerprint());
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
            fields[9] = request.getIntent()
                .getPosition();
            System.arraycopy(extraFields, 0, fields, 10, extraFields.length);
            DevelopmentTrace.event("excavation-live", event, fields);
        }

        private void aimAtTarget() {
            EntityPlayer player = minecraft.thePlayer;
            TargetAim aim = verifiedTargetAim;
            BlockPosition position = request.getIntent()
                .getPosition();
            Vec3 point = aim == null
                ? Vec3.createVectorHelper(position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D)
                : aim.point;
            double dx = point.xCoord - player.posX;
            double dy = point.yCoord - (player.posY + MinecraftRuntimeAccess.eyeHeight(player));
            double dz = point.zCoord - player.posZ;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            player.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            player.rotationPitch = (float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI);
        }

        private int targetSide() {
            TargetAim aim = verifiedTargetAim;
            if (aim != null) return aim.side;
            EntityPlayer player = minecraft.thePlayer;
            BlockPosition position = request.getIntent()
                .getPosition();
            double dx = player.posX - (position.getX() + 0.5D);
            double dy = player.posY + MinecraftRuntimeAccess.eyeHeight(player) - (position.getY() + 0.5D);
            double dz = player.posZ - (position.getZ() + 0.5D);
            double ax = Math.abs(dx);
            double ay = Math.abs(dy);
            double az = Math.abs(dz);
            if (ay >= ax && ay >= az) return dy > 0.0D ? 1 : 0;
            if (ax >= az) return dx > 0.0D ? 5 : 4;
            return dz > 0.0D ? 3 : 2;
        }

        private final class TargetAim {

            private final Vec3 point;
            private final int side;
            private final double distanceSquared;

            private TargetAim(Vec3 point, int side, double distanceSquared) {
                this.point = point;
                this.side = side;
                this.distanceSquared = distanceSquared;
            }
        }

        private ExcavationActionProgress snapshot() {
            return new ExcavationActionProgress(request.getRequestId(), state, detail, confirmed);
        }

        private boolean isTerminal() {
            return state == ExcavationActionState.CONFIRMED || state == ExcavationActionState.CANCELLED
                || state == ExcavationActionState.FAILED;
        }
    }

    private static String describeStack(ItemStack stack) {
        if (stack == null) return "empty";
        Object itemName = ItemStack.class.cast(stack)
            .getItem();
        return itemName + ":meta=" + stack.getItemDamage() + ":count=" + stack.stackSize;
    }

    private static final class ImmediateHandle implements ExcavationActionHandle {

        private final ExcavationActionRequest request;
        private final ConfirmedExcavationTargetResult confirmed;

        private ImmediateHandle(ExcavationActionRequest request, ExcavationTargetOutcome outcome) {
            this.request = request;
            this.confirmed = confirmation(request, outcome);
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public ExcavationActionProgress progress() {
            return new ExcavationActionProgress(
                request.getRequestId(),
                ExcavationActionState.CONFIRMED,
                "Non-mutating excavation intent confirmed from exact observation",
                confirmed);
        }

        @Override
        public void cancel() {}
    }

    private enum Phase {
        APPROACHING,
        WAITING_FOR_DIG_SESSION,
        DIGGING,
        FINISHING
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }
}
