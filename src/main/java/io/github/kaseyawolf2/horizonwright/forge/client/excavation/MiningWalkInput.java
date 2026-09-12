package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraftforge.common.util.ForgeDirection;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;

/** Scoped vanilla walking input. Never writes global key bindings or moves the freecam. */
final class MiningWalkInput extends MovementInput implements AutoCloseable {

    private static final double STOP_DISTANCE = 1.0;

    private final Minecraft mc;
    private final EntityClientPlayerMP player;
    private final net.minecraft.world.World world;
    private final MovementInput previous;
    private final ActionLease lease;
    private final ActionSessionGuard guard;
    private final BlockPosition target;
    private final BlockPosition toward;
    private final boolean approaching;
    private final io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec pickupArea;
    private volatile boolean enabled = true;

    MiningWalkInput(Minecraft mc, ActionLease lease, ActionSessionGuard guard, BlockPosition target,
        BlockPosition toward) {
        this(mc, lease, guard, target, toward, false);
    }

    MiningWalkInput(Minecraft mc, ActionLease lease, ActionSessionGuard guard, BlockPosition target,
        BlockPosition toward, boolean approaching) {
        this(mc, lease, guard, target, toward, approaching, null);
    }

    MiningWalkInput(Minecraft mc, ActionLease lease, ActionSessionGuard guard, BlockPosition target,
        BlockPosition toward, boolean approaching,
        io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec pickupArea) {
        this.pickupArea = pickupArea;
        this.approaching = approaching;
        this.mc = mc;
        this.player = mc.thePlayer;
        this.world = mc.theWorld;
        this.previous = player.movementInput;
        this.lease = lease;
        this.guard = guard;
        this.target = target;
        this.toward = toward;
        player.movementInput = this;
    }

    @Override
    public void updatePlayerMoveState() {
        moveForward = moveStrafe = 0;
        jump = sneak = false;
        if (!enabled || mc.theWorld != world
            || !lease.isValid()
            || !guard.isActiveLease(lease)
            || mc.thePlayer != player
            || mc.currentScreen != null
            || !player.onGround
            || player.isInWater()
            || player.isOnLadder()
            || player.isRiding()) return;
        if (MinecraftRuntimeAccess.isAirBlock(mc.theWorld, target.getX(), target.getY(), target.getZ())) {
            brake();
            return;
        }
        MiningPickupSteering.Destination pickup = nearbyPickup();
        double destinationX = pickup == null ? toward.getX() + 0.5 : pickup.x;
        double destinationZ = pickup == null ? toward.getZ() + 0.5 : pickup.z;
        if (!canWalkToward(destinationX, destinationZ, pickup != null)) {
            brake();
            return;
        }
        float[] input = MiningPickupSteering
            .relativeInput(player.rotationYaw, destinationX - player.posX, destinationZ - player.posZ);
        moveForward = input[0];
        moveStrafe = input[1];
        // Rotation remains owned by LiveHandle.aimAtTarget while walking may strafe or move backwards.
        player.setSprinting(false);
    }

    private MiningPickupSteering.Destination nearbyPickup() {
        if (approaching || pickupArea == null || world.provider.dimensionId != pickupArea.getDimensionId()) return null;
        double reach = mc.playerController.getBlockReachDistance() - 0.15;
        java.util.List<MiningPickupSteering.Destination> candidates = new java.util.ArrayList<>();
        java.util.List<net.minecraft.entity.item.EntityItem> drops = world.getEntitiesWithinAABB(
            net.minecraft.entity.item.EntityItem.class,
            player.boundingBox.expand(reach, 1.25, reach));
        for (net.minecraft.entity.item.EntityItem drop : drops) {
            if (drop.isDead || !drop.onGround
                || !ExcavationDropCollector.hasRoom(player.inventory.mainInventory, drop.getEntityItem())
                || !pickupArea.contains(
                    new BlockPosition(
                        MathHelper.floor_double(drop.posX),
                        Math.max(
                            pickupArea.getBottomY(),
                            Math.min(pickupArea.getTopY(), MathHelper.floor_double(drop.posY))),
                        MathHelper.floor_double(drop.posZ))))
                continue;
            candidates.add(new MiningPickupSteering.Destination(drop.posX, drop.posY, drop.posZ));
        }
        return MiningPickupSteering.choose(
            player.posX,
            MinecraftRuntimeAccess.playerInteractionOrigin(player).yCoord,
            player.boundingBox.minY,
            player.posZ,
            target,
            reach,
            candidates,
            drop -> canWalkToward(drop.x, drop.z, true));
    }

    private boolean canWalkToward(double destinationX, double destinationZ, boolean pickup) {
        double dx = destinationX - player.posX, dz = destinationZ - player.posZ;
        double distance = Math.hypot(dx, dz);
        if (distance <= (pickup ? MiningPickupSteering.PICKUP_STOP_DISTANCE : STOP_DISTANCE)
            || tooClose(player.posX, player.posZ, 0, 0, target)) return false;
        double sx = dx / distance * Math.min(0.35, distance) + player.motionX;
        double sz = dz / distance * Math.min(0.35, distance) + player.motionZ;
        if (tooClose(player.posX, player.posZ, sx, sz, target)
            || !pickup && tooClose(player.posX, player.posZ, sx, sz, toward)) return false;
        double eyeY = MinecraftRuntimeAccess.playerInteractionOrigin(player).yCoord;
        double reach = approaching ? approachDistance(mc.playerController.getBlockReachDistance())
            : mc.playerController.getBlockReachDistance() - 0.15;
        if (!withinReach(player.posX + sx, eyeY, player.posZ + sz, target, reach)) return false;
        net.minecraft.util.MovingObjectPosition hit = MinecraftRuntimeAccess.rayTraceBlocks(
            world,
            net.minecraft.util.Vec3.createVectorHelper(player.posX + sx, eyeY, player.posZ + sz),
            net.minecraft.util.Vec3.createVectorHelper(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
            false);
        if (hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
            || hit.blockX != target.getX()
            || hit.blockY != target.getY()
            || hit.blockZ != target.getZ()) return false;
        AxisAlignedBB swept = player.boundingBox.addCoord(sx, 0, sz);
        if (!world.getCollidingBoundingBoxes(player, swept)
            .isEmpty() || world.isAnyLiquid(swept)) return false;
        int floorY = MathHelper.floor_double(player.boundingBox.minY - 0.05);
        // Evaluate the full swept footprint as if the digging target had already disappeared.
        for (int x = MathHelper.floor_double(swept.minX); x <= MathHelper.floor_double(swept.maxX); x++)
            for (int z = MathHelper.floor_double(swept.minZ); z <= MathHelper.floor_double(swept.maxZ); z++) {
                boolean removed = x == target.getX() && floorY == target.getY() && z == target.getZ();
                if (!world.blockExists(x, floorY, z)) return false;
                for (int y = floorY - 1; y <= floorY + 2; y++) {
                    if (io.github.kaseyawolf2.horizonwright.navigation.baritone.QuicksandTravelPolicy
                        .isHazard(world, x, y, z)) return false;
                }
                if (!removed && world.isSideSolid(x, floorY, z, ForgeDirection.UP)) continue;
                if (!removed && !MinecraftRuntimeAccess.isAirBlock(world, x, floorY, z)) return false;
                if (!world.blockExists(x, floorY - 1, z) || !world.isSideSolid(x, floorY - 1, z, ForgeDirection.UP)
                    || world.isAnyLiquid(AxisAlignedBB.getBoundingBox(x, floorY - 1, z, x + 1, floorY + 1, z + 1)))
                    return false;
            }
        return true;
    }

    static double approachDistance(double blockReach) {
        return blockReach + 0.75;
    }

    static boolean withinReach(double x, double y, double z, BlockPosition target, double reach) {
        double dx = x - (target.getX() + 0.5), dy = y - (target.getY() + 0.5), dz = z - (target.getZ() + 0.5);
        return reach > 0 && dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private void brake() {
        player.motionX = 0;
        player.motionZ = 0;
        player.setSprinting(false);
    }

    static boolean tooClose(double x, double z, double stepX, double stepZ, BlockPosition block) {
        double dx = block.getX() + 0.5 - x, dz = block.getZ() + 0.5 - z;
        double lengthSquared = stepX * stepX + stepZ * stepZ;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, (dx * stepX + dz * stepZ) / lengthSquared));
        dx -= stepX * t;
        dz -= stepZ * t;
        return dx * dx + dz * dz <= STOP_DISTANCE * STOP_DISTANCE;
    }

    static float stableYaw(float current, double dx, double dz) {
        if (dx * dx + dz * dz < 0.0025) return current;
        float desired = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        return current + MathHelper.wrapAngleTo180_float(desired - current);
    }

    @Override
    public void close() {
        disable();
        if (player.movementInput == this) player.movementInput = previous;
    }

    void disable() {
        enabled = false;
        moveForward = moveStrafe = 0;
        jump = sneak = false;
    }
}
