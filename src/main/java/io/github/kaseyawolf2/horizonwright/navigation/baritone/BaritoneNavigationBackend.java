package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.compat.BlockPos;
import baritone.pathing.movement.CalculationContext;
import baritone.utils.PathingCommandContext;
import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocation;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocationListener;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;

public final class BaritoneNavigationBackend implements NavigationBackend, ActionRevocationListener {

    static final int MAX_CLEANUP_ATTEMPTS = 3;

    private static final EnumSet<ActionCapability> REQUIRED_CAPABILITIES = EnumSet
        .of(ActionCapability.MOVEMENT, ActionCapability.LOOK);

    private final IBaritone baritone;
    private final ActionSessionGuard actionSessionGuard;
    private final HorizonwrightBaritoneProcess process;
    private volatile BackendAvailability availability = BackendAvailability
        .available("Baritone enhanced build fcbbd4882c ready (movement/look only)");
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
    }

    @Override
    public synchronized BackendAvailability availability() {
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
        if (!movementLease.getCapabilities()
            .containsAll(REQUIRED_CAPABILITIES)) {
            throw new IllegalArgumentException("Baritone navigation requires MOVEMENT and LOOK capabilities");
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

        Goal goal = request.getTolerance() == 0 ? new GoalBlock(request.getX(), request.getY(), request.getZ())
            : new GoalNear(new BlockPos(request.getX(), request.getY(), request.getZ()), request.getTolerance());
        CalculationContext movementOnlyContext = createMovementOnlyContext();
        Handle handle = new Handle(this, request, movementLease, goal, movementOnlyContext);
        actionSessionGuard.begin(movementLease);
        active = handle;
        process.activate(handle);
        return handle;
    }

    @Override
    public void onActionEpochRevoked(ActionRevocation revocation) {
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
        PendingCleanup cleanup;
        synchronized (this) {
            cleanup = pendingCleanup;
        }
        if (cleanup != null) {
            cleanupTerminal(cleanup);
        }
    }

    synchronized Handle activeHandle() {
        return active;
    }

    synchronized boolean validateActive(Handle handle) {
        if (active != handle || handle.isTerminal()) {
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

    PathingCommand movementOnlyCommand(Handle handle, PathingCommandType commandType) {
        return new PathingCommandContext(handle.goal, commandType, handle.movementOnlyContext);
    }

    private CalculationContext createMovementOnlyContext() {
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
                settings.allowBreakAnyway.value = Collections.emptyList();
                settings.allowPlace.value = false;
                settings.allowInventory.value = false;
                settings.allowParkour.value = false;
                settings.allowParkourPlace.value = false;
                settings.allowWaterBucketFall.value = false;
                return new CalculationContext(baritone, true);
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
        private final CalculationContext movementOnlyContext;
        private volatile NavigationState state = NavigationState.SUBMITTED;
        private volatile String detail = "Request accepted; waiting for Baritone control";

        private Handle(BaritoneNavigationBackend backend, NavigationRequest request, ActionLease movementLease,
            Goal goal, CalculationContext movementOnlyContext) {
            this.backend = backend;
            this.request = request;
            this.movementLease = movementLease;
            this.goal = goal;
            this.movementOnlyContext = movementOnlyContext;
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
