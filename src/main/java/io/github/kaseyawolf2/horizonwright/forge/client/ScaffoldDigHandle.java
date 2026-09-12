package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.QuicksandTravelPolicy;

/** Exact support removal without asking the pathfinder to stand inside the support block. */
public final class ScaffoldDigHandle implements NavigationHandle {

    private final Minecraft mc;
    private final ActionSessionGuard guard;
    private final ActionLease lease;
    private final String id;
    private final int dimension, priorSlot;
    private final BlockPosition target;
    private final BooleanSupplier identityMatches;
    private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
    private NavigationState state = NavigationState.MOVING;
    private String detail = "Breaking recorded pillar block";
    private boolean started;
    private int lastTick = Integer.MIN_VALUE;

    public static ScaffoldDigHandle startIfReachable(Minecraft mc, ActionSessionGuard guard, ActionLease lease,
        String id, int dimension, BlockPosition target, BooleanSupplier identityMatches) {
        if (!lease.isValid() || !lease.getCapabilities()
            .containsAll(
                EnumSet.of(
                    ActionCapability.MOVEMENT,
                    ActionCapability.LOOK,
                    ActionCapability.DIG,
                    ActionCapability.HELD_USE)))
            throw new IllegalArgumentException("Pillar removal needs movement, look, dig and held-tool authority");
        if (mc.thePlayer == null || mc.theWorld == null
            || mc.theWorld.provider.dimensionId != dimension
            || !VerticalMiningStability.isStable(mc.thePlayer)
            || !identityMatches.getAsBoolean()
            || aim(mc, target) == null
            || !safeToRemove(mc, target)) return null;
        return new ScaffoldDigHandle(mc, guard, lease, id, dimension, target, identityMatches);
    }

    private ScaffoldDigHandle(Minecraft mc, ActionSessionGuard guard, ActionLease lease, String id, int dimension,
        BlockPosition target, BooleanSupplier identityMatches) {
        this.mc = mc;
        this.guard = guard;
        this.lease = lease;
        this.id = id;
        this.dimension = dimension;
        this.target = target;
        this.identityMatches = identityMatches;
        priorSlot = mc.thePlayer.inventory.currentItem;
        guard.begin(lease);
        try {
            ClientBootstrap.blockDamageShield()
                .acquire(id);
            selectTool();
            DevelopmentTrace
                .event("scaffolding", "dig-start", "position", target, "slot", mc.thePlayer.inventory.currentItem);
        } catch (RuntimeException failure) {
            finish(NavigationState.FAILED, failure.getMessage());
            throw failure;
        }
    }

    public String getRequestId() {
        return id;
    }

    public boolean isTerminal() {
        return state == NavigationState.COMPLETED || state == NavigationState.FAILED
            || state == NavigationState.CANCELLED;
    }

    public long epoch() {
        return lease.getEpoch();
    }

    public NavigationProgress progress() {
        if (!isTerminal()) {
            try {
                tick();
            } catch (RuntimeException failure) {
                finish(NavigationState.FAILED, failure.getMessage());
            }
        }
        return new NavigationProgress(id, lease.getEpoch(), state, detail);
    }

    private void tick() {
        if (!lease.isValid() || !guard.isActiveLease(lease)) {
            finish(NavigationState.CANCELLED, "Pillar dig authority revoked");
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null || mc.theWorld.provider.dimensionId != dimension) {
            finish(NavigationState.FAILED, "Pillar world changed");
            return;
        }
        if (mc.theWorld.isAirBlock(target.getX(), target.getY(), target.getZ())) {
            finish(NavigationState.COMPLETED, "Recorded pillar block removed");
            return;
        }
        if (!identityMatches.getAsBoolean()) {
            finish(NavigationState.FAILED, "Recorded pillar block changed");
            return;
        }
        if (System.nanoTime() - deadline >= 0) {
            finish(NavigationState.FAILED, "Pillar digging timed out");
            return;
        }
        if (lastTick == mc.thePlayer.ticksExisted) return;
        lastTick = mc.thePlayer.ticksExisted;
        if (!VerticalMiningStability.isStable(mc.thePlayer)) {
            mc.playerController.resetBlockRemoving();
            started = false;
            detail = "Waiting for vertical movement to stop before pillar digging";
            return;
        }
        MovingObjectPosition hit = aim(mc, target);
        if (hit == null || !safeToRemove(mc, target)) {
            finish(NavigationState.FAILED, "Pillar left safe digging reach");
            return;
        }
        Vec3 eye = MinecraftRuntimeAccess.playerInteractionOrigin(mc.thePlayer);
        double dx = hit.hitVec.xCoord - eye.xCoord, dy = hit.hitVec.yCoord - eye.yCoord,
            dz = hit.hitVec.zCoord - eye.zCoord;
        mc.thePlayer.rotationYaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        mc.thePlayer.rotationPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        if (!started) {
            mc.playerController.clickBlock(target.getX(), target.getY(), target.getZ(), hit.sideHit);
            started = true;
        } else mc.playerController.onPlayerDamageBlock(target.getX(), target.getY(), target.getZ(), hit.sideHit);
        ClientBootstrap.blockDamageShield()
            .checkpoint();
        mc.thePlayer.swingItem();
    }

    private void selectTool() {
        Block block = mc.theWorld.getBlock(target.getX(), target.getY(), target.getZ());
        int best = priorSlot;
        float speed = -1;
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
                if (stack != null && areaTool(stack)) continue;
                mc.thePlayer.inventory.currentItem = i;
                float candidate = block.getPlayerRelativeBlockHardness(
                    mc.thePlayer,
                    mc.theWorld,
                    target.getX(),
                    target.getY(),
                    target.getZ());
                if (candidate > speed) {
                    speed = candidate;
                    best = i;
                }
            }
        } finally {
            mc.thePlayer.inventory.currentItem = priorSlot;
        }
        if (speed <= 0) throw new IllegalStateException("No usable hotbar tool for pillar removal");
        mc.thePlayer.inventory.currentItem = best;
        mc.playerController.updateController();
        io.github.kaseyawolf2.horizonwright.forge.client.network.HeldSlotSynchronization.sendCurrentSlot(mc);
    }

    private static boolean areaTool(ItemStack stack) {
        String name = stack.getItem()
            .getClass()
            .getName();
        return name.startsWith("tconstruct.items.tools.") && (name.endsWith(".LumberAxe") || name.endsWith(".Hammer")
            || name.endsWith(".Excavator")
            || name.endsWith(".Scythe"));
    }

    private static MovingObjectPosition aim(Minecraft mc, BlockPosition target) {
        Vec3 eye = MinecraftRuntimeAccess.playerInteractionOrigin(mc.thePlayer);
        double reach = mc.playerController.getBlockReachDistance();
        MovingObjectPosition hit = MinecraftRuntimeAccess.rayTraceBlocks(
            mc.theWorld,
            eye,
            Vec3.createVectorHelper(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
            false);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == target.getX()
            && hit.blockY == target.getY()
            && hit.blockZ == target.getZ()
            && eye.squareDistanceTo(hit.hitVec) <= reach * reach ? hit : null;
    }

    private static boolean safeToRemove(Minecraft mc, BlockPosition target) {
        net.minecraft.util.AxisAlignedBB box = mc.thePlayer.boundingBox;
        if (box.maxX <= target.getX() || box.minX >= target.getX() + 1
            || box.maxZ <= target.getZ()
            || box.minZ >= target.getZ() + 1
            || box.minY < target.getY() + 1) return true;
        if (!mc.thePlayer.onGround) return false;
        int x = target.getX(), z = target.getZ();
        return ScaffoldDescentSafety.safeLanding(
            target.getY(),
            box.minY,
            y -> mc.theWorld.blockExists(x, y, z) && mc.theWorld.isAirBlock(x, y, z),
            y -> {
                if (!mc.theWorld.blockExists(x, y, z)) return false;
                Block floor = mc.theWorld.getBlock(x, y, z);
                return ScaffoldDescentSafety
                    .supportsLanding(floor.getCollisionBoundingBoxFromPool(mc.theWorld, x, y, z), x, y, z)
                    && !(floor instanceof BlockFalling)
                    && !QuicksandTravelPolicy.isHazard(mc.theWorld, x, y, z);
            });
    }

    public void cancel() {
        if (!isTerminal()) finish(NavigationState.CANCELLED, "Pillar dig cancelled");
    }

    private void finish(NavigationState result, String message) {
        if (isTerminal()) return;
        state = result;
        detail = message;
        try {
            ClientBootstrap.blockDamageShield()
                .release(id);
            if (mc.thePlayer != null) {
                mc.playerController.resetBlockRemoving();
                mc.thePlayer.inventory.currentItem = priorSlot;
                if (lease.isValid() && guard.isActiveLease(lease)) {
                    mc.playerController.updateController();
                    io.github.kaseyawolf2.horizonwright.forge.client.network.HeldSlotSynchronization
                        .sendCurrentSlot(mc);
                }
            }
        } finally {
            io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch
                .endAfterPendingWrites(mc, guard, lease);
        }
        DevelopmentTrace.event("scaffolding", "dig-finished", "position", target, "state", state, "detail", detail);
    }
}
