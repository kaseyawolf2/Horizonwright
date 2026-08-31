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
import io.github.kaseyawolf2.horizonwright.runtime.task.SleepBackend;

/** Conservative registered-vanilla-bed interaction with fresh danger and block evidence. */
public final class LiveVanillaSleepBackend implements SleepBackend {

    public interface NavigationSource {

        NavigationBackend getNavigationBackend();
    }

    private static final EnumSet<ActionCapability> REQUIRED = EnumSet
        .of(ActionCapability.MOVEMENT, ActionCapability.LOOK, ActionCapability.USE);
    private static final int APPROACH_TOLERANCE = 2;
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
        if (navigation == null) return Availability.unavailable("No navigation backend is configured for sleep");
        BackendAvailability status = navigation.availability();
        return status.isAvailable()
            ? Availability.available("Registered vanilla-bed sleep ready through " + status.getDiagnostic())
            : Availability.unavailable("Sleep navigation unavailable: " + status.getDiagnostic());
    }

    @Override
    public synchronized ObservationSnapshot observe(ObservationRequest request) {
        requireClient(request);
        NamedLocation location = configuration.resolveBed(request.getBedLocationId());
        SleepObservation observation = observeBed(location);
        return new ObservationSnapshot(
            request.getTaskId(),
            request.getBedLocationId(),
            request.getActionEpoch(),
            observation);
    }

    @Override
    public synchronized ActionHandle execute(ActionRequest request, ActionLease lease) {
        requireClient(request);
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
        return new SleepObservation(
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
        double reach = minecraft.playerController.getBlockReachDistance() + 0.5D;
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
                return;
            }
            SleepObservation fresh = observeBed(location);
            SleepDecision freshDecision = planner.plan(fresh);
            if (freshDecision.getAction() == SleepActionKind.SKIP_DAYTIME) {
                state = ActionState.CONFIRMED;
                detail = "Daytime was confirmed before bed interaction";
                clearActive(this);
                return;
            }
            boolean exactBed = minecraft.theWorld.blockExists(bed.getX(), bed.getY(), bed.getZ())
                && minecraft.theWorld.getBlock(bed.getX(), bed.getY(), bed.getZ()) == Blocks.bed;
            if (freshDecision.getAction() != SleepActionKind.USE_REGISTERED_BED || !exactBed || !canReach(bed)) {
                fail("Registered bed is no longer safe, present, and reachable");
                return;
            }
            guard.begin(lease);
            ownsActionSession = true;
            aimAt(bed);
            boolean accepted = minecraft.playerController.onPlayerRightClick(
                minecraft.thePlayer,
                minecraft.theWorld,
                minecraft.thePlayer.getHeldItem(),
                bed.getX(),
                bed.getY(),
                bed.getZ(),
                1,
                Vec3.createVectorHelper(bed.getX() + 0.5D, bed.getY() + 0.5D, bed.getZ() + 0.5D));
            minecraft.thePlayer.swingItem();
            stopActionSession();
            if (!accepted) {
                fail("Minecraft rejected the registered bed interaction");
                return;
            }
            phase = Phase.CONFIRMING;
            detail = "Waiting for server-confirmed sleeping or daytime";
        }

        private void confirmSleep() {
            if (minecraft.thePlayer.isPlayerSleeping() || !SleepWindow.vanilla()
                .contains(minecraft.theWorld.getWorldTime())) {
                state = ActionState.CONFIRMED;
                detail = minecraft.thePlayer.isPlayerSleeping() ? "Player sleeping state confirmed"
                    : "Daytime confirmed after sleep";
                clearActive(this);
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
            clearActive(this);
        }

        private ActionProgress snapshot() {
            return new ActionProgress(request.getRequestId(), state, detail);
        }

        private boolean isTerminal() {
            return state == ActionState.CONFIRMED || state == ActionState.CANCELLED || state == ActionState.FAILED;
        }
    }

    private enum Phase {
        APPROACHING,
        WAITING_FOR_ACTION_SESSION,
        CONFIRMING
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }
}
