package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.event.events.SprintStateEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.compat.BlockPos;
import baritone.pathing.movement.CalculationContext;
import baritone.utils.PathingCommandContext;
import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocation;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocationListener;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.CropTravelSafety;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationGoalKind;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;

public final class BaritoneNavigationBackend
    implements NavigationBackend, CropTravelSafety, ActionRevocationListener, AbstractGameEventListener {

    static final int MAX_CLEANUP_ATTEMPTS = 3;

    private static final EnumSet<ActionCapability> REQUIRED_CAPABILITIES = EnumSet
        .of(ActionCapability.MOVEMENT, ActionCapability.LOOK);

    private final IBaritone baritone;
    private final ActionSessionGuard actionSessionGuard;
    private final HorizonwrightBaritoneProcess process;
    private final CropsNhTravelPolicy cropsNhTravel = new CropsNhTravelPolicy();
    private final Set<String> cropOperations = new HashSet<>();
    private boolean cropSprintSuppressed;
    private volatile BackendAvailability availability = BackendAvailability
        .available("Baritone enhanced build fcbbd4882c ready (scoped navigation contexts)");
    private Handle active;
    private PendingCleanup pendingCleanup;

    BaritoneNavigationBackend(IBaritone baritone, ActionSessionGuard actionSessionGuard) {
        if (baritone == null || actionSessionGuard == null) {
            throw new IllegalArgumentException("baritone and actionSessionGuard are required");
        }
        this.baritone = baritone;
        this.actionSessionGuard = actionSessionGuard;
        this.process = new HorizonwrightBaritoneProcess(this, baritone);
        baritone.getPathingControlManager()
            .registerProcess(process);
        baritone.getGameEventHandler()
            .registerEventListener(this);
        DevelopmentTrace.event("navigation", "backend-created", "availability", availability.getDiagnostic());
    }

    @Override
    public synchronized BackendAvailability availability() {
        DevelopmentTrace.event(
            "navigation",
            "availability",
            "available",
            availability.isAvailable(),
            "diagnostic",
            availability.getDiagnostic(),
            "active",
            active == null ? "none" : active.request.getRequestId());
        return availability;
    }

    @Override
    public synchronized NavigationHandle submit(NavigationRequest request, ActionLease movementLease) {
        if (request == null || movementLease == null) {
            throw new IllegalArgumentException("request and movementLease are required");
        }
        if (!availability.isAvailable()) {
            throw new IllegalStateException(availability.getDiagnostic());
        }
        if (!movementLease.isValid()) {
            throw new IllegalArgumentException("movement lease is not valid");
        }
        EnumSet<ActionCapability> requiredCapabilities = EnumSet.copyOf(REQUIRED_CAPABILITIES);
        if (request.isPlacementAllowed()) {
            requiredCapabilities.add(ActionCapability.PLACE);
            requiredCapabilities.add(ActionCapability.HELD_USE);
        }
        if (request.isBreakingAllowed()) requiredCapabilities.add(ActionCapability.DIG);
        if (!movementLease.getCapabilities()
            .containsAll(requiredCapabilities)) {
            throw new IllegalArgumentException(
                "Baritone navigation requires capabilities " + requiredCapabilities + " for this request");
        }
        if (request.getActionEpoch() != movementLease.getEpoch()) {
            throw new IllegalArgumentException("request and lease epochs differ");
        }
        if (active != null && !active.isTerminal()) {
            throw new IllegalStateException("Baritone navigation already has an active request");
        }
        boolean sessionReady = movementLease.isSafetyRecoveryLease()
            ? actionSessionGuard.isReadyForSafetyRecoverySession()
            : actionSessionGuard.isReadyForSession();
        if (pendingCleanup != null || !sessionReady) {
            throw new IllegalStateException(actionSessionGuard.readinessDiagnostic());
        }
        requireClientThread();
        requireRequestWorld(request);
        DevelopmentTrace.event(
            "navigation",
            "submit",
            "request",
            request.getRequestId(),
            "epoch",
            request.getActionEpoch(),
            "dimension",
            request.getDimensionId(),
            "goalKind",
            request.getGoalKind(),
            "x",
            request.getX(),
            "y",
            request.getY(),
            "z",
            request.getZ(),
            "tolerance",
            request.getTolerance(),
            "allowPlacement",
            request.isPlacementAllowed(),
            "allowedBreakBlocks",
            request.getAllowedBreakBlockIds(),
            "leaseOwner",
            movementLease.getOwner(),
            "safetyRecovery",
            movementLease.isSafetyRecoveryLease());

        BlockPos target = new BlockPos(request.getX(), request.getY(), request.getZ());
        Goal goal = request.getGoalKind() == NavigationGoalKind.ADJACENT ? new GoalGetToBlock(target)
            : request.getTolerance() == 0 ? new GoalBlock(request.getX(), request.getY(), request.getZ())
                : new GoalNear(target, request.getTolerance());
        CalculationContext calculationContext = createMovementContext(
            request.isPlacementAllowed(),
            request.getAllowedBreakBlockIds());
        Handle handle = new Handle(this, request, movementLease, goal, calculationContext);
        actionSessionGuard.begin(movementLease);
        active = handle;
        process.activate(handle);
        DevelopmentTrace.event(
            "navigation",
            "activated",
            "request",
            request.getRequestId(),
            "goal",
            goal,
            "deadlineNanos",
            request.getDeadlineNanos());
        return handle;
    }

    @Override
    public void onActionEpochRevoked(ActionRevocation revocation) {
        DevelopmentTrace.event(
            "navigation",
            "epoch-revoked",
            "revokedEpoch",
            revocation.getRevokedEpoch(),
            "replacementEpoch",
            revocation.getNewEpoch(),
            "reason",
            revocation.getReason());
        Runnable cancellation = () -> cancelRevokedEpoch(revocation);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.func_152345_ab()) {
            cancellation.run();
        } else {
            minecraft.func_152344_a(cancellation);
        }
    }

    @Override
    public void clientTick() {
        enforceCropSprintSafety();
        PendingCleanup cleanup;
        synchronized (this) {
            cleanup = pendingCleanup;
        }
        if (cleanup != null) {
            cleanupTerminal(cleanup);
        }
    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {
        if (event == null) return;
        boolean protectionRequested;
        synchronized (this) {
            protectionRequested = cropProtectionRequested();
        }
        if (protectionRequested && cropsNhTravel.shouldSuppressSprint(
            baritone.getPlayerContext()
                .world(),
            baritone.getPlayerContext()
                .player()))
            event.setState(false);
    }

    private void enforceCropSprintSafety() {
        Handle handle;
        boolean protectionRequested;
        synchronized (this) {
            handle = active;
            protectionRequested = cropProtectionRequested();
        }
        boolean suppress = protectionRequested && baritone.getPlayerContext()
            .world() != null
            && baritone.getPlayerContext()
                .player() != null
            && cropsNhTravel.shouldSuppressSprint(
                baritone.getPlayerContext()
                    .world(),
                baritone.getPlayerContext()
                    .player());
        if (suppress) baritone.getPlayerContext()
            .player()
            .setSprinting(false);
        if (cropSprintSuppressed != suppress) {
            cropSprintSuppressed = suppress;
            DevelopmentTrace.event(
                "navigation",
                "cropsnh-sprint-suppression",
                "active",
                suppress,
                "request",
                handle == null ? "none" : handle.request.getRequestId(),
                "cropOperations",
                cropOperationCount(),
                "radius",
                CropsNhTravelPolicy.SPRINT_SUPPRESSION_RADIUS);
        }
    }

    @Override
    public synchronized void beginCropOperation(String operationId) {
        String normalized = requireOperationId(operationId);
        if (cropOperations.add(normalized)) {
            DevelopmentTrace.event(
                "navigation",
                "cropsnh-protection-acquired",
                "operation",
                normalized,
                "owners",
                cropOperations.size());
        }
    }

    @Override
    public synchronized void endCropOperation(String operationId) {
        String normalized = requireOperationId(operationId);
        if (cropOperations.remove(normalized)) {
            DevelopmentTrace.event(
                "navigation",
                "cropsnh-protection-released",
                "operation",
                normalized,
                "owners",
                cropOperations.size());
        }
    }

    private boolean cropProtectionRequested() {
        return active != null && !active.isTerminal() || !cropOperations.isEmpty();
    }

    private synchronized int cropOperationCount() {
        return cropOperations.size();
    }

    private static String requireOperationId(String operationId) {
        if (operationId == null || operationId.trim()
            .isEmpty()) throw new IllegalArgumentException("crop operation id is required");
        return operationId.trim();
    }

    synchronized Handle activeHandle() {
        return active;
    }

    synchronized boolean validateActive(Handle handle) {
        if (active != handle || handle.isTerminal()) {
            DevelopmentTrace.event(
                "navigation",
                "validation-rejected",
                "request",
                handle.request.getRequestId(),
                "state",
                handle.state,
                "isActiveHandle",
                active == handle);
            return false;
        }
        if (!handle.movementLease.isValid() || !actionSessionGuard.isActiveLease(handle.movementLease)) {
            finishFromProcess(handle, NavigationState.CANCELLED, "Action lease was revoked");
            return false;
        }
        if (handle.request.isExpired(System.nanoTime())) {
            finishFromProcess(handle, NavigationState.FAILED, "Navigation deadline exceeded");
            return false;
        }
        if (baritone.getPlayerContext()
            .world() == null
            || baritone.getPlayerContext()
                .player() == null) {
            finishFromProcess(handle, NavigationState.FAILED, "Client world or player is unavailable");
            return false;
        }
        if (baritone.getPlayerContext()
            .world().provider.dimensionId != handle.request.getDimensionId()) {
            finishFromProcess(handle, NavigationState.FAILED, "Dimension changed during navigation");
            return false;
        }
        if (actionSessionGuard.getBlockedActionCount() > 0L) {
            finishFromProcess(
                handle,
                NavigationState.FAILED,
                "Safety firewall blocked " + actionSessionGuard.getLastBlockedAction());
            return false;
        }
        return true;
    }

    synchronized void markMoving(Handle handle, String detail) {
        if (active == handle && !handle.isTerminal()) {
            handle.state = NavigationState.MOVING;
            handle.detail = detail;
            DevelopmentTrace.event(
                "navigation",
                "moving",
                "request",
                handle.request.getRequestId(),
                "detail",
                detail,
                "player",
                baritone.getPlayerContext()
                    .playerFeet());
        }
    }

    void finishFromProcess(Handle handle, NavigationState state, String detail) {
        markTerminal(handle, state, detail);
    }

    void lostControl(Handle handle) {
        markTerminal(handle, NavigationState.FAILED, "Baritone process lost pathing control");
    }

    private void cancelExternally(Handle handle, String detail) {
        markTerminal(handle, NavigationState.CANCELLED, detail);
    }

    private void cancelRevokedEpoch(ActionRevocation revocation) {
        Handle handle;
        synchronized (this) {
            handle = active;
        }
        if (handle != null && handle.request.getActionEpoch() == revocation.getRevokedEpoch()) {
            cancelExternally(handle, "Action epoch revoked: " + revocation.getReason());
        }
    }

    private boolean markTerminal(Handle handle, NavigationState state, String detail) {
        synchronized (this) {
            if (active != handle || handle.isTerminal()) {
                return false;
            }
            actionSessionGuard.quarantine(handle.movementLease);
            handle.state = state;
            handle.detail = detail;
            active = null;
            process.deactivate(handle);
            pendingCleanup = new PendingCleanup(handle);
            DevelopmentTrace.event(
                "navigation",
                "terminal",
                "request",
                handle.request.getRequestId(),
                "state",
                state,
                "detail",
                detail,
                "blockedActions",
                actionSessionGuard.getBlockedActionCount());
            return true;
        }
    }

    private void cleanupTerminal(PendingCleanup cleanup) {
        requireClientThread();
        Handle handle = cleanup.handle;
        boolean stopped = false;
        RuntimeException cleanupFailure = null;
        try {
            boolean cancelled = baritone.getPathingBehavior()
                .cancelEverything();
            if (!cancelled || baritone.getPathingBehavior()
                .isPathing()
                || baritone.getPathingBehavior()
                    .hasPath()
                || baritone.getPathingBehavior()
                    .getInProgress()
                    .isPresent()) {
                baritone.getPathingBehavior()
                    .forceCancel();
            }
            clearInputs();
            stopped = !baritone.getPathingBehavior()
                .isPathing()
                && !baritone.getPathingBehavior()
                    .hasPath()
                && !baritone.getPathingBehavior()
                    .getInProgress()
                    .isPresent();
        } catch (RuntimeException failure) {
            cleanupFailure = failure;
            HorizonwrightMod.LOG.error("Failed to stop Baritone navigation", failure);
        } finally {
            try {
                clearInputs();
            } catch (RuntimeException inputFailure) {
                if (cleanupFailure == null) {
                    cleanupFailure = inputFailure;
                } else {
                    cleanupFailure.addSuppressed(inputFailure);
                }
            }
        }
        if (stopped && cleanupFailure == null) {
            actionSessionGuard.end(handle.movementLease);
            clearPendingCleanup(cleanup);
            return;
        }
        String detail = cleanupFailure == null ? "Baritone still owns a path after force cancellation"
            : "Baritone cleanup failed: " + cleanupFailure.getMessage();
        boolean exhausted;
        synchronized (this) {
            if (pendingCleanup != cleanup) {
                return;
            }
            exhausted = cleanup.retryBudget.recordFailureAndIsExhausted();
            detail = "Baritone cleanup attempt " + cleanup.retryBudget
                .getFailureCount() + "/" + MAX_CLEANUP_ATTEMPTS + " failed: " + detail;
            availability = BackendAvailability.unavailable(detail);
        }
        handle.detail = detail;
        if (!exhausted) {
            HorizonwrightMod.LOG.warn("{}; retrying on the next client tick", detail);
            return;
        }

        HorizonwrightMod.LOG.error(
            "{}; Horizonwright navigation remains disabled and the player packet quarantine will be released",
            detail,
            cleanupFailure);
        actionSessionGuard.end(handle.movementLease);
        clearPendingCleanup(cleanup);
    }

    private synchronized void clearPendingCleanup(PendingCleanup cleanup) {
        if (pendingCleanup == cleanup) {
            pendingCleanup = null;
        }
    }

    private void clearInputs() {
        try {
            baritone.getInputOverrideHandler()
                .clearAllKeys();
        } catch (RuntimeException failure) {
            availability = BackendAvailability.unavailable("Baritone input release failed: " + failure.getMessage());
            HorizonwrightMod.LOG.error("Failed to release Baritone inputs", failure);
            throw failure;
        }
    }

    PathingCommand navigationCommand(Handle handle, PathingCommandType commandType) {
        return new PathingCommandContext(handle.goal, commandType, handle.calculationContext);
    }

    private CalculationContext createMovementContext(boolean allowPlacement, List<String> allowedBreakBlockIds) {
        Settings settings = BaritoneAPI.getSettings();
        synchronized (settings) {
            boolean allowBreak = settings.allowBreak.value;
            List<Block> allowBreakAnyway = settings.allowBreakAnyway.value;
            boolean allowPlace = settings.allowPlace.value;
            boolean allowInventory = settings.allowInventory.value;
            boolean allowParkour = settings.allowParkour.value;
            boolean allowParkourPlace = settings.allowParkourPlace.value;
            boolean allowWaterBucketFall = settings.allowWaterBucketFall.value;
            try {
                settings.allowBreak.value = false;
                settings.allowBreakAnyway.value = resolveAllowedBreakBlocks(allowedBreakBlockIds);
                settings.allowPlace.value = allowPlacement;
                settings.allowInventory.value = false;
                settings.allowParkour.value = false;
                settings.allowParkourPlace.value = allowPlacement;
                settings.allowWaterBucketFall.value = false;
                return new HorizonwrightCalculationContext(baritone, cropsNhTravel);
            } finally {
                settings.allowBreak.value = allowBreak;
                settings.allowBreakAnyway.value = allowBreakAnyway;
                settings.allowPlace.value = allowPlace;
                settings.allowInventory.value = allowInventory;
                settings.allowParkour.value = allowParkour;
                settings.allowParkourPlace.value = allowParkourPlace;
                settings.allowWaterBucketFall.value = allowWaterBucketFall;
            }
        }
    }

    private static List<Block> resolveAllowedBreakBlocks(List<String> blockIds) {
        if (blockIds.isEmpty()) return Collections.emptyList();
        List<Block> blocks = new ArrayList<>(blockIds.size());
        for (String blockId : blockIds) {
            Object registered = Block.blockRegistry.getObject(blockId);
            if (!(registered instanceof Block)) {
                throw new IllegalArgumentException("navigation allowed-break block is not registered: " + blockId);
            }
            Block block = (Block) registered;
            if (!blocks.contains(block)) blocks.add(block);
        }
        return blocks;
    }

    private void requireRequestWorld(NavigationRequest request) {
        if (baritone.getPlayerContext()
            .world() == null
            || baritone.getPlayerContext()
                .player() == null) {
            throw new IllegalStateException("A joined client world is required for navigation");
        }
        int currentDimension = baritone.getPlayerContext()
            .world().provider.dimensionId;
        if (currentDimension != request.getDimensionId()) {
            throw new IllegalArgumentException(
                "request dimension " + request.getDimensionId()
                    + " differs from current dimension "
                    + currentDimension);
        }
    }

    private static void requireClientThread() {
        if (!Minecraft.getMinecraft()
            .func_152345_ab()) {
            throw new IllegalStateException("Baritone navigation must run on the Minecraft client thread");
        }
    }

    static final class Handle implements NavigationHandle {

        private final BaritoneNavigationBackend backend;
        private final NavigationRequest request;
        private final ActionLease movementLease;
        final Goal goal;
        private final CalculationContext calculationContext;
        private volatile NavigationState state = NavigationState.SUBMITTED;
        private volatile String detail = "Request accepted; waiting for Baritone control";

        private Handle(BaritoneNavigationBackend backend, NavigationRequest request, ActionLease movementLease,
            Goal goal, CalculationContext calculationContext) {
            this.backend = backend;
            this.request = request;
            this.movementLease = movementLease;
            this.goal = goal;
            this.calculationContext = calculationContext;
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public NavigationProgress progress() {
            return new NavigationProgress(request.getRequestId(), request.getActionEpoch(), state, detail);
        }

        @Override
        public void cancel() {
            backend.cancelExternally(this, "Navigation cancelled");
        }

        boolean isTerminal() {
            return state == NavigationState.COMPLETED || state == NavigationState.CANCELLED
                || state == NavigationState.FAILED;
        }
    }

    static final class CleanupRetryBudget {

        private final int maximumAttempts;
        private int failureCount;

        CleanupRetryBudget(int maximumAttempts) {
            if (maximumAttempts < 1) {
                throw new IllegalArgumentException("maximumAttempts must be positive");
            }
            this.maximumAttempts = maximumAttempts;
        }

        boolean recordFailureAndIsExhausted() {
            if (failureCount < maximumAttempts) {
                failureCount++;
            }
            return failureCount >= maximumAttempts;
        }

        int getFailureCount() {
            return failureCount;
        }
    }

    private static final class PendingCleanup {

        private final Handle handle;
        private final CleanupRetryBudget retryBudget = new CleanupRetryBudget(MAX_CLEANUP_ATTEMPTS);

        private PendingCleanup(Handle handle) {
            this.handle = handle;
        }
    }
}
