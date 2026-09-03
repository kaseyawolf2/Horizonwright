package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
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
import io.github.kaseyawolf2.horizonwright.forge.client.AutomationInputHold;
import io.github.kaseyawolf2.horizonwright.forge.client.ClientBootstrap;
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
            ? Availability.available("Exact vanilla farm actions ready through " + status.getDiagnostic())
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
        DevelopmentTrace.event(
            "farm-live",
            "execute-validated",
            "request",
            request.getRequestId(),
            "seedSlot",
            seedSlot,
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

    private final class LiveHandle implements ActionHandle {

        private final ActionRequest request;
        private final ActionLease lease;
        private final NavigationBackend navigation;
        private final AutomationInputHold attackInput;
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
            this.attackInput = new AutomationInputHold("farm:" + request.getRequestId(), new AttackBinding());
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
            ClientBootstrap.blockDamageShield()
                .acquire(request.getRequestId());
            phase = Phase.BREAKING;
            detail = "Breaking one exact mature crop";
            aimAt(
                request.getDecision()
                    .getTarget());
            attackInput.hold();
            BasePosition target = request.getDecision()
                .getTarget();
            minecraft.playerController.clickBlock(target.getX(), target.getY(), target.getZ(), targetSide());
            trace("break-start", "target", target);
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
            attackInput.hold();
            boolean directProgress = minecraft.currentScreen != null || !minecraft.inGameHasFocus;
            if (directProgress) {
                minecraft.playerController
                    .onPlayerDamageBlock(target.getX(), target.getY(), target.getZ(), targetSide());
            }
            trace(
                "break-tick",
                "progressDriver",
                directProgress ? "horizonwright" : "vanilla-held-input",
                "screen",
                minecraft.currentScreen == null ? "none"
                    : minecraft.currentScreen.getClass()
                        .getSimpleName(),
                "inGameFocus",
                minecraft.inGameHasFocus);
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
                || minecraft.theWorld.provider.dimensionId != target.getDimensionId()) {
                trace("reach", "reachable", false, "reason", "client-or-dimension-unavailable");
                return false;
            }
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
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
            MovingObjectPosition hit = minecraft.theWorld.rayTraceBlocks(eyes, center, false);
            boolean reachable = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.blockX == target.getX()
                && hit.blockY == target.getY()
                && hit.blockZ == target.getZ();
            trace(
                "reach",
                "reachable",
                reachable,
                "reason",
                reachable ? "target-hit" : "raytrace-mismatch",
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
            attackInput.release();
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

        private final class AttackBinding implements AutomationInputHold.Binding {

            @Override
            public boolean isPressed() {
                return minecraft.gameSettings.keyBindAttack.getIsKeyPressed();
            }

            @Override
            public void setPressed(boolean pressed) {
                KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindAttack.getKeyCode(), pressed);
            }
        }

        private ActionProgress snapshot() {
            return new ActionProgress(request.getRequestId(), state, detail, confirmedAfter);
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
        CONFIRMING
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }
}
