package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
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
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmBackend;

/** Exact vanilla crop approach, break, hotbar replant, and immutable postcondition backend. */
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
    private static final int APPROACH_TOLERANCE = 3;
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
        if (navigation == null) return Availability.unavailable("No navigation backend is configured for farming");
        BackendAvailability status = navigation.availability();
        return status.isAvailable()
            ? Availability.available("Exact vanilla farm actions ready through " + status.getDiagnostic())
            : Availability.unavailable("Farm navigation unavailable: " + status.getDiagnostic());
    }

    @Override
    public PassSnapshot scan(ScanRequest request) {
        requireClient(request);
        NamedArea plot = observer.resolvePlot(request.getPlotId());
        List<CropObservation> observations = observer.scan(plot);
        return new PassSnapshot(request.getTaskId(), request.getActionEpoch(), plot, observations);
    }

    @Override
    public TargetSnapshot observe(TargetRequest request) {
        requireClient(request);
        CropObservation crop = observer.observeRequired(request.getPosition());
        SeedReserveEvidence reserve = observer
            .reserve(request.getPassRevision(), crop.getRequiredSeedFingerprint(), request.getMinimumSeedReserve());
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
        if (request.getDecision()
            .getAction() != FarmActionKind.BREAK_AND_REPLANT) {
            throw new IllegalArgumentException("live vanilla backend supports only break-and-replant mutations");
        }
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || !lease.getCapabilities()
                .containsAll(BREAK_REPLANT)) {
            throw new IllegalArgumentException("matching movement/look/dig/place/held-use authority is required");
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
        if (!request.getDecision()
            .getReserveEvidence()
            .isSameSnapshot(reserve)) {
            throw new IllegalStateException("seed inventory changed after planning");
        }
        int seedSlot = observer.findHotbarSeed(
            request.getDecision()
                .getRequiredSeedFingerprint());
        if (seedSlot < 0)
            throw new IllegalStateException("an exact approved replant seed must be present in the hotbar");
        verifier.requireCurrent(
            request.getDecision(),
            current,
            reserve,
            request.getDecision()
                .getRequiredSeedFingerprint());
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        Availability available = availability();
        if (navigation == null || !available.isAvailable()) throw new IllegalStateException(available.getDiagnostic());
        LiveHandle handle = new LiveHandle(request, lease, navigation, seedSlot, current, System.nanoTime());
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

    private final class LiveHandle implements ActionHandle {

        private final ActionRequest request;
        private final ActionLease lease;
        private final NavigationBackend navigation;
        private final int seedSlot;
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
        private boolean slotChanged;
        private volatile boolean cancellationRequested;

        private LiveHandle(ActionRequest request, ActionLease lease, NavigationBackend navigation, int seedSlot,
            CropObservation plannedBefore, long startedAtNanos) {
            this.request = request;
            this.lease = lease;
            this.navigation = navigation;
            this.seedSlot = seedSlot;
            this.plannedBefore = plannedBefore;
            this.priorHotbarSlot = minecraft.thePlayer.inventory.currentItem;
            this.deadlineNanos = saturatingAdd(startedAtNanos, ACTION_TIMEOUT_NANOS);
        }

        private void start() {
            if (canReachCrop()) {
                phase = Phase.WAITING_FOR_ACTION_SESSION;
                state = ActionState.EXECUTING;
                detail = "Crop is within confirmed reach";
                return;
            }
            BasePosition target = request.getDecision()
                .getTarget();
            navigationHandle = navigation.submit(
                new NavigationRequest(
                    request.getRequestId() + "-approach",
                    request.getActionEpoch(),
                    target.getDimensionId(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    APPROACH_TOLERANCE,
                    System.nanoTime(),
                    ACTION_TIMEOUT_NANOS),
                lease);
            state = ActionState.EXECUTING;
            detail = "Approaching exact farm target";
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public synchronized ActionProgress progress() {
            requireClient(request);
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
            else if (phase == Phase.CONFIRMING) confirmReplacement();
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
                return;
            }
            CropObservation current = observer.observeSupported(
                request.getDecision()
                    .getTarget());
            if (!samePlannedCrop(current) || !current.isMature() || !canReachCrop()) {
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
            if (observer.findHotbarSeed(
                request.getDecision()
                    .getRequiredSeedFingerprint())
                != seedSlot) {
                fail("Seed inventory changed after approach");
                return;
            }
            try {
                verifier.requireCurrent(
                    request.getDecision(),
                    current,
                    reserve,
                    request.getDecision()
                        .getRequiredSeedFingerprint());
            } catch (RuntimeException changed) {
                fail(changed.getMessage());
                return;
            }
            guard.begin(lease);
            ownsActionSession = true;
            phase = Phase.BREAKING;
            detail = "Breaking one exact mature crop";
            aimAt(
                request.getDecision()
                    .getTarget());
            BasePosition target = request.getDecision()
                .getTarget();
            minecraft.playerController.clickBlock(target.getX(), target.getY(), target.getZ(), targetSide());
        }

        private void breakOneTick() {
            if (!guard.isActiveLease(lease)) {
                fail("Farm digging session lost packet authority");
                return;
            }
            BasePosition target = request.getDecision()
                .getTarget();
            CropObservation current = observer.observeSupported(target);
            if (current == null) {
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
            minecraft.thePlayer.swingItem();
        }

        private void plantOnce() {
            if (!guard.isActiveLease(lease) || !minecraft.theWorld.isAirBlock(
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

        private void confirmReplacement() {
            CropObservation after = observer.observeSupported(
                request.getDecision()
                    .getTarget());
            if (after == null) {
                detail = "Waiting for replacement crop synchronization";
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
            state = ActionState.CONFIRMED;
            detail = "Exact replacement crop is confirmed immature";
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
                || minecraft.theWorld.provider.dimensionId != target.getDimensionId()) return false;
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
            Vec3 center = Vec3.createVectorHelper(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            double reach = minecraft.playerController.getBlockReachDistance() + 0.5D;
            if (eyes.squareDistanceTo(center) > reach * reach) return false;
            MovingObjectPosition hit = minecraft.theWorld.rayTraceBlocks(eyes, center, false);
            return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.blockX == target.getX()
                && hit.blockY == target.getY()
                && hit.blockZ == target.getZ();
        }

        private void aimAt(BasePosition target) {
            EntityPlayer player = minecraft.thePlayer;
            double dx = target.getX() + 0.5D - player.posX;
            double dy = target.getY() + 0.5D - (player.posY + player.getEyeHeight());
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

        private void stopActionSession() {
            restoreSlot();
            minecraft.playerController.resetBlockRemoving();
            if (ownsActionSession) {
                guard.quarantine(lease);
                guard.end(lease);
                ownsActionSession = false;
            }
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
            clearActive(this);
        }

        private ActionProgress snapshot() {
            return new ActionProgress(request.getRequestId(), state, detail, confirmedAfter);
        }

        private boolean isTerminal() {
            return state == ActionState.CONFIRMED || state == ActionState.CANCELLED || state == ActionState.FAILED;
        }
    }

    private enum Phase {
        APPROACHING,
        WAITING_FOR_ACTION_SESSION,
        BREAKING,
        PLANTING,
        DISPATCHING_REPLANT,
        CONFIRMING
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }
}
