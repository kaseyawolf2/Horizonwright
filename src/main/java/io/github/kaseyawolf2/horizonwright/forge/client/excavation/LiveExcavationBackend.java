package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

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
        .of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.DIG);
    private static final int APPROACH_TOLERANCE = 3;
    private static final long ACTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(20L);

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
            return ExcavationBackendAvailability.unavailable("No navigation backend is configured for excavation");
        }
        BackendAvailability available = navigation.availability();
        return available.isAvailable()
            ? ExcavationBackendAvailability
                .available("Exact vanilla block excavation ready through " + available.getDiagnostic())
            : ExcavationBackendAvailability
                .unavailable("Excavation navigation unavailable: " + available.getDiagnostic());
    }

    @Override
    public ExcavationObservationResult observe(ExcavationObservationRequest request) {
        ExcavationObservation observation = observer.observe(request);
        ExcavationServiceRequirements requirements = request.getServiceRequirements();
        RepairToolSnapshot tool = requirements.isRepairConfigured()
            && observation.getClassification() == ExcavationBlockClassification.BREAKABLE
                ? toolReader.read(
                    minecraft.thePlayer.inventory.getStackInSlot(requirements.getReservedToolSlot()),
                    requirements.getReservedToolSlot())
                : null;
        return new ExcavationObservationResult(
            request.getTaskRevision(),
            request.getActionEpoch(),
            request.getGeometryKey(),
            request.getStartFrontier(),
            observation,
            serviceTriggers.evaluate(observation.getClassification(), requirements, emptyMainInventorySlots(), tool));
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
        if (immediate != null) return new ImmediateHandle(request, immediate);
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
        private final long deadlineNanos;
        private NavigationHandle navigationHandle;
        private Phase phase = Phase.APPROACHING;
        private ExcavationActionState state = ExcavationActionState.SUBMITTED;
        private String detail = "Preparing exact-target approach";
        private ConfirmedExcavationTargetResult confirmed;
        private boolean ownsDigSession;
        private volatile boolean cancellationRequested;

        private LiveHandle(ExcavationActionRequest request, ActionLease lease, NavigationBackend navigation,
            long startedAtNanos) {
            this.request = request;
            this.lease = lease;
            this.navigation = navigation;
            this.deadlineNanos = saturatingAdd(startedAtNanos, ACTION_TIMEOUT_NANOS);
        }

        private void start() {
            if (canReachTarget()) {
                phase = Phase.WAITING_FOR_DIG_SESSION;
                state = ExcavationActionState.EXECUTING;
                detail = "Target is within confirmed reach";
                return;
            }
            BlockPosition position = request.getIntent()
                .getPosition();
            NavigationRequest approach = new NavigationRequest(
                request.getRequestId() + "-approach",
                request.getActionEpoch(),
                request.getDimensionId(),
                position.getX(),
                position.getY(),
                position.getZ(),
                APPROACH_TOLERANCE,
                System.nanoTime(),
                ACTION_TIMEOUT_NANOS);
            navigationHandle = navigation.submit(approach, lease);
            state = ExcavationActionState.EXECUTING;
            detail = "Approaching exact excavation target";
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public synchronized ExcavationActionProgress progress() {
            requireClientThread();
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
            NavigationProgress progress = navigationHandle.progress();
            if (progress.getState() == NavigationState.COMPLETED) {
                navigationHandle = null;
                phase = Phase.WAITING_FOR_DIG_SESSION;
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
                return;
            }
            ExcavationObservation current = currentObservation();
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
            phase = Phase.DIGGING;
            detail = "Digging one fingerprint-bound block";
            aimAtTarget();
            BlockPosition position = request.getIntent()
                .getPosition();
            minecraft.playerController.clickBlock(position.getX(), position.getY(), position.getZ(), targetSide());
        }

        private synchronized void digOneTick() {
            if (!guard.isActiveLease(lease)) {
                stopDigSession();
                fail("Exact digging session lost packet authority");
                return;
            }
            ExcavationObservation current = currentObservation();
            if (isAir(current)) {
                stopDigSession();
                confirm(ExcavationTargetOutcome.COMPLETED, "Exact target is confirmed air");
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
            minecraft.playerController.resetBlockRemoving();
            guard.quarantine(lease);
            guard.end(lease);
            ownsDigSession = false;
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
            clearActive(this);
        }

        private void fail(String failureDetail) {
            state = ExcavationActionState.FAILED;
            detail = failureDetail;
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
            if (minecraft.thePlayer == null || minecraft.theWorld == null
                || minecraft.playerController == null
                || minecraft.theWorld.provider == null
                || minecraft.theWorld.provider.dimensionId != request.getDimensionId()) return false;
            BlockPosition position = request.getIntent()
                .getPosition();
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
            Vec3 center = Vec3
                .createVectorHelper(position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D);
            double reach = minecraft.playerController.getBlockReachDistance() + 0.5D;
            if (eyes.squareDistanceTo(center) > reach * reach) return false;
            MovingObjectPosition hit = minecraft.theWorld.rayTraceBlocks(eyes, center, false);
            return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.blockX == position.getX()
                && hit.blockY == position.getY()
                && hit.blockZ == position.getZ();
        }

        private void aimAtTarget() {
            EntityPlayer player = minecraft.thePlayer;
            BlockPosition position = request.getIntent()
                .getPosition();
            double dx = position.getX() + 0.5D - player.posX;
            double dy = position.getY() + 0.5D - (player.posY + player.getEyeHeight());
            double dz = position.getZ() + 0.5D - player.posZ;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            player.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            player.rotationPitch = (float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI);
        }

        private int targetSide() {
            EntityPlayer player = minecraft.thePlayer;
            BlockPosition position = request.getIntent()
                .getPosition();
            double dx = player.posX - (position.getX() + 0.5D);
            double dy = player.posY + player.getEyeHeight() - (position.getY() + 0.5D);
            double dz = player.posZ - (position.getZ() + 0.5D);
            double ax = Math.abs(dx);
            double ay = Math.abs(dy);
            double az = Math.abs(dz);
            if (ay >= ax && ay >= az) return dy > 0.0D ? 1 : 0;
            if (ax >= az) return dx > 0.0D ? 5 : 4;
            return dz > 0.0D ? 3 : 2;
        }

        private ExcavationActionProgress snapshot() {
            return new ExcavationActionProgress(request.getRequestId(), state, detail, confirmed);
        }

        private boolean isTerminal() {
            return state == ExcavationActionState.CONFIRMED || state == ExcavationActionState.CANCELLED
                || state == ExcavationActionState.FAILED;
        }
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
        DIGGING
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }
}
