package io.github.kaseyawolf2.horizonwright.forge.client.container;

import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionState;

/** Baritone travel, reach-checked interaction, then exact storage identity confirmation. */
final class LiveStorageAccess implements UnloadActionHandle {

    private final Minecraft mc;
    private final ActionSessionGuard guard;
    private final NavigationBackend navigation;
    private final LiveVanillaChestUnloadBackend.ConfigurationSource config;
    private final String id, storage;
    private final long epoch;
    private final ActionLease lease;
    private final NamedLocation location;
    private final long started = System.nanoTime();
    private NavigationHandle moving;
    private boolean approached, clicked, ownsSession;
    private volatile boolean cancelled;
    private int clickedTick;
    private UnloadActionState state = UnloadActionState.EXECUTING;
    private String detail = "Preparing saved-storage approach";

    LiveStorageAccess(Minecraft mc, ActionSessionGuard guard, NavigationBackend navigation,
        LiveVanillaChestUnloadBackend.ConfigurationSource config, String id, String storage, long epoch,
        ActionLease lease) {
        if (navigation == null || lease == null
            || !lease.isValid()
            || lease.getEpoch() != epoch
            || !lease.getCapabilities()
                .containsAll(
                    java.util.EnumSet.of(
                        ActionCapability.MOVEMENT,
                        ActionCapability.LOOK,
                        ActionCapability.USE,
                        ActionCapability.PLACE)))
            throw new IllegalStateException("Storage access needs navigation and movement/look/use authority");
        this.mc = mc;
        this.guard = guard;
        this.navigation = navigation;
        this.config = config;
        this.id = id;
        this.storage = storage;
        this.epoch = epoch;
        this.lease = lease;
        location = config.location(storage);
        if (location == null) throw new IllegalStateException("Storage location is not configured");
    }

    public String getRequestId() {
        return id;
    }

    public synchronized UnloadActionProgress progress() {
        if (state != UnloadActionState.EXECUTING) return snapshot();
        try {
            if (cancelled || !lease.isValid() || !mc.func_152345_ab() || mc.thePlayer == null || mc.theWorld == null)
                throw new IllegalStateException("Storage access interrupted");
            if (mc.theWorld.provider.dimensionId != location.getDimensionId())
                throw new IllegalStateException("Storage is in another dimension");
            if (System.nanoTime() - started > TimeUnit.MINUTES.toNanos(2))
                throw new IllegalStateException("Storage access timed out");
            if (config.matches(storage, mc.thePlayer.openContainer)
                || clicked && mc.thePlayer.ticksExisted - clickedTick <= 100
                    && config.bindOpened(storage, mc.thePlayer.openContainer)) {
                stop();
                if (guard.isReadyForSession()) {
                    state = UnloadActionState.CONFIRMED;
                    detail = "Saved storage opened and identity verified";
                }
                return snapshot();
            }
            if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) throw new IllegalStateException(
                "A different container is open; close it before accessing saved storage");
            if (clicked) {
                detail = "Waiting for server to open saved storage";
                return snapshot();
            }
            if (moving != null) {
                NavigationProgress p = moving.progress();
                detail = p.getDetail();
                if (p.getState() == NavigationState.COMPLETED) {
                    moving = null;
                    approached = true;
                } else if (p.getState() == NavigationState.FAILED || p.getState() == NavigationState.CANCELLED)
                    throw new IllegalStateException("Storage route failed: " + p.getDetail());
                return snapshot();
            }
            if (!guard.isReadyForSession()) {
                detail = "Waiting for storage approach cleanup";
                return snapshot();
            }
            MovingObjectPosition hit = hit();
            if (hit == null) {
                if (approached)
                    throw new IllegalStateException("Saved storage is obscured or out of reach after approach");
                moving = navigation.submit(
                    NavigationRequest.adjacentTo(
                        id + "-route",
                        epoch,
                        location.getDimensionId(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        System.nanoTime(),
                        TimeUnit.MINUTES.toNanos(2)),
                    lease);
                detail = "Baritone travelling to saved storage";
                return snapshot();
            }
            SupportedStorageTarget.requireSupported(
                MinecraftRuntimeAccess.tileEntity(mc.theWorld, location.getX(), location.getY(), location.getZ()));
            if (mc.thePlayer.isSneaking()) throw new IllegalStateException("Release sneak before opening storage");
            guard.begin(lease);
            ownsSession = true;
            // C08 represents chest activation too; an ItemBlock makes the classifier require PLACE.
            DevelopmentTrace.event(
                "storage-access",
                "interact",
                "request",
                id,
                "target",
                location,
                "held",
                mc.thePlayer.getHeldItem(),
                "capabilities",
                lease.getCapabilities());
            if (!mc.playerController.onPlayerRightClick(
                mc.thePlayer,
                mc.theWorld,
                mc.thePlayer.getHeldItem(),
                location.getX(),
                location.getY(),
                location.getZ(),
                hit.sideHit,
                hit.hitVec)) throw new IllegalStateException("Storage interaction was rejected");
            clicked = true;
            clickedTick = mc.thePlayer.ticksExisted;
            detail = "Storage interaction sent; waiting for matching container";
            io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch
                .afterPendingWrites(mc, this::stop);
        } catch (RuntimeException failure) {
            stop();
            state = UnloadActionState.FAILED;
            detail = failure.getMessage();
        }
        return snapshot();
    }

    private MovingObjectPosition hit() {
        Vec3 eye = MinecraftRuntimeAccess.playerInteractionOrigin(mc.thePlayer);
        Vec3 target = Vec3.createVectorHelper(location.getX() + 0.5, location.getY() + 0.5, location.getZ() + 0.5);
        double reach = Math.min(3.25, mc.playerController.getBlockReachDistance());
        if (eye.squareDistanceTo(target) > reach * reach) return null;
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, target);
        return hit != null && hit.blockX == location.getX()
            && hit.blockY == location.getY()
            && hit.blockZ == location.getZ() ? hit : null;
    }

    public void cancel() {
        cancelled = true;
        if (mc.func_152345_ab()) stop();
        else mc.func_152344_a(this::stop);
    }

    private synchronized void stop() {
        if (moving != null) {
            moving.cancel();
            moving = null;
        }
        if (ownsSession) {
            guard.quarantine(lease);
            guard.end(lease);
            ownsSession = false;
        }
    }

    private UnloadActionProgress snapshot() {
        DevelopmentTrace
            .event("storage-access", "progress", "request", id, "storage", storage, "state", state, "detail", detail);
        return new UnloadActionProgress(id, state, detail == null ? "Storage access failed" : detail);
    }
}
