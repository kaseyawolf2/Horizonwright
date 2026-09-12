package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.navigation.ScaffoldCleanup;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationBackend;

/** Removes recorded supports, then performs bounded movement-only collection between mining actions. */
final class ExcavationDropCollector implements ExcavationBackend.DropCollection {

    private final Minecraft mc;
    private final ActionSessionGuard guard;
    private final NavigationBackend navigation;
    private final String taskId;
    private final CylinderExcavationSpec area;
    private final ActionLease lease;
    private final Set<Integer> deferred = new HashSet<>();
    private long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    private NavigationHandle moving;
    private ScaffoldCleanup scaffoldCleanup;
    private boolean scaffoldsCleared;
    private EntityItem target;
    private BlockPosition position;
    private int emptyTicks, arrivalTicks, attempts, sequence;
    private boolean closed, finishing;
    private String detail = "Scanning nearby mined items";

    ExcavationDropCollector(Minecraft mc, ActionSessionGuard guard, NavigationBackend navigation, String taskId,
        CylinderExcavationSpec area, ActionLease lease) {
        this.mc = mc;
        this.guard = guard;
        this.navigation = navigation;
        this.taskId = taskId;
        this.area = area;
        this.lease = lease;
    }

    @Override
    public boolean poll() {
        if (closed || !lease.isValid()
            || mc.thePlayer == null
            || mc.theWorld == null
            || mc.theWorld.provider.dimensionId != area.getDimensionId())
            throw new IllegalStateException("Excavation collection interrupted");
        if (!scaffoldsCleared) {
            if (scaffoldCleanup == null)
                scaffoldCleanup = new ScaffoldCleanup(navigation, lease, taskId, area.getDimensionId());
            if (!scaffoldCleanup.poll()) {
                detail = "Removing temporary excavation pillars";
                return false;
            }
            scaffoldsCleared = true;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        }
        if (System.nanoTime() >= deadline) finishing = true;
        if (finishing) {
            cancelNavigation();
            detail = "Item collection pass finished; continuing excavation";
            return guard.isReadyForSession();
        }
        if (target != null && (target.isDead || !mc.theWorld.loadedEntityList.contains(target)
            || !hasRoom(mc.thePlayer.inventory.mainInventory, target.getEntityItem()))) {
            clearTarget();
        }
        if (target == null) {
            List<EntityItem> items = mc.theWorld
                .getEntitiesWithinAABB(EntityItem.class, mc.thePlayer.boundingBox.expand(24, 12, 24));
            double best = Double.POSITIVE_INFINITY;
            for (EntityItem item : items) {
                if (item.isDead || !item.onGround
                    || deferred.contains(item.getEntityId())
                    || !area.contains(
                        new BlockPosition(
                            (int) Math.floor(item.posX),
                            Math.max(area.getBottomY(), Math.min(area.getTopY(), (int) Math.floor(item.posY))),
                            (int) Math.floor(item.posZ)))
                    || !hasRoom(mc.thePlayer.inventory.mainInventory, item.getEntityItem())) continue;
                double distance = mc.thePlayer.getDistanceSqToEntity(item);
                if (distance < best) {
                    best = distance;
                    target = item;
                }
            }
            if (target == null) {
                detail = "Waiting for nearby mined-item drops";
                if (++emptyTicks >= 10) finishing = true;
                return false;
            }
            emptyTicks = 0;
        }
        BlockPosition current = new BlockPosition(
            (int) Math.floor(target.posX),
            (int) Math.floor(target.posY),
            (int) Math.floor(target.posZ));
        if (!current.equals(position)) {
            cancelNavigation();
            position = current;
            attempts = 0;
            arrivalTicks = 0;
        }
        if (moving != null) {
            NavigationProgress progress = moving.progress();
            if (progress.getState() == NavigationState.FAILED || progress.getState() == NavigationState.CANCELLED) {
                cancelNavigation();
                if (progress.getDetail()
                    .contains("firewall"))
                    throw new IllegalStateException("Collection navigation: " + progress.getDetail());
                if (attempts >= 2) deferTarget();
            } else if (progress.getState() == NavigationState.COMPLETED) {
                moving = null;
                arrivalTicks = 1;
            }
        } else if (arrivalTicks > 0) {
            if (++arrivalTicks >= 20) {
                if (attempts >= 2) deferTarget();
                else arrivalTicks = 0;
            }
        } else if (guard.isReadyForSession()) {
            int tolerance = attempts++ == 0 ? 0 : 1;
            moving = navigation.submit(
                new NavigationRequest(
                    taskId + "-mined-drops-" + (++sequence),
                    lease.getEpoch(),
                    area.getDimensionId(),
                    current.getX(),
                    current.getY(),
                    current.getZ(),
                    tolerance,
                    System.nanoTime(),
                    TimeUnit.SECONDS.toNanos(8)),
                lease);
        }
        detail = "Collecting nearby mined items";
        return false;
    }

    static boolean hasRoom(ItemStack[] inventory, ItemStack drop) {
        if (drop == null || drop.stackSize <= 0) return false;
        for (int i = 0; i < Math.min(36, inventory.length); i++) {
            ItemStack stack = inventory[i];
            if (stack == null || stack.isItemEqual(drop) && ItemStack.areItemStackTagsEqual(stack, drop)
                && stack.stackSize < Math.min(64, stack.getMaxStackSize())) return true;
        }
        return false;
    }

    private void deferTarget() {
        deferred.add(target.getEntityId());
        clearTarget();
    }

    private void clearTarget() {
        cancelNavigation();
        target = null;
        position = null;
        attempts = arrivalTicks = 0;
    }

    private void cancelNavigation() {
        if (moving != null) moving.cancel();
        moving = null;
    }

    @Override
    public String detail() {
        return detail;
    }

    @Override
    public void close() {
        closed = true;
        if (scaffoldCleanup != null) scaffoldCleanup.close();
        cancelNavigation();
    }
}
