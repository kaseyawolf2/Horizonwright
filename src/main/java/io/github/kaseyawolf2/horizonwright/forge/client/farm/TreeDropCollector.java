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
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.runtime.task.TreeBackend;

/** Non-destructive, live-rescanned pickup pass before any saplings are planted. */
final class TreeDropCollector implements TreeBackend.CollectionHandle {

    private final Minecraft minecraft;
    private final ActionSessionGuard guard;
    private final NavigationBackend navigation;
    private final String task;
    private final NamedArea area;
    private final ActionLease lease;
    private NavigationHandle moving;
    private EntityItem target;
    private long deadline;
    private int emptySince = -1;
    private int arrivalTick = -1;
    private int sequence;
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
        BasePosition min = area.getMinimum(), max = area.getMaximum();
        if (minecraft.theWorld.provider.dimensionId != min.getDimensionId())
            throw new IllegalStateException("Tree collection dimension changed");
        for (int cx = min.getX() >> 4; cx <= (max.getX() >> 4); cx++)
            for (int cz = min.getZ() >> 4; cz <= (max.getZ() >> 4); cz++)
                if (!MinecraftRuntimeAccess.blockExists(minecraft.theWorld, cx << 4, min.getY(), cz << 4))
                    throw new IllegalStateException("Tree farm must remain completely loaded during collection");
        List<EntityItem> drops = MinecraftRuntimeAccess.getEntitiesWithinAabb(
            minecraft.theWorld,
            EntityItem.class,
            AxisAlignedBB
                .getBoundingBox(min.getX(), min.getY(), min.getZ(), max.getX() + 1D, max.getY() + 1D, max.getZ() + 1D));
        drops.removeIf(
            drop -> drop.isDead || !area.contains(
                new BasePosition(
                    min.getDimensionId(),
                    (int) Math.floor(drop.posX),
                    (int) Math.floor(drop.posY),
                    (int) Math.floor(drop.posZ))));
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
                .min(java.util.Comparator.comparingDouble(drop -> minecraft.thePlayer.getDistanceSqToEntity(drop)))
                .get();
            deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        }
        if (System.nanoTime() - deadline >= 0)
            throw new IllegalStateException("Timed out collecting tree drop " + target.getEntityId());
        if (moving != null) {
            NavigationProgress progress = moving.progress();
            if (progress.getState() == NavigationState.FAILED || progress.getState() == NavigationState.CANCELLED)
                throw new IllegalStateException("Cannot reach tree drops: " + progress.getDetail());
            if (progress.getState() == NavigationState.COMPLETED) {
                moving = null;
                arrivalTick = tick;
            }
        } else if (arrivalTick >= 0) {
            if (tick - arrivalTick >= 60) throw new IllegalStateException(
                "Tree drop remains after pickup approach; check inventory space or inaccessible drops. Resume to retry before planting.");
        } else if (guard.isReadyForSession()) {
            moving = navigation.submit(
                new NavigationRequest(
                    task + "-tree-drops-" + (++sequence),
                    lease.getEpoch(),
                    min.getDimensionId(),
                    (int) Math.floor(target.posX),
                    (int) Math.floor(target.posY),
                    (int) Math.floor(target.posZ),
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

    @Override
    public void cancel() {
        cancelled = true;
        if (moving != null) moving.cancel();
        moving = null;
    }
}
