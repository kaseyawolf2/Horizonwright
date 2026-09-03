package io.github.kaseyawolf2.horizonwright.forge.client.sleep;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.SleepActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.SleepDecision;
import io.github.kaseyawolf2.horizonwright.core.base.SleepObservation;
import io.github.kaseyawolf2.horizonwright.core.base.SleepPlanner;
import io.github.kaseyawolf2.horizonwright.core.base.SleepProviderKind;
import io.github.kaseyawolf2.horizonwright.core.base.SleepWindow;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
import io.github.kaseyawolf2.horizonwright.runtime.task.SleepBackend;

/** Conservative registered-vanilla-bed interaction with fresh danger and block evidence. */
public final class LiveVanillaSleepBackend implements SleepBackend {

    private static final int REQUIRED_SLEEPING_TICKS = 5;

    public interface NavigationSource {

        NavigationBackend getNavigationBackend();
    }

    private static final EnumSet<ActionCapability> REQUIRED = EnumSet
        .of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.USE);
    private static final int APPROACH_TOLERANCE = 1;
    private static final double MAX_INTERACTION_DISTANCE = 3.25D;
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(45L);

    private final Minecraft minecraft;
    private final ActionSessionGuard guard;
    private final NavigationSource navigationSource;
    private final ProfileSleepConfiguration configuration;
    private final SleepPlanner planner = new SleepPlanner();
    private long observationRevision;
    private LiveHandle active;

    public LiveVanillaSleepBackend(Minecraft minecraft, ActionSessionGuard guard, NavigationSource navigationSource,
        ProfileSleepConfiguration configuration) {
        if (minecraft == null || guard == null || navigationSource == null || configuration == null) {
            throw new IllegalArgumentException("complete live sleep dependencies are required");
        }
        this.minecraft = minecraft;
        this.guard = guard;
        this.navigationSource = navigationSource;
        this.configuration = configuration;
    }

    @Override
    public Availability availability() {
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        if (navigation == null) {
            DevelopmentTrace.event("sleep-live", "availability", "available", false, "reason", "no-navigation-backend");
            return Availability.unavailable("No navigation backend is configured for sleep");
        }
        BackendAvailability status = navigation.availability();
        Availability result = status.isAvailable()
            ? Availability.available("Registered vanilla-bed sleep ready through " + status.getDiagnostic())
            : Availability.unavailable("Sleep navigation unavailable: " + status.getDiagnostic());
        DevelopmentTrace.event(
            "sleep-live",
            "availability",
            "available",
            result.isAvailable(),
            "diagnostic",
            result.getDiagnostic());
        return result;
    }

    @Override
    public synchronized ObservationSnapshot observe(ObservationRequest request) {
        requireClient(request);
        NamedLocation location = configuration.resolveBed(request.getBedLocationId());
        SleepObservation observation = observeBed(location);
        traceObservation("observed", request.getTaskId(), request.getBedLocationId(), observation);
        return new ObservationSnapshot(
            request.getTaskId(),
            request.getBedLocationId(),
            request.getActionEpoch(),
            observation);
    }

    @Override
    public synchronized ActionHandle execute(ActionRequest request, ActionLease lease) {
        requireClient(request);
        DevelopmentTrace.event(
            "sleep-live",
            "execute-request",
            "request",
            request.getRequestId(),
            "task",
            request.getTaskId(),
            "bed",
            request.getBedLocationId(),
            "epoch",
            request.getActionEpoch(),
            "action",
            request.getDecision()
                .getAction(),
            "leaseValid",
            lease != null && lease.isValid(),
            "capabilities",
            lease == null ? "none" : lease.getCapabilities());
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || !lease.getCapabilities()
                .containsAll(REQUIRED)) {
            throw new IllegalArgumentException("matching movement/look/use authority is required");
        }
        if (request.getDecision()
            .getAction() != SleepActionKind.USE_REGISTERED_BED) {
            throw new IllegalArgumentException("live sleep supports only registered vanilla beds");
        }
        if (active != null && !active.isTerminal()) throw new IllegalStateException("another sleep action is active");
        NamedLocation location = configuration.resolveBed(request.getBedLocationId());
        BasePosition plannedBed = request.getDecision()
            .getProvider() == SleepProviderKind.REGISTERED_BED ? request.getDecision()
                .getDecisionBed() : null;
        BasePosition configuredBed = new BasePosition(
            location.getDimensionId(),
            location.getX(),
            location.getY(),
            location.getZ());
        if (plannedBed != null && !plannedBed.equals(configuredBed)) {
            throw new IllegalStateException("registered bed location changed after planning");
        }
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        Availability available = availability();
        if (navigation == null || !available.isAvailable()) throw new IllegalStateException(available.getDiagnostic());
        LiveHandle handle = new LiveHandle(request, lease, navigation, location, System.nanoTime());
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

    private SleepObservation observeBed(NamedLocation location) {
        BasePosition bed = new BasePosition(
            location.getDimensionId(),
            location.getX(),
            location.getY(),
            location.getZ());
        boolean sameDimension = minecraft.theWorld.provider != null
            && minecraft.theWorld.provider.dimensionId == location.getDimensionId();
        boolean loaded = sameDimension
            && minecraft.theWorld.blockExists(location.getX(), location.getY(), location.getZ());
        boolean exactBed = loaded
            && minecraft.theWorld.getBlock(location.getX(), location.getY(), location.getZ()) == Blocks.bed;
        // An unloaded registered location may authorize only the approach. Exact block,
        // danger, and click reach are all revalidated after the approach completes.
        boolean providerAvailable = sameDimension && (!loaded || exactBed);
        boolean danger = exactBed && hasNearbyHostile(bed);
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        boolean reachable = providerAvailable && navigation != null
            && navigation.availability()
                .isAvailable();
        long time = Math.max(0L, minecraft.theWorld.getWorldTime());
        long revision = ++observationRevision;
        String fingerprint = location.getId() + ":"
            + location.getDimensionId()
            + ":"
            + location.getX()
            + ":"
            + location.getY()
            + ":"
            + location.getZ()
            + ":"
            + time
            + ":"
            + danger
            + ":"
            + providerAvailable
            + ":"
            + loaded
            + ":"
            + exactBed
            + ":"
            + reachable;
        SleepObservation observation = new SleepObservation(
            revision,
            fingerprint,
            minecraft.theWorld.provider.dimensionId,
            time,
            minecraft.theWorld.provider.canRespawnHere(),
            danger,
            SleepProviderKind.REGISTERED_BED,
            bed,
            providerAvailable,
            reachable);
        DevelopmentTrace.event(
            "sleep-live",
            "bed-scan",
            "bed",
            location.getId(),
            "revision",
            revision,
            "position",
            bed,
            "worldTime",
            time,
            "sameDimension",
            sameDimension,
            "loaded",
            loaded,
            "exactBed",
            exactBed,
            "danger",
            danger,
            "providerAvailable",
            providerAvailable,
            "navigationReachable",
            reachable);
        return observation;
    }

    private boolean hasNearbyHostile(BasePosition bed) {
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(bed.getX(), bed.getY(), bed.getZ(), bed.getX() + 1, bed.getY() + 1, bed.getZ() + 1)
            .expand(8.0D, 5.0D, 8.0D);
        @SuppressWarnings("unchecked")
        List<EntityMob> hostiles = minecraft.theWorld.getEntitiesWithinAABB(EntityMob.class, box);
        for (EntityMob hostile : hostiles) if (hostile != null && !hostile.isDead) return true;
        return false;
    }

    private boolean canReach(BasePosition bed) {
        EntityPlayer player = minecraft.thePlayer;
        Vec3 eyes = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 center = Vec3.createVectorHelper(bed.getX() + 0.5D, bed.getY() + 0.5D, bed.getZ() + 0.5D);
        double reach = Math.min(minecraft.playerController.getBlockReachDistance(), MAX_INTERACTION_DISTANCE);
        if (eyes.squareDistanceTo(center) > reach * reach) return false;
        MovingObjectPosition hit = minecraft.theWorld.rayTraceBlocks(eyes, center, false);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == bed.getX()
            && hit.blockY == bed.getY()
            && hit.blockZ == bed.getZ();
    }

    private void requireClient(Object request) {
        if (request == null) throw new IllegalArgumentException("sleep request is required");
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.playerController == null
            || minecraft.theWorld.provider == null) {
            throw new IllegalStateException("live sleep requires a joined Minecraft client thread");
        }
    }

    private synchronized void clearActive(LiveHandle handle) {
        if (active == handle) active = null;
    }

    private final class LiveHandle implements ActionHandle {

        private final ActionRequest request;
        private final ActionLease lease;
        private final NavigationBackend navigation;
        private final NamedLocation location;
        private final BasePosition bed;
        private final long deadlineNanos;
        private NavigationHandle navigationHandle;
        private Phase phase = Phase.APPROACHING;
        private ActionState state = ActionState.SUBMITTED;
        private String detail = "Preparing registered bed approach";
        private boolean ownsActionSession;
        private boolean interactionDispatched;
        private int consecutiveSleepingTicks;
        private volatile boolean cancellationRequested;

        private LiveHandle(ActionRequest request, ActionLease lease, NavigationBackend navigation,
            NamedLocation location, long startedAtNanos) {
            this.request = request;
            this.lease = lease;
            this.navigation = navigation;
            this.location = location;
            this.bed = new BasePosition(location.getDimensionId(), location.getX(), location.getY(), location.getZ());
            this.deadlineNanos = saturatingAdd(startedAtNanos, TIMEOUT_NANOS);
        }

        private void start() {
            if (canReach(bed)) {
                phase = Phase.WAITING_FOR_ACTION_SESSION;
                state = ActionState.EXECUTING;
                detail = "Registered bed is within confirmed reach";
                trace("phase", "reach", true);
                return;
            }
            navigationHandle = navigation.submit(
                new NavigationRequest(
                    request.getRequestId() + "-approach",
                    request.getActionEpoch(),
                    bed.getDimensionId(),
                    bed.getX(),
                    bed.getY(),
                    bed.getZ(),
                    APPROACH_TOLERANCE,
                    System.nanoTime(),
                    TIMEOUT_NANOS),
                lease);
            state = ActionState.EXECUTING;
            detail = "Approaching registered bed";
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
                "worldTime",
                minecraft.theWorld.getWorldTime(),
                "sleeping",
                minecraft.thePlayer.isPlayerSleeping());
            if (isTerminal()) return snapshot();
            if (cancellationRequested) {
                cancelOnClientThread();
                return snapshot();
            }
            if (!lease.isValid()) {
                stopProducers();
                fail("Sleep action lease was revoked");
                return snapshot();
            }
            if (System.nanoTime() - deadlineNanos >= 0L) {
                stopProducers();
                fail("Sleep action deadline exceeded");
                return snapshot();
            }
            if (phase == Phase.APPROACHING) pollApproach();
            else if (phase == Phase.WAITING_FOR_ACTION_SESSION) interactWhenReady();
            else if (phase == Phase.DISPATCHING_INTERACTION) awaitInteractionDispatch();
            else if (phase == Phase.CONFIRMING) confirmSleep();
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
                detail = "Bed approach complete; waiting for packet drain";
            } else if (progress.getState() == NavigationState.FAILED)
                fail("Could not approach bed: " + progress.getDetail());
            else if (progress.getState() == NavigationState.CANCELLED) {
                state = ActionState.CANCELLED;
                detail = "Bed approach was cancelled";
                clearActive(this);
            } else detail = "Approaching bed: " + progress.getDetail();
        }

        private void interactWhenReady() {
            if (!guard.isReadyForSession()) {
                detail = "Waiting for approach packets to drain";
                trace("packet-drain", "blockedActions", guard.getBlockedActionCount());
                return;
            }
            SleepObservation fresh = observeBed(location);
            SleepDecision freshDecision = planner.plan(fresh);
            traceObservation("pre-interaction-observation", request.getTaskId(), request.getBedLocationId(), fresh);
            if (freshDecision.getAction() == SleepActionKind.SKIP_DAYTIME) {
                state = ActionState.CONFIRMED;
                detail = "Daytime was confirmed before bed interaction";
                clearActive(this);
                return;
            }
            boolean exactBed = minecraft.theWorld.blockExists(bed.getX(), bed.getY(), bed.getZ())
                && minecraft.theWorld.getBlock(bed.getX(), bed.getY(), bed.getZ()) == Blocks.bed;
            if (freshDecision.getAction() != SleepActionKind.USE_REGISTERED_BED || !exactBed || !canReach(bed)) {
                trace(
                    "pre-interaction-rejected",
                    "decision",
                    freshDecision.getAction(),
                    "exactBed",
                    exactBed,
                    "reach",
                    canReach(bed));
                fail("Registered bed is no longer safe, present, and reachable");
                return;
            }
            guard.begin(lease);
            ownsActionSession = true;
            MovingObjectPosition hit = rayTraceBed(bed);
            if (hit == null) {
                fail("Registered bed no longer has an exact interaction face");
                return;
            }
            aimAt(bed);
            boolean accepted = minecraft.playerController.onPlayerRightClick(
                minecraft.thePlayer,
                minecraft.theWorld,
                minecraft.thePlayer.getHeldItem(),
                bed.getX(),
                bed.getY(),
                bed.getZ(),
                hit.sideHit,
                hit.hitVec);
            trace("interaction", "accepted", accepted, "side", hit.sideHit, "hit", hit.hitVec);
            if (!accepted) {
                stopActionSession();
                fail("Minecraft rejected the registered bed interaction");
                return;
            }
            phase = Phase.DISPATCHING_INTERACTION;
            detail = "Dispatching the registered-bed interaction";
            try {
                ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                    synchronized (LiveHandle.this) {
                        stopActionSession();
                        interactionDispatched = true;
                    }
                });
            } catch (RuntimeException failure) {
                stopActionSession();
                fail("Could not dispatch the registered-bed interaction: " + failure.getMessage());
            }
        }

        private void awaitInteractionDispatch() {
            if (!interactionDispatched) {
                detail = "Waiting for the registered-bed packet boundary";
                return;
            }
            phase = Phase.CONFIRMING;
            detail = "Waiting for server-confirmed sleeping or daytime";
        }

        private MovingObjectPosition rayTraceBed(BasePosition target) {
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
            Vec3 center = Vec3.createVectorHelper(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            MovingObjectPosition hit = minecraft.theWorld.rayTraceBlocks(eyes, center, false);
            return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.blockX == target.getX()
                && hit.blockY == target.getY()
                && hit.blockZ == target.getZ() ? hit : null;
        }

        private void confirmSleep() {
            if (minecraft.thePlayer.isPlayerSleeping()) {
                consecutiveSleepingTicks++;
            } else {
                consecutiveSleepingTicks = 0;
            }
            boolean daytime = !SleepWindow.vanilla()
                .contains(minecraft.theWorld.getWorldTime());
            trace(
                "confirmation",
                "sleepingTicks",
                consecutiveSleepingTicks,
                "daytime",
                daytime,
                "worldTime",
                minecraft.theWorld.getWorldTime());
            if (consecutiveSleepingTicks >= REQUIRED_SLEEPING_TICKS || daytime) {
                state = ActionState.CONFIRMED;
                detail = consecutiveSleepingTicks >= REQUIRED_SLEEPING_TICKS ? "Stable player sleeping state confirmed"
                    : "Daytime confirmed after sleep";
                clearActive(this);
            } else {
                detail = consecutiveSleepingTicks == 0 ? "Waiting for server-confirmed sleeping or daytime"
                    : "Confirming stable sleeping state (" + consecutiveSleepingTicks
                        + "/"
                        + REQUIRED_SLEEPING_TICKS
                        + ")";
            }
        }

        private void aimAt(BasePosition target) {
            Entity player = minecraft.thePlayer;
            double dx = target.getX() + 0.5D - player.posX;
            double dy = target.getY() + 0.5D - (player.posY + minecraft.thePlayer.getEyeHeight());
            double dz = target.getZ() + 0.5D - player.posZ;
            player.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            player.rotationPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI);
        }

        private void stopActionSession() {
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
            detail = "Sleep cancelled before confirmation";
            clearActive(this);
        }

        private void fail(String message) {
            stopProducers();
            state = ActionState.FAILED;
            detail = message;
            trace("failed", "failure", message);
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
            fields[8] = "bed";
            fields[9] = bed;
            System.arraycopy(extraFields, 0, fields, 10, extraFields.length);
            DevelopmentTrace.event("sleep-live", event, fields);
        }

        private ActionProgress snapshot() {
            return new ActionProgress(request.getRequestId(), state, detail);
        }

        private boolean isTerminal() {
            return state == ActionState.CONFIRMED || state == ActionState.CANCELLED || state == ActionState.FAILED;
        }
    }

    private static void traceObservation(String event, String taskId, String bedId, SleepObservation observation) {
        DevelopmentTrace.event(
            "sleep-live",
            event,
            "task",
            taskId,
            "bedId",
            bedId,
            "revision",
            observation.getRevision(),
            "fingerprint",
            observation.getObservationFingerprint(),
            "dimension",
            observation.getCurrentDimension(),
            "worldTime",
            observation.getWorldTime(),
            "danger",
            observation.isDanger(),
            "sleepValidDimension",
            observation.isSleepValidDimension(),
            "provider",
            observation.getProvider(),
            "providerAvailable",
            observation.isProviderAvailable(),
            "reachable",
            observation.isLoadedAndReachable(),
            "position",
            observation.getRegisteredBed());
    }

    private enum Phase {
        APPROACHING,
        WAITING_FOR_ACTION_SESSION,
        DISPATCHING_INTERACTION,
        CONFIRMING
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }
}
