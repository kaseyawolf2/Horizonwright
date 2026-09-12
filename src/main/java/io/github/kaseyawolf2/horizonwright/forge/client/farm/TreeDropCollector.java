package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.AxisAlignedBB;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.navigation.ScaffoldCleanup;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeBackend;

/** Removes recorded supports, then rescans drops before any saplings are planted. */
final class TreeDropCollector implements TreeBackend.CollectionHandle {

    private final Minecraft minecraft;
    private final ActionSessionGuard guard;
    private final NavigationBackend navigation;
    private final String task;
    private final NamedArea area;
    private final ActionLease lease;
    private NavigationHandle moving;
    private EntityItem target;
    private ScaffoldCleanup scaffoldCleanup;
    private boolean scaffoldsCleared;
    private long deadline;
    private int emptySince = -1;
    private int arrivalTick = -1;
    private int sequence;
    private BasePosition trackedPosition;
    private int approachAttempt;
    private final java.util.Map<Integer, Integer> failedRounds = new java.util.HashMap<>();
    private boolean cancelled;
    private String detail = "Scanning tree-farm drops";

    TreeDropCollector(Minecraft minecraft, ActionSessionGuard guard, NavigationBackend navigation, String task,
        NamedArea area, ActionLease lease) {
        if (navigation == null || lease == null || !lease.isValid())
            throw new IllegalStateException("Collection needs live navigation authority");
        this.minecraft = minecraft;
        this.guard = guard;
        this.navigation = navigation;
        this.task = task;
        this.area = area;
        this.lease = lease;
    }

    @Override
    public boolean poll() {
        if (cancelled || !lease.isValid() || minecraft.thePlayer == null || minecraft.theWorld == null)
            throw new IllegalStateException("Tree collection interrupted");
        if (!scaffoldsCleared) {
            if (scaffoldCleanup == null) scaffoldCleanup = new ScaffoldCleanup(
                navigation,
                lease,
                task,
                area.getMinimum()
                    .getDimensionId());
            if (!scaffoldCleanup.poll()) {
                detail = "Removing remaining temporary tree pillars";
                return false;
            }
            scaffoldsCleared = true;
        }
        BasePosition min = area.getMinimum(), max = area.getMaximum();
        int pickupMargin = io.github.kaseyawolf2.horizonwright.core.base.TreeHarvestBoundary.OUTSIDE_REACH;
        if (minecraft.theWorld.provider.dimensionId != min.getDimensionId())
            throw new IllegalStateException("Tree collection dimension changed");
        for (int cx = min.getX() >> 4; cx <= (max.getX() >> 4); cx++)
            for (int cz = min.getZ() >> 4; cz <= (max.getZ() >> 4); cz++)
                if (!MinecraftRuntimeAccess.blockExists(minecraft.theWorld, cx << 4, min.getY(), cz << 4))
                    throw new IllegalStateException("Tree farm must remain completely loaded during collection");
        List<EntityItem> drops = MinecraftRuntimeAccess.getEntitiesWithinAabb(
            minecraft.theWorld,
            EntityItem.class,
            AxisAlignedBB.getBoundingBox(
                min.getX() - pickupMargin,
                0,
                min.getZ() - pickupMargin,
                max.getX() + pickupMargin + 1D,
                256,
                max.getZ() + pickupMargin + 1D));
        drops.removeIf(drop -> drop.isDead);
        int tick = minecraft.thePlayer.ticksExisted;
        DevelopmentTrace.event(
            "tree-collection",
            "scan",
            "task",
            task,
            "drops",
            drops.size(),
            "target",
            target == null ? "none" : target.getEntityId());
        if (target != null && !drops.contains(target)) {
            if (moving != null) moving.cancel();
            moving = null;
            target = null;
            arrivalTick = -1;
            trackedPosition = null;
            approachAttempt = 0;
        }
        if (drops.isEmpty()) {
            if (!guard.isReadyForSession()) {
                detail = "Waiting for collection navigation cleanup";
                return false;
            }
            if (emptySince < 0) emptySince = tick;
            detail = "Waiting for late tree drops (" + Math.max(0, 60 - (tick - emptySince)) + " ticks)";
            return tick - emptySince >= 60;
        }
        emptySince = -1;
        if (target == null) {
            target = drops.stream()
                .filter(drop -> failedRounds.getOrDefault(drop.getEntityId(), 0) < 2)
                .min(
                    java.util.Comparator
                        .<EntityItem>comparingInt(drop -> failedRounds.getOrDefault(drop.getEntityId(), 0))
                        .thenComparingDouble(drop -> minecraft.thePlayer.getDistanceSqToEntity(drop)))
                .orElseThrow(
                    () -> new IllegalStateException(
                        "Remaining tree drops could not be collected after two rounds; check inventory space or access, then retry. Planting has not started."));
            deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        }
        if (System.nanoTime() - deadline >= 0) return deferTarget("Pickup deadline exceeded");
        BasePosition currentPosition = new BasePosition(
            min.getDimensionId(),
            (int) Math.floor(target.posX),
            (int) Math.floor(target.posY),
            (int) Math.floor(target.posZ));
        if (!currentPosition.equals(trackedPosition)) {
            if (moving != null) moving.cancel();
            moving = null;
            arrivalTick = -1;
            approachAttempt = 0;
            trackedPosition = currentPosition;
            DevelopmentTrace.event(
                "tree-collection",
                "drop-position-updated",
                "target",
                target.getEntityId(),
                "position",
                currentPosition);
        }
        if (moving != null) {
            NavigationProgress progress = moving.progress();
            if (progress.getState() == NavigationState.FAILED || progress.getState() == NavigationState.CANCELLED) {
                moving = null;
                if (progress.getDetail()
                    .contains("firewall"))
                    throw new IllegalStateException("Cannot reach tree drops: " + progress.getDetail());
                if (approachAttempt >= 5) return deferTarget(progress.getDetail());
                detail = "Trying another pickup position: " + progress.getDetail();
                return false;
            }
            if (progress.getState() == NavigationState.COMPLETED) {
                moving = null;
                arrivalTick = tick;
            }
        } else if (arrivalTick >= 0) {
            if (tick - arrivalTick >= 20) {
                if (approachAttempt >= 5) return deferTarget("Drop remained after alternate pickup positions");
                arrivalTick = -1;
            }
        } else if (guard.isReadyForSession()) {
            int[][] offsets = { { 0, 0 }, { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
            int[] offset = offsets[approachAttempt++];
            DevelopmentTrace.event(
                "tree-collection",
                "pickup-goal",
                "target",
                target.getEntityId(),
                "attempt",
                approachAttempt,
                "x",
                currentPosition.getX() + offset[0],
                "y",
                currentPosition.getY(),
                "z",
                currentPosition.getZ() + offset[1],
                "onGround",
                target.onGround,
                "playerFeetY",
                minecraft.thePlayer.boundingBox.minY);
            moving = navigation.submit(
                new NavigationRequest(
                    task + "-tree-drops-" + (++sequence),
                    lease.getEpoch(),
                    min.getDimensionId(),
                    currentPosition.getX() + offset[0],
                    currentPosition.getY(),
                    currentPosition.getZ() + offset[1],
                    0,
                    System.nanoTime(),
                    NavigationRequest.MAX_RUNTIME_NANOS),
                lease);
        }
        detail = "Collecting tree drops before planting: " + drops.size() + " stack(s) remain";
        return false;
    }

    @Override
    public String detail() {
        return detail;
    }

    private boolean deferTarget(String reason) {
        int id = target.getEntityId();
        int rounds = failedRounds.getOrDefault(id, 0) + 1;
        failedRounds.put(id, rounds);
        if (moving != null) moving.cancel();
        moving = null;
        target = null;
        trackedPosition = null;
        arrivalTick = -1;
        approachAttempt = 0;
        detail = "Deferring unreachable drop " + id + " (round " + rounds + "/2): " + reason + "; checking other drops";
        DevelopmentTrace.event("tree-collection", "deferred-target", "target", id, "round", rounds, "reason", reason);
        return false;
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (scaffoldCleanup != null) scaffoldCleanup.close();
        if (moving != null) moving.cancel();
        moving = null;
    }
}
